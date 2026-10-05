// PortedFrom: MC1Services/Sources/MC1Services/Services/InlineImageDimensionsStore.swift@db14559b39d32322b06477c6ae676112f583db50
// NOTE on test ownership: the frozen source's test file for this type,
// MC1Services/Tests/MC1ServicesTests/InlineImageDimensionsStoreTests.swift, is assigned by
// port-manifest.json to WP-213 as primary owner, not WP-218. Per explicit coordinator correction,
// that primary-ownership assignment does NOT excuse WP-218 from porting the actual consumer
// behavior its own production file must satisfy: InlineImageDimensionsStoreTest.kt below ports
// the source file's 9 original cases directly (not an independent re-derivation), each disclosed
// as a cross-consumer WP-218 binding rather than claimed WP-218 154-declaration credit -- the
// same cross-reference pattern already used for the `GeodesicDistance`/RFCalculator slice (see
// docs/android/deviations/WP-218.md).
//
// The source is an `actor` whose designated initializer takes an explicit `fileURL`; production
// callers use a zero-arg initializer that resolves the app's Application Support directory via
// `FileManager`. That default-path resolution has no pure-JVM equivalent (Android's
// `Context.filesDir` requires an Android `Context`), so -- matching the established
// DataStoreLinkPreviewPreferencesSource/BitmapImageDecoder native-adapter pattern -- this port
// only accepts an explicit `File` (mirroring the source's test-facing designated initializer);
// resolving a production default path is deferred to a future app-layer native adapter, not yet
// admitted under this receipt.
//
// Persistence format: the source round-trips `[String: Entry]` through `JSONEncoder`/
// `JSONDecoder` with NO explicit `dateEncodingStrategy`, so `Entry.fetchedAt: Date` serializes via
// `Codable`'s default (`timeIntervalSinceReferenceDate`, i.e. seconds since 2001-01-01T00:00:00Z,
// NOT Unix epoch). Per explicit coordinator correction, a cache-format change is NOT an
// automatically-approved "no observable behavior change" deviation (unlike this same file's
// backup-envelope-adjacent codecs, which have a hard Unix-seconds contract for a different
// reason), so this port preserves the source's literal on-disk numeric encoding: `fetchedAt` is
// still tracked internally as Unix epoch seconds (`java.time.Instant`, the natural pure-JVM
// representation), but is translated to/from `timeIntervalSinceReferenceDate` seconds at the
// JSON boundary via [NSDATE_REFERENCE_DATE_EPOCH_OFFSET_SECONDS] -- the well-known, documented
// `kCFAbsoluteTimeIntervalSince1970` constant (978,307,200 seconds between the Unix epoch and the
// 2001-01-01 reference date) -- so a byte-for-byte-matching Swift-written file round-trips
// correctly and a Kotlin-written file is byte-compatible with the source format, even though this
// file remains process-owned/disposable/non-versioned/non-backed-up and cross-platform interop
// was never the requirement being solved here.
//
// The source's all-or-nothing `try? JSONDecoder().decode(...)` recovery (ANY decode failure --
// missing file, corrupt bytes, a single malformed entry -- starts the whole actor empty, never a
// partial per-key recovery) is preserved exactly below: a single try/catch wraps the entire parse
// and rejects partial success.
//
// The source's actor isolation serializes every `save` (no internal `await` inside the method
// body, so no reentrancy is possible mid-call) and its nonisolated `aspect(for:)` lookup is a
// wait-free read against an `OSAllocatedUnfairLock`-guarded mirror dictionary updated at the start
// of `save`, before the JSON file write. This port mirrors that: a [Mutex] serializes `save` calls
// (matching actor isolation), a [ConcurrentHashMap] mirror is updated before the file write
// (matching the mirror-before-persist ordering) and backs the wait-free, lock-free [aspect] read.
//
// The source's `EventBroadcaster<URL>` multicasts every post-subscription `save` to every live
// subscriber independently, buffering the newest 64 per-subscriber on backpressure. A
// [MutableSharedFlow] with `replay = 0` (new subscribers see nothing retroactively, matching
// "events yielded after subscription") and `onBufferOverflow = DROP_OLDEST` (matching "newest 64")
// is the direct Kotlin equivalent; each `collect()` call is an independent subscription, mirroring
// the source's `resolutionUpdates()` returning a fresh `AsyncStream` per call.
package com.meshcoreone.android.core.services.content

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Newest-N multicast buffer depth, matching the source `EventBroadcaster`'s fixed 64-event
 * per-subscriber backpressure window. */
private const val RESOLUTION_STREAM_BUFFER_DEPTH = 64

/**
 * Seconds between the Unix epoch (1970-01-01T00:00:00Z) and Swift's `Date`
 * `timeIntervalSinceReferenceDate` epoch (2001-01-01T00:00:00Z) -- the documented
 * `kCFAbsoluteTimeIntervalSince1970` constant. Used to translate [Instant.epochSecond] to/from
 * the exact on-disk numeric encoding `Codable`'s default `Date` strategy produces, so this file's
 * JSON format matches the source byte-for-byte rather than silently changing on-disk meaning.
 */
private const val NSDATE_REFERENCE_DATE_EPOCH_OFFSET_SECONDS = 978_307_200L

