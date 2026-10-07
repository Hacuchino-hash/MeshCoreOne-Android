// AndroidOnly: WP-217 byte-for-byte agreement between the Kotlin seed and the frozen Swift seed under swiftc.
package com.meshcoreone.android.core.services.simulator

import com.meshcoreone.android.core.services.simulator.SimulatorTestSupport.native
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class SimulatorSeedSwiftOracleTest {
    @TestFactory
    fun swiftOracleTests(): List<DynamicTest> = listOf(
        native("seed rows match the Swift oracle line for line at the pinned instant") {
            val actual = SimulatorSeedDump.lines(SimulatorTestSupport.oracleNow)
            val expected = SimulatorSeedSwiftOracleVectors.DUMP_AT_INTEGRAL_NOW
            assertEquals(expected.size, actual.size, "row count")
            expected.zip(actual).forEachIndexed { index, (want, got) -> assertEquals(want, got, "row $index") }
            assertEquals(SimulatorSeedSwiftOracleVectors.DUMP_SHA256_AT_INTEGRAL_NOW, SimulatorSeedDump.sha256(actual))
        },
        native("seed rows match the Swift oracle at a fractional instant") {
            val fractionalNow = Instant.ofEpochSecond(SimulatorSeedSwiftOracleVectors.INTEGRAL_NOW_EPOCH_SECONDS, 750_000_000)
            assertEquals(
                SimulatorSeedSwiftOracleVectors.DUMP_SHA256_AT_FRACTIONAL_NOW,
                SimulatorSeedDump.sha256(SimulatorSeedDump.lines(fractionalNow)),
            )
        },
    )
}
