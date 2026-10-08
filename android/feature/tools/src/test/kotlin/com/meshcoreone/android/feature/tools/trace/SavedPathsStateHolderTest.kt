// AndroidOnly: WP-314 Saved-path list/detail and presentation rules (no source unit tests); fixture store, no radio.
package com.meshcoreone.android.feature.tools.trace

import com.meshcoreone.android.core.contracts.domain.TracePathPersisting
import com.meshcoreone.android.core.model.DeviceDTO
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedPathsStateHolderTest {
    private object Strings : SavedPathsStrings {
        override val loadFailed = "Failed to load saved paths."
        override val renameFailed = "Failed to rename path."
        override val deleteFailed = "Failed to delete path."
    }

    private val harness = TraceHarness()
    private val store = InMemoryTracePathStore { harness.time.now() }
    private val diagnostics = RecordingDiagnostics()
    private val holder = SavedPathsStateHolder(Strings, diagnostics)
    private var connected: DeviceDTO? = device()

    init {
        holder.configure(object : SavedPathsDependencies {
            override fun savedPaths(): TracePathPersisting = store
            override fun connectedDevice(): DeviceDTO? = connected
        })
    }

    @Test
    fun `load lists the connected radio's paths and a failure keeps the old list with a message`() {
        val mine = savedPath(Bytes.of(1), radioId = RADIO)
        store.insert(mine)
        store.insert(savedPath(Bytes.of(2)))
        harness.complete { holder.loadSavedPaths() }
        assertEquals(listOf(mine), holder.state.value.savedPaths)
        assertFalse(holder.state.value.isLoading)
        store.failure = IOException("db")
        harness.complete { holder.loadSavedPaths() }
        assertEquals("Failed to load saved paths.", holder.state.value.errorMessage)
        assertEquals(listOf(mine), holder.state.value.savedPaths)
        assertFalse(holder.state.value.isLoading)
    }

    @Test
    fun `without a connected radio loading is a no-op`() {
        connected = null
        harness.complete { holder.loadSavedPaths() }
        assertTrue(holder.state.value.savedPaths.isEmpty())
        assertNull(holder.state.value.errorMessage)
    }

    @Test
    fun `rename reloads and delete drops the row locally`() {
        val path = savedPath(Bytes.of(1), radioId = RADIO)
        store.insert(path)
        harness.complete { holder.loadSavedPaths() }
        harness.complete { holder.renamePath(path, "Ridge loop") }
        assertEquals("Ridge loop", holder.state.value.savedPaths.single().name)
        harness.complete { holder.deletePath(path) }
        assertTrue(holder.state.value.savedPaths.isEmpty())
        assertTrue(store.paths.isEmpty())
    }

    @Test
    fun `rename and delete failures set their messages`() {
        val path = savedPath(Bytes.of(1), radioId = RADIO)
        store.insert(path)
        harness.complete { holder.loadSavedPaths() }
        store.failure = IOException("locked")
        harness.complete { holder.renamePath(path, "x") }
        assertEquals("Failed to rename path.", holder.state.value.errorMessage)
        harness.complete { holder.deletePath(path) }
        assertEquals("Failed to delete path.", holder.state.value.errorMessage)
        assertEquals(listOf(path), holder.state.value.savedPaths)
        assertEquals(listOf("renamePath", "deletePath"), diagnostics.failures.map { it.first })
    }

    @Test
    fun `detail sorts runs newest first and derives best, average and success rate`() {
        val runs = listOf(run(T0.minusSeconds(30), 300), run(T0, 100), run(T0.minusSeconds(60), success = false), run(T0.minusSeconds(10), 200))
        val detail = SavedPathDetailState(savedPath(Bytes.of(1, 2), hashSize = 2, runs = runs))
        assertEquals(listOf(T0, T0.minusSeconds(10), T0.minusSeconds(30), T0.minusSeconds(60)), detail.sortedRuns.map { it.date })
        assertEquals(3, detail.successfulRuns.size)
        assertEquals(100L, detail.bestRoundTrip)
        assertEquals(200L, detail.averageRoundTrip)
        assertEquals("75%", detail.successRateText)
        assertEquals(2L, detail.hashSize)
    }

    @Test
    fun `detail refresh replaces the path from the store`() {
        val path = savedPath(Bytes.of(1), radioId = RADIO)
        store.insert(path)
        val detail = SavedPathDetailStateHolder(path, diagnostics)
        detail.configure { store }
        harness.complete { store.appendTracePathRun(com.meshcoreone.android.core.contracts.domain.EntityKey(RADIO, path.id), run(T0)) }
        harness.complete { detail.refresh() }
        assertEquals(1, detail.state.value.savedPath.runs.size)
        assertFalse(detail.state.value.isLoading)
    }

    @Test
    fun `health, trend, comparison and hop display follow the row rules`() {
        assertEquals(SavedPathHealth.HEALTHY, SavedPathHealth.of(90))
        assertEquals(SavedPathHealth.DEGRADED, SavedPathHealth.of(50))
        assertEquals(SavedPathHealth.POOR, SavedPathHealth.of(49))
        assertNull(RttSummary.of(emptyList()))
        assertEquals(RttSummary(100, RttTrend.STABLE), RttSummary.of(listOf(100L)))
        assertEquals(RttSummary(150, RttTrend.INCREASING), RttSummary.of(listOf(100L, 100L, 200L, 200L)))
        assertEquals(RttTrend.DECREASING, RttSummary.of(listOf(300L, 100L))?.trend)
        assertEquals(RttTrend.STABLE, RttSummary.of(listOf(100L, 150L))?.trend)
        assertEquals(RttComparison(50, 50.0), RttComparison.of(150, 100))
        assertEquals(0.0, RttComparison.of(10, 0).percentChange)
        assertFalse(RttComparison.of(100, 100).changed)
        assertEquals(2.0, TraceHopDisplay.displaySnr(1.0, HopStats(5.0, 1.0, 9.0), 2.0, isBatchInProgress = true))
        assertEquals(5.0, TraceHopDisplay.displaySnr(1.0, HopStats(5.0, 1.0, 9.0), 2.0, isBatchInProgress = false))
        assertEquals(1.0, TraceHopDisplay.displaySnr(1.0, null, null, isBatchInProgress = false))
        assertEquals(listOf("AABB", "CC"), TraceHopDisplay.hopHexStrings(Bytes.of(0xAA, 0xBB, 0xCC), 2))
    }
}
