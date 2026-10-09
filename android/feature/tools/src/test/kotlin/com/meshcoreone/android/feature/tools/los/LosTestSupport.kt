// AndroidOnly: WP-315 JVM test support: source-case binding, single-thread virtual-time runner and LOS port fakes.
package com.meshcoreone.android.feature.tools.los

import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.Coordinate
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.ContactType
import java.io.File
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * Binds a JUnit method to its frozen Swift case id (`Suite::name()`), as core:data and
 * core:connectivity do; the JUnit XML names the method, this annotation names the source case.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
internal annotation class OriginalCase(val value: String, val disposition: String = "source-behavior")

internal const val SOURCE_PIN = "db14559b39d32322b06477c6ae676112f583db50"

internal val SAN_FRANCISCO = Coordinate(37.7749, -122.4194)
internal val OAKLAND = Coordinate(37.8044, -122.2712)
internal val BERKELEY = Coordinate(37.8716, -122.2727)
internal val TEST_REPEATER_COORDINATE = Coordinate(37.8, -122.35)

/** Bit-exact Double from an oracle hex bit pattern. */
internal fun bits(hex: String): Double = java.lang.Double.longBitsToDouble(java.lang.Long.parseUnsignedLong(hex, 16))

internal fun assertBits(expectedHex: String, actual: Double, label: String = "") {
    val actualHex = String.format("%016x", actual.toRawBits())
    kotlin.test.assertEquals(expectedHex, actualHex, "$label expected ${bits(expectedHex)} got $actual")
}

/** Virtual monotonic clock: sleepers resume only when the test advances time. */
internal class ManualClock {
    private class Sleeper(val deadline: Long, val continuation: CancellableContinuation<Unit>)
    private var now = 0L
    private val sleepers = mutableListOf<Sleeper>()

    val pendingSleepers: Int get() = sleepers.size

    suspend fun sleep(millis: Long) {
        if (millis <= 0) {
            yield()
            return
        }
        suspendCancellableCoroutine { continuation ->
            val sleeper = Sleeper(now + millis, continuation)
            sleepers += sleeper
            continuation.invokeOnCancellation { sleepers -= sleeper }
        }
    }

    suspend fun advanceBy(millis: Long) {
        val target = now + millis
        while (true) {
            val due = sleepers.filter { it.deadline <= target }.minByOrNull { it.deadline } ?: break
            sleepers -= due
            now = maxOf(now, due.deadline)
            due.continuation.resumeWith(Result.success(Unit))
            settle()
        }
        now = maxOf(now, target)
        settle()
    }

    suspend fun advanceToNext(): Boolean {
        val next = sleepers.minOfOrNull { it.deadline } ?: return false
        advanceBy((next - now).coerceAtLeast(0))
        return true
    }
}

/** Lets every coroutine queued on the single-thread event loop run to its next suspension. */
internal suspend fun settle(rounds: Int = 200) = repeat(rounds) { yield() }

internal class LosScenario(val scope: CoroutineScope, val clock: ManualClock) {
    val elevation = FakeElevationSource(clock)
    val analyzer = FakePathAnalyzer()

    fun holder(preselectedContact: ContactDTO? = null): LineOfSightStateHolder =
        LineOfSightStateHolder(scope, elevation, analyzer, EmptyCoroutineContext, preselectedContact = preselectedContact)

    /** Swift `waitUntil`: settles, advancing virtual time only while [condition] is unmet. */
    suspend fun waitUntil(message: String, maxVirtualMillis: Long = 60_000, condition: () -> Boolean) {
        settle()
        var waited = 0L
        while (!condition()) {
            if (waited >= maxVirtualMillis || !clock.advanceToNext()) break
            waited += 1
        }
        settle()
        assertTrue(condition(), message)
    }
}

/** Runs a test body on one thread; leftover work is cancelled and drained so nothing leaks. */
internal fun losScenario(body: suspend LosScenario.() -> Unit) = runBlocking {
    val job = SupervisorJob()
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job)
    try {
        withTimeout(30_000) { LosScenario(scope, ManualClock()).body() }
    } finally {
        job.cancel()
        settle()
    }
}

internal class FakeElevationError(message: String) : Exception(message)

/** Test double for the WP-218 elevation port (Swift `MockElevationService`). */
internal class FakeElevationSource(private val clock: ManualClock) : LineOfSightElevationSource {
    var elevationToReturn = 100.0
    var shouldFail = false
    var fetchCount = 0
    var profileToReturn: List<ElevationSample>? = null
    var fetchDelayMillis = 0L
    val requestedPaths = mutableListOf<List<Coordinate>>()

    override suspend fun fetchElevation(coordinate: Coordinate): Double {
        fetchCount += 1
        if (fetchDelayMillis > 0) clock.sleep(fetchDelayMillis)
        if (shouldFail) throw FakeElevationError("No elevation data")
        return elevationToReturn
    }

    override suspend fun fetchElevations(path: List<Coordinate>): List<ElevationSample> {
        fetchCount += 1
        requestedPaths += path
        if (fetchDelayMillis > 0) clock.sleep(fetchDelayMillis)
        if (shouldFail) throw FakeElevationError("No elevation data")
        profileToReturn?.let { return it }
        val start = path.firstOrNull() ?: Coordinate(0.0, 0.0)
        return path.mapIndexed { index, coordinate ->
            ElevationSample(coordinate, elevationToReturn, if (index == 0) 0.0 else haversineMeters(start, coordinate))
        }
    }

