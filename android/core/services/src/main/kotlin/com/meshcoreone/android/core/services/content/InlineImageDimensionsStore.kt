// PortedFrom: MC1Services/Sources/MC1Services/Services/InlineImageDimensionsStore.swift@db14559b39d32322b06477c6ae676112f583db50
// NOTE on test ownership: the frozen source's test file for this type,
// MC1Services/Tests/MC1ServicesTests/InlineImageDimensionsStoreTests.swift, is assigned by
// port-manifest.json to WP-213, not WP-218. This port does not claim/port those 9 Swift
// assertions as WP-218's 154-declaration credit; InlineImageDimensionsStoreTest.kt is an
// independently-authored suite covering this Kotlin port's own contract (see
// docs/android/deviations/WP-218.md for the disclosure).
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
// `Codable`'s default (`timeIntervalSinceReferenceDate`, i.e. seconds since 2001-01-01, NOT Unix
// epoch). This file is explicitly documented in the source as process-owned, disposable,
// non-versioned, and NOT part of any backup envelope or cross-platform exchange (unlike the
// envelope-versioned backup codecs elsewhere in the port) -- so byte-for-byte interop with a
// Swift-written file is never required. This port instead encodes `fetchedAt` as Unix epoch
// seconds (`java.time.Instant`), which is internally consistent (what this store writes, only
// this store ever reads) and avoids importing an NSDate-reference-date convention with no other
// use in this codebase. This deviation is disclosed in docs/android/deviations/WP-218.md.
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
import kotlinx.coroutines.flow.BufferOverflow
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

    private val aspectMirror = ConcurrentHashMap<String, Double>().apply {
        entries.forEach { (url, entry) -> put(url, entry.aspect) }
    }

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
                        put("fetchedAt", JsonPrimitive(entry.fetchedAtEpochSecond))
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
                        fetchedAtEpochSecond = entryObject.getValue("fetchedAt").jsonPrimitive.long,
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
