// PortedFrom: MC1Tests/State/PendingExternalURLTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.app.deeplinks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PendingExternalRouteTest {
    @Test
    @SourceCases("PendingExternalURLTests::A URL submitted before ready is held, not routed, until markReady()")
    fun coldStartHoldsUntilReady() = runTest {
        val routed = mutableListOf<String>()
        val pending = PendingExternalRoute { routed += it; DeepLinkRouteOutcome.Navigated }
        assertIs<PendingExternalRoute.SubmitOutcome.Staged>(pending.submit(PendingExternalRoute.Delivery("1", "meshcore://map?lat=1&lon=2")))
        assertNotNull(pending.snapshot())
        assertEquals(emptyList(), routed)
        assertIs<PendingExternalRoute.SubmitOutcome.Routed>(pending.markReady())
        assertEquals(listOf("meshcore://map?lat=1&lon=2"), routed)
        assertNull(pending.snapshot())
    }

    @Test
    @SourceCases("PendingExternalURLTests::markReady routes a held URL exactly once()")
    fun repeatedReadinessAndDuplicateDeliveryRouteOnce() = runTest {
        var count = 0
        val pending = PendingExternalRoute { count++; DeepLinkRouteOutcome.Navigated }
        val delivery = PendingExternalRoute.Delivery("launch", "meshcore://map?lat=1&lon=2")
        pending.submit(delivery)
        pending.markReady()
        pending.markReady()
        assertIs<PendingExternalRoute.SubmitOutcome.Duplicate>(pending.submit(delivery))
        assertEquals(1, count)
    }

    @Test
    @SourceCases("PendingExternalURLTests::A URL submitted after ready routes immediately()")
    fun warmDeliveryRoutesImmediately() = runTest {
        val routed = mutableListOf<String>()
        val pending = PendingExternalRoute { routed += it; DeepLinkRouteOutcome.Navigated }
        pending.markReady()
        assertIs<PendingExternalRoute.SubmitOutcome.Routed>(
            pending.submit(PendingExternalRoute.Delivery("warm", "meshcoreone://hashtag/general")),
        )
        assertEquals(1, routed.size)
    }

    @Test
    fun cancellationRetainsThePendingDeliveryForOneRetry() = runTest {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val pending = PendingExternalRoute {
            calls++
            started.complete(Unit)
            gate.await()
            DeepLinkRouteOutcome.Navigated
        }
        pending.markReady()
        val job = launch { pending.submit(PendingExternalRoute.Delivery("cancel", "meshcore://contact/add")) }
        started.await()
        job.cancelAndJoin()
        assertNotNull(pending.snapshot())
        gate.complete(Unit)
        assertIs<PendingExternalRoute.SubmitOutcome.Routed>(pending.retry())
        assertNull(pending.snapshot())
        assertEquals(2, calls)
    }

    @Test
    fun latestColdDeliveryReplacesAnOlderPendingRoute() = runTest {
        val routed = mutableListOf<String>()
        val pending = PendingExternalRoute { routed += it; DeepLinkRouteOutcome.Navigated }
        pending.submit(PendingExternalRoute.Delivery("1", "meshcore://map?lat=1&lon=2"))
        pending.submit(PendingExternalRoute.Delivery("2", "meshcoreone://hashtag/general"))
        pending.markReady()
        assertEquals(listOf("meshcoreone://hashtag/general"), routed)
    }
}
