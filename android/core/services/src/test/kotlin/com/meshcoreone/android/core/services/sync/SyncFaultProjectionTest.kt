// AndroidOnly: WP-214 Actual sync producer projections preserve reason, diagnostic and simplified state equality.
package com.meshcoreone.android.core.services.sync

import com.meshcoreone.android.core.contracts.domain.errors.SourceServiceFaultCarrier
import com.meshcoreone.android.core.contracts.domain.errors.SyncFault
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Test

class SyncFaultProjectionTest {
    @Test fun everyCasePreservesDiagnosticCauseReasonAndItsNeutralPayload() {
        val reason = "store unavailable \u4e2d\u6587"
        val cases = listOf(
            Triple(SyncCoordinatorError.NotConnected(), SyncFault.NotConnected, "Not connected to device."),
            Triple(SyncCoordinatorError.SyncFailed(reason), SyncFault.SyncFailed(reason), "Sync failed: $reason"),
            Triple(SyncCoordinatorError.AlreadySyncing(), SyncFault.AlreadySyncing, "A sync is already in progress."),
        )
        assertEquals(3, cases.size)
        assertEquals(3, cases.map { it.second }.toSet().size)
        for ((producer, expected, message) in cases) {
            val carrier: SourceServiceFaultCarrier = producer
            assertEquals(expected, carrier.sourceServiceFault)
            assertEquals(expected, producer.sourceServiceFault)
            assertEquals(message, producer.message)
            assertNull(producer.cause)
            val cause = IllegalStateException("private cause")
            producer.initCause(cause)
            assertEquals(expected, producer.sourceServiceFault)
            assertSame(cause, producer.cause)
            val throwable: Throwable = producer
            assertFalse(throwable is CancellationException)
        }
        assertSame(reason, assertIs<SyncFault.SyncFailed>(cases[1].first.sourceServiceFault).reason)
    }

    @Test fun projectionDoesNotChangeFailedStateEqualityOrCancellationIdentity() {
        val first = SyncState.Failed(SyncCoordinatorError.SyncFailed("first"))
        val second = SyncState.Failed(SyncCoordinatorError.AlreadySyncing())
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertFalse(first.isSyncing)
        val cancelled: Throwable = CancellationException("cooperative cancel")
        assertFalse(cancelled is SourceServiceFaultCarrier)
    }
}