    /** Thresholds of the source `ElevationService.optimalSampleCount`. */
    override fun optimalSampleCount(distanceMeters: Double): Int = when {
        distanceMeters < 1000.0 -> 20
        distanceMeters < 5000.0 -> 50
        distanceMeters < 20000.0 -> 80
        else -> 100
    }

    override fun sampleCoordinates(from: Coordinate, to: Coordinate, sampleCount: Int): List<Coordinate> {
        val count = sampleCount.coerceIn(2, 100)
        return (0 until count).map { i ->
            val fraction = i.toDouble() / (count - 1).toDouble()
            Coordinate(from.latitude + fraction * (to.latitude - from.latitude), from.longitude + fraction * (to.longitude - from.longitude))
        }
    }
}

/** Haversine distance used by the fakes (the source mock calls RFCalculator.distance). */
internal fun haversineMeters(from: Coordinate, to: Coordinate): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val deltaLat = Math.toRadians(to.latitude - from.latitude)
    val deltaLon = Math.toRadians(to.longitude - from.longitude)
    val a = sin(deltaLat / 2) * sin(deltaLat / 2) + cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
    return 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/**
 * Test double for the WP-212 RF port. Distances follow the source origin rules (0 for a full
 * path, the first sample for a segment); clearance values are fixed because RF numerics are
 * WP-212's tests' concern. Every call is recorded so slicing/heights/RF inputs are asserted.
 */
internal class FakePathAnalyzer : LineOfSightPathAnalyzer {
    data class Call(
        val kind: String,
        val size: Int,
        val firstDistance: Double,
        val lastDistance: Double,
        val startHeight: Double,
        val endHeight: Double,
        val frequencyMHz: Double,
        val refractionK: Double,
    )

    val calls = mutableListOf<Call>()
    var status = ClearanceStatus.CLEAR

    override fun distanceMeters(from: Coordinate, to: Coordinate): Double = haversineMeters(from, to)

    override fun analyzePath(
        elevationProfile: List<ElevationSample>,
        pointAHeightMeters: Double,
        pointBHeightMeters: Double,
        frequencyMHz: Double,
        refractionK: Double,
    ): PathAnalysisResult = record("path", elevationProfile, pointAHeightMeters, pointBHeightMeters, frequencyMHz, refractionK, 0.0)

    override fun analyzePathSegment(
        elevationProfile: List<ElevationSample>,
        startHeightMeters: Double,
        endHeightMeters: Double,
        frequencyMHz: Double,
        refractionK: Double,
    ): PathAnalysisResult = record(
        "segment", elevationProfile, startHeightMeters, endHeightMeters, frequencyMHz, refractionK,
        elevationProfile.firstOrNull()?.distanceFromAMeters ?: 0.0,
    )

    @Suppress("LongParameterList")
    private fun record(
        kind: String, profile: List<ElevationSample>, start: Double, end: Double, frequency: Double, k: Double, origin: Double,
    ): PathAnalysisResult {
        calls += Call(kind, profile.size, profile.firstOrNull()?.distanceFromAMeters ?: Double.NaN,
            profile.lastOrNull()?.distanceFromAMeters ?: Double.NaN, start, end, frequency, k)
        val length = if (profile.size >= 2) profile.last().distanceFromAMeters - origin else 0.0
        return PathAnalysisResult(length, 0.0, 0.0, 0.0, status, 100.0, emptyList(), frequency, k)
    }
}

/** Test double for the contact port (Swift `MockPersistenceStore.fetchContacts`). */
internal class FakeContactSource : LineOfSightContactSource {
    val contacts = linkedMapOf<UUID, ContactDTO>()
    var failure: Exception? = null

    fun add(contact: ContactDTO) {
        contacts[contact.id] = contact
    }

    override suspend fun fetchContacts(radioId: RadioId): List<ContactDTO> {
        failure?.let { throw it }
        return contacts.values.filter { it.radioId == radioId }
    }
}

internal fun createTestContact(
    name: String = "Test Contact",
    latitude: Double = 37.7749,
    longitude: Double = -122.4194,
    type: ContactType = ContactType.CHAT,
    radioId: RadioId = RadioId(UUID.randomUUID()),
): ContactDTO = ContactDTO(
    id = UUID.randomUUID(),
    radioId = radioId,
    publicKey = Bytes(byteArrayOf(0xAB.toByte()) + ByteArray(31)),
    name = name,
    typeRawValue = type.rawValue,
    flags = 0u,
    outPathLength = 0u,
    latitude = latitude,
    longitude = longitude,
    lastHeardTimestamp = null,
)

/** 101 samples spanning 10 km of flat 100 m terrain (the source relay/repeater fixtures). */
internal fun flatProfile101(): List<ElevationSample> = (0..100).map { i ->
    ElevationSample(Coordinate(37.7749 + i * 0.001, -122.4194), 100.0, i * 100.0)
}

/** English value of a converted WP-005 string resource, read from the committed values XML. */
internal fun englishString(resourceName: String): String {
    val xml = File("../../core/l10n/src/main/res/values/l10n_strings.xml").readText()
    val match = Regex("<string name=\"${Regex.escape(resourceName)}\"[^>]*>\"?(.*?)\"?</string>").find(xml)
    return requireNotNull(match) { "Missing string resource $resourceName" }.groupValues[1]
}
