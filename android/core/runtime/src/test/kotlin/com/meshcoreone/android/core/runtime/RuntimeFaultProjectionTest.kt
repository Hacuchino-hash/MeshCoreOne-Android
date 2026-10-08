// AndroidOnly: WP-207 Paired real runtime projection assertions for the authorized WP-304 consumer seam.
package com.meshcoreone.android.core.runtime

import com.meshcoreone.android.core.contracts.domain.errors.ConnectionFault
import com.meshcoreone.android.core.contracts.domain.errors.RuntimeTimeoutFault
import com.meshcoreone.android.core.contracts.domain.errors.SourceServiceFaultCarrier
import java.io.IOException
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import org.junit.jupiter.api.TestFactory

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeFaultProjectionTest {
    @TestFactory
    fun projectionCases() = listOf(
        nativeCase("runtime faults preserve every connection payload cause and diagnostic") {
            val reason = String(charArrayOf('r', 'e', 'a', 's', 'o', 'n'))
            val capability = String(charArrayOf('c', 'a', 'p'))
            val cause = IOException("synthetic original cause")
            val cases = listOf(
                Triple(ConnectionError.ConnectionFailed(reason, cause), ConnectionFault.ConnectionFailed(reason),
                    "Connection failed: $reason"),
                Triple(ConnectionError.DeviceNotFound(), ConnectionFault.DeviceNotFound, "Device not found"),
                Triple(ConnectionError.NotConnected(), ConnectionFault.NotConnected, "Not connected to device"),
                Triple(ConnectionError.InitializationFailed(reason, cause), ConnectionFault.InitializationFailed(reason),
                    "Device initialization failed: $reason"),
                Triple(ConnectionError.UnsupportedCapability(capability), ConnectionFault.UnsupportedCapability(capability),
                    "Unsupported connection capability: $capability"),
                Triple(ConnectionError.ForeignPhysicalOwner(), ConnectionFault.ForeignPhysicalOwner,
                    "Physical connection is owned by another runtime"),
                Triple(ConnectionError.RetainedPhysicalLink(), ConnectionFault.RetainedPhysicalLink,
                    "A retained physical link cannot become a fresh protocol generation"),
                Triple(ConnectionError.InvalidIdentity(), ConnectionFault.InvalidIdentity,
                    "Radio public key must contain exactly 32 bytes"),
                Triple(ConnectionError.FactoryOwnershipViolation(), ConnectionFault.FactoryOwnershipViolation,
                    "Factory must return its registered generation-owned service handle"),
            )
            assertEquals(9, cases.size)
            for ((failure, expected, diagnostic) in cases) {
                val carrier: SourceServiceFaultCarrier = failure
                assertEquals(expected, carrier.sourceServiceFault)
                assertEquals(expected, carrier.sourceServiceFault)
                assertEquals(diagnostic, failure.message)
                when (val fault = failure.sourceServiceFault) {
                    is ConnectionFault.ConnectionFailed -> { assertSame(reason, fault.reason); assertSame(cause, failure.cause) }
                    is ConnectionFault.InitializationFailed -> { assertSame(reason, fault.reason); assertSame(cause, failure.cause) }
                    is ConnectionFault.UnsupportedCapability -> { assertSame(capability, fault.capability); assertNull(failure.cause) }
                    else -> assertNull(failure.cause)
                }
            }
            assertEquals(cases.map { it.first.javaClass }.toSet(), ConnectionError::class.java.permittedSubclasses.toSet())
            assertEquals(cases.map { it.second.javaClass }.toSet(), ConnectionFault::class.java.permittedSubclasses.toSet())
        },
        nativeCase("runtime timeout projection preserves operation Duration and constructor behavior") {
            val name = String(charArrayOf('s', 'y', 'n', 'c'))
            for (duration in listOf(Duration.ZERO, 5.seconds, (-1).seconds, Duration.INFINITE)) {
                val failure = TimeoutError(name, duration)
                val carrier: SourceServiceFaultCarrier = failure
                val fault = assertIs<RuntimeTimeoutFault>(carrier.sourceServiceFault)
                assertSame(name, fault.operationName)
                assertEquals(duration, fault.timeout)
                assertEquals(RuntimeTimeoutFault(name, duration), failure.sourceServiceFault)
                assertEquals("Operation '$name' timed out after $duration", failure.message)
                assertNull(failure.cause)
            }
        },
        nativeCase("actual runtime deadline projects its fault and joins the cancelled child") {
            var ended = false
            val failure = assertFailsWith<TimeoutError> {
                withRuntimeTimeout(3.seconds, "session.start", TestClock(testScheduler)) {
                    try { awaitCancellation() } finally { ended = true }
                }
            }
            assertTrue(ended)
            assertEquals(RuntimeTimeoutFault("session.start", 3.seconds), failure.sourceServiceFault)
            assertEquals("Operation 'session.start' timed out after 3s", failure.message)
        },
        nativeCase("cooperative runtime deadline remains cancellation without a fault carrier") {
            var ended = false
            val cancelled = assertFailsWith<CancellationException> {
                withCooperativeTimeout(2.seconds, TestClock(testScheduler)) {
                    try { awaitCancellation() } finally { ended = true }
                }
            }
            assertTrue(ended)
            assertFalse(cancelled is SourceServiceFaultCarrier)
        },
    )
}
