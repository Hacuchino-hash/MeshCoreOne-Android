// AndroidOnly: WP-217 shared test helpers: settable clock, bounded blocking runner and JUnit display names.
package com.meshcoreone.android.core.services.simulator

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.DynamicTest

/** A clock that only moves when the test moves it. */
internal class SettableClock(start: Instant) : Clock() {
    private val lock = Any()
    private var current = start

    override fun instant(): Instant = synchronized(lock) { current }
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this

    fun advance(seconds: Long) = synchronized(lock) { current = current.plusSeconds(seconds) }
}

internal object SimulatorTestSupport {
    /** The Swift oracle's pinned instant, 2026-01-01T00:00:00Z. */
    val oracleNow: Instant = Instant.ofEpochSecond(SimulatorSeedSwiftOracleVectors.INTEGRAL_NOW_EPOCH_SECONDS)

    private const val TEST_TIMEOUT_MILLIS = 10_000L

    fun <T> blocking(block: suspend CoroutineScope.() -> T): T = runBlocking { withTimeout(TEST_TIMEOUT_MILLIS) { block() } }

    /** A connection mode on a fixed clock with no connect delay. */
    fun mode(clock: Clock = SettableClock(oracleNow), connectDelay: Duration = Duration.ZERO) =
        SimulatorConnectionMode(clock, connectDelay)

    /** A store seeded once at [now]. */
    fun seededStore(now: Instant = oracleNow): SimulatorInMemorySeedStore {
        val store = SimulatorInMemorySeedStore()
        blocking { mode(SettableClock(now)).seedDataStore(store) }
        return store
    }

    /** Process-wide defaults for [DemoModeManager.shared], installed idempotently by every test that needs it. */
    val standardDefaults = InMemoryDemoModeDefaults()

    fun installStandardDefaults() = DemoModeManager.installStandardDefaults(standardDefaults)

    fun caseNamed(suite: String, name: String, body: () -> Unit): DynamicTest =
        DynamicTest.dynamicTest("$suite::$name()", body)

    fun native(description: String, body: () -> Unit): DynamicTest = DynamicTest.dynamicTest("WP-217::$description", body)
}