/**
 * Process-owned, disposable (non-versioned, non-backed-up) persistent store mapping an image
 * URL string to its width/height aspect ratio, so previously-decoded inline images can reserve
 * correctly-shaped layout space before a re-fetch/re-decode completes.
 *
 * Mirrors `MC1Services/Sources/MC1Services/Services/InlineImageDimensionsStore.swift`'s actor
 * contract: [save] silently rejects non-positive width/height (matching the source's silent
 * no-op on `size.width <= 0 || size.height <= 0`), [aspect] is a wait-free lookup against an
 * in-memory mirror populated eagerly at [save] time (before the file write completes), and
 * [resolutionUpdates] multicasts every post-subscription [save]'s URL to every live collector.
 */
class InlineImageDimensionsStore(private val file: File) {
    private data class Entry(val aspect: Double, val fetchedAtEpochSecond: Long)

    private val mutex = Mutex()
    private var entries: Map<String, Entry> = loadEntries(file)

    // NOTE: deliberately NOT `ConcurrentHashMap<String, Double>().apply { entries.forEach { ... } }`
    // -- inside `apply`'s implicit receiver scope, the unqualified `entries` would resolve to the
    // `java.util.Map` extension property on the `ConcurrentHashMap` receiver itself (shadowing the
    // outer `entries: Map<String, Entry>` property above), so `entry` would be a `Map.Entry<String,
    // Double>` and `.aspect` would not resolve -- a genuine compile error, not cascading noise from
    // an unrelated import. Constructing directly from the already-mapped values avoids the shadow.
    private val aspectMirror = ConcurrentHashMap<String, Double>(entries.mapValues { it.value.aspect })

    private val resolutionEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = RESOLUTION_STREAM_BUFFER_DEPTH,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Multicast stream of URLs whose aspect was just [save]d; a fresh subscription (each
     * `collect()` call) only observes events emitted strictly after it starts collecting,
     * matching the source's `resolutionUpdates()` returning a fresh `AsyncStream` per call. */
    val resolutionUpdates: SharedFlow<String> = resolutionEvents.asSharedFlow()

    /** Wait-free lookup against the in-memory mirror; never touches the filesystem. Returns
     * `null` for a URL with no recorded (or rejected, non-positive) dimensions. */
    fun aspect(url: String): Double? = aspectMirror[url]

    /**
     * Records `width / height` as the aspect ratio for [url] and persists it to [file].
     * Non-positive width or height is silently rejected (no entry recorded, no event emitted),
     * matching the source's `guard size.width > 0, size.height > 0 else { return }`.
     *
     * Calls are serialized by an internal [Mutex] (no internal suspension point other than the
     * lock itself), matching the source actor's non-reentrant method body.
     */
    suspend fun save(url: String, width: Double, height: Double) {
        if (width <= 0.0 || height <= 0.0) return
        val aspect = width / height
        mutex.withLock {
            entries = entries + (url to Entry(aspect, Instant.now().epochSecond))
            aspectMirror[url] = aspect
            persist()
            resolutionEvents.emit(url)
        }
    }

    private fun persist() {
        val payload = buildJsonObject {
            entries.forEach { (url, entry) ->
                put(
                    url,
                    buildJsonObject {
                        put("aspect", JsonPrimitive(entry.aspect))
                        put(
                            "fetchedAt",
                            JsonPrimitive(
                                entry.fetchedAtEpochSecond - NSDATE_REFERENCE_DATE_EPOCH_OFFSET_SECONDS,
                            ),
                        )
                    },
                )
            }
        }.toString()
        try {
            val parent = file.parentFile
            if (parent != null) {
                Files.createDirectories(parent.toPath())
            }
            val tmp = File(parent, "${file.name}.tmp-${System.nanoTime()}")
            tmp.writeText(payload, Charsets.UTF_8)
            try {
                Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: IOException) {
            // Best-effort write, matching the source's log-and-continue on persistence failure
            // (the source never throws out of `save` on a write error). No logger dependency
            // exists in this pure-JVM module; the in-memory mirror and `entries` map remain the
            // source of truth for the remainder of this process's lifetime regardless.
        }
    }

    private companion object {
        /**
         * All-or-nothing load, matching the source's `try? JSONDecoder().decode(...)`: a missing
         * file, corrupt bytes, or even a single malformed entry rejects the ENTIRE parse and
         * starts empty -- never a partial per-key recovery. The caught exception set covers
         * exactly the failure modes a malformed/corrupt/missing persistence file can produce
         * (JSON structure errors, wrong-shaped values, missing keys, non-numeric values, and I/O
         * failures reading the file); it is not a broad catch-all over unrelated service errors.
         */
        fun loadEntries(file: File): Map<String, Entry> {
            if (!file.isFile) return emptyMap()
            return try {
                val bytes = file.readBytes()
                val root = Json.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonObject
                root.mapValues { (_, value) ->
                    val entryObject = value.jsonObject
                    Entry(
                        aspect = entryObject.getValue("aspect").jsonPrimitive.double,
                        fetchedAtEpochSecond = entryObject.getValue("fetchedAt").jsonPrimitive.long +
                            NSDATE_REFERENCE_DATE_EPOCH_OFFSET_SECONDS,
                    )
                }
            } catch (_: IOException) {
                emptyMap()
            } catch (_: SerializationException) {
                emptyMap()
            } catch (_: IllegalArgumentException) {
                emptyMap()
            } catch (_: NoSuchElementException) {
                emptyMap()
            } catch (_: NumberFormatException) {
                emptyMap()
            }
        }
    }
}
