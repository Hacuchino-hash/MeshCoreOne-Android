// AndroidOnly: WP-313 Native tests for the RadioMetricCharts builder (order, skipping, shared packet domain) and drill-down history state.
package com.meshcoreone.android.feature.remotenodes.history

import com.meshcoreone.android.core.l10n.generated.AppRemoteNodesStrings
import com.meshcoreone.android.core.model.NeighborSnapshotEntry
import com.meshcoreone.android.core.model.NodeStatusSnapshotDTO
import com.meshcoreone.android.core.model.OCVPreset
import com.meshcoreone.android.core.model.snapshot
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.remotenodes.common.RemoteNodesText
import com.meshcoreone.android.feature.remotenodes.support.VirtualClock
import com.meshcoreone.android.feature.remotenodes.support.bytes
import com.meshcoreone.android.feature.remotenodes.support.runSuspend
import com.meshcoreone.android.feature.remotenodes.telemetry.ChartAccent
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class RadioMetricChartsTest {
    private val key = bytes(32, 7)
    private val ocv = OCVPreset.LI_ION.ocvArray.toList()

    private fun res(id: Int) = RemoteNodesText.Resource(id)

    @Test
    fun `charts follow Swift order and skip metrics without data`() {
        val s1 = NodeStatusSnapshotDTO(
            timestamp = at(0), nodePublicKey = key, batteryMillivolts = 3800u, lastSNR = 5.0,
            sentDirect = 10u, receiveErrors = 2u,
        )
        val s2 = NodeStatusSnapshotDTO(timestamp = at(60), nodePublicKey = key, batteryMillivolts = 3700u, sentFlood = 30u)
        val items = RadioMetricCharts.build(listOf(s1, s2), ocv)

        assertEquals(3, items.size)
        val battery = assertIs<RadioChartItem.Chart>(items[0]).chart
        assertEquals(res(AppRemoteNodesStrings.remoteNodesHistoryBattery), battery.title)
        assertEquals("V", battery.unit)
        assertEquals(ChartAccent.MINT, battery.series.single().color)
        assertEquals(listOf(3.8, 3.7), battery.series.single().dataPoints.map { it.value })
        assertEquals(listOf(s1.id, s2.id), battery.series.single().dataPoints.map { it.id })
        assertEquals(MetricChartModel.voltageChartDomain(ocv, battery.series.single().dataPoints), battery.yAxisDomain)

        val snr = assertIs<RadioChartItem.Chart>(items[1]).chart
        assertEquals(res(AppRemoteNodesStrings.remoteNodesHistorySnr), snr.title)
        assertEquals("dB", snr.unit)
        assertNull(snr.yAxisDomain)

        val packets = assertIs<RadioChartItem.PacketSection>(items[2])
        assertEquals(res(AppRemoteNodesStrings.remoteNodesHistoryPackets), packets.header)
        assertEquals(
            listOf(res(AppRemoteNodesStrings.remoteNodesHistoryPacketsSent), res(AppRemoteNodesStrings.remoteNodesHistoryReceiveErrors)),
            packets.charts.map { it.title },
        )
        val sent = packets.charts[0]
        assertEquals(
            listOf(res(AppRemoteNodesStrings.remoteNodesHistoryDirect), res(AppRemoteNodesStrings.remoteNodesHistoryFlood)),
            sent.series.map { it.name },
        )
        assertEquals(listOf(ChartAccent.BLUE, ChartAccent.ORANGE), sent.series.map { it.color })
        val errors = packets.charts[1]
        assertEquals(ChartAccent.RED, errors.series.single().color)
        assertEquals("", errors.unit)
        packets.charts.forEach {
            assertEquals(0.0, it.yAxisDomain?.start)
            assertEquals(31.5, it.yAxisDomain?.endInclusive ?: 0.0, 1e-9)
        }
    }

    @Test
    fun `overlay drops an empty series and room posts come last`() {
        val s = NodeStatusSnapshotDTO(
            timestamp = at(0), nodePublicKey = key, lastRSSI = -90, noiseFloor = -118, receivedFlood = 4u,
            directDuplicates = 1u, postedCount = 3u, postPushCount = 2u,
        )
        val items = RadioMetricCharts.build(listOf(s), ocv)
        val titles = items.map { (it as? RadioChartItem.Chart)?.chart?.title }
        assertEquals(
            listOf(
                res(AppRemoteNodesStrings.remoteNodesHistoryRssi), res(AppRemoteNodesStrings.remoteNodesHistoryNoiseFloor), null,
                res(AppRemoteNodesStrings.remoteNodesRoomStatusPostsReceived), res(AppRemoteNodesStrings.remoteNodesRoomStatusPostsPushed),
            ),
            titles,
        )
        val rssi = assertIs<RadioChartItem.Chart>(items[0]).chart
        assertEquals("dBm", rssi.unit)
        assertEquals(ChartAccent.PURPLE, rssi.series.single().color)
        assertEquals(ChartAccent.INDIGO, assertIs<RadioChartItem.Chart>(items[1]).chart.series.single().color)
        assertEquals(ChartAccent.CYAN, assertIs<RadioChartItem.Chart>(items[4]).chart.series.single().color)
        val packets = assertIs<RadioChartItem.PacketSection>(items[2]).charts
        assertEquals(listOf(res(AppRemoteNodesStrings.remoteNodesHistoryFlood)), packets[0].series.map { it.name })
        assertEquals(listOf(res(AppRemoteNodesStrings.remoteNodesHistoryDirect)), packets[1].series.map { it.name })
        assertTrue(packets.none { it.isMultiSeries })
    }

    @Test
    fun `no radio data builds nothing and all-zero packets have no shared domain`() {
        assertTrue(RadioMetricCharts.build(listOf(NodeStatusSnapshotDTO(nodePublicKey = key)), ocv).isEmpty())
        val zero = NodeStatusSnapshotDTO(nodePublicKey = key, sentDirect = 0u)
        assertNull(RadioMetricCharts.packetSection(listOf(zero))?.charts?.single()?.yAxisDomain)
        assertEquals(RadioMetricCharts.build(listOf(zero), ocv), NodeStatusHistory.charts(listOf(zero), ocv))
    }

    @Test
    fun `drill-down holder loads once and filters by the selected range`() = runSuspend {
        val clock = VirtualClock()
        val now = clock.now
        val old = NodeStatusSnapshotDTO(timestamp = now.minusSeconds(10 * 86_400L), nodePublicKey = key)
        val recent = NodeStatusSnapshotDTO(timestamp = now.minusSeconds(3_600), nodePublicKey = key)
        var fetches = 0
        val holder = SnapshotHistoryStateHolder({ fetches++; listOf(old, recent) }, clock, UTC)
        assertTrue(holder.filteredSnapshots.isEmpty())
        holder.load()
        assertEquals(1, fetches)
        assertEquals(HistoryTimeRange.MONTH, holder.state.value.timeRange)
        assertEquals(listOf(old, recent), holder.filteredSnapshots)
        holder.setTimeRange(HistoryTimeRange.WEEK)
        assertEquals(listOf(recent), holder.filteredSnapshots)
    }

    @Test
    fun `neighbor SNR takes the first exact prefix match per snapshot and filters by range`() {
        val prefix = Bytes.of(0x01, 0x02)
        val now = at(40 * 86_400L)
        val snapshots = listOf(
            NodeStatusSnapshotDTO(
                timestamp = at(0), nodePublicKey = key,
                neighborSnapshots = listOf(NeighborSnapshotEntry(prefix, 4.0, 1), NeighborSnapshotEntry(prefix, 9.0, 1)).snapshot(),
            ),
            NodeStatusSnapshotDTO(
                timestamp = now.minusSeconds(60), nodePublicKey = key,
                neighborSnapshots = listOf(NeighborSnapshotEntry(Bytes.of(0x01, 0x02, 0x03), 1.0, 1), NeighborSnapshotEntry(prefix, 6.5, 2)).snapshot(),
            ),
            NodeStatusSnapshotDTO(timestamp = now, nodePublicKey = key),
        )
        val points = NeighborSnrHistory.dataPoints(snapshots, prefix)
        assertEquals(listOf(4.0, 6.5), points.map { it.value })
        val chart = NeighborSnrHistory.chart("Hilltop", points, HistoryTimeRange.MONTH, now, UTC)
        assertEquals(RemoteNodesText.Verbatim("Hilltop"), chart.title)
        assertEquals("dB", chart.unit)
        assertEquals(ChartAccent.BLUE, chart.series.single().color)
        assertEquals(listOf(6.5), chart.series.single().dataPoints.map { it.value })
        assertEquals(2, NeighborSnrHistory.chart("x", points, HistoryTimeRange.ALL, now, UTC).series.single().dataPoints.size)
    }
}
