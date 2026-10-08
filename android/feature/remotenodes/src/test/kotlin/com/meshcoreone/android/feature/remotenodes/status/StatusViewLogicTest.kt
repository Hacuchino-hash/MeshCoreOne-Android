// AndroidOnly: WP-313 Native coverage of the status sections' view logic (section bodies, neighbour rows, telemetry rows, sheet lifecycle).
package com.meshcoreone.android.feature.remotenodes.status

import com.meshcoreone.android.core.l10n.R
import com.meshcoreone.android.core.l10n.generated.AppRemoteNodesStrings
import com.meshcoreone.android.core.model.NeighborSnapshotEntry
import com.meshcoreone.android.core.model.NodeLocationFix
import com.meshcoreone.android.core.model.NodeStatusSnapshotDTO
import com.meshcoreone.android.core.model.OCVPreset
import com.meshcoreone.android.core.model.snapshot
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.Neighbour
import com.meshcoreone.android.core.protocol.lpp.LPPDataPoint
import com.meshcoreone.android.core.protocol.lpp.LPPSensorType
import com.meshcoreone.android.core.protocol.lpp.LPPValue
import com.meshcoreone.android.feature.remotenodes.common.RemoteNodesText
import com.meshcoreone.android.feature.remotenodes.resolver.NodeNameMatchKind
import com.meshcoreone.android.feature.remotenodes.support.TEST_RADIO
import com.meshcoreone.android.feature.remotenodes.support.VirtualClock
import com.meshcoreone.android.feature.remotenodes.support.runSuspend
import com.meshcoreone.android.feature.remotenodes.support.session
import com.meshcoreone.android.feature.remotenodes.telemetry.localizedNameRes
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class StatusViewLogicTest {
    private val us = Locale.US
    private val clock = VirtualClock()

    @Test
    fun `status and telemetry bodies follow the Swift branch order`() {
        val error = RemoteNodesText.Verbatim("e")
        assertTrue(StatusSections.shouldLoadOnExpand(isExpanded = true, isLoaded = false, isLoading = false))
        assertFalse(StatusSections.shouldLoadOnExpand(isExpanded = true, isLoaded = true, isLoading = false))
        assertFalse(StatusSections.shouldLoadOnExpand(isExpanded = true, isLoaded = false, isLoading = true))

        assertEquals(SectionBody.Empty, StatusSections.statusBody(NodeStatusState()))
        assertEquals(SectionBody.Loading, StatusSections.statusBody(NodeStatusState(isLoadingStatus = true)))
        assertEquals(SectionBody.Error(error), StatusSections.statusBody(NodeStatusState(statusSectionError = error)))
        assertEquals(
            SectionBody.Content,
            StatusSections.statusBody(NodeStatusState(status = statusResponse(), isLoadingStatus = true, statusSectionError = error)),
        )

        val noTelemetry = SectionBody.Message(AppRemoteNodesStrings.remoteNodesStatusNoTelemetryData)
        assertEquals(noTelemetry, StatusSections.telemetryBody(NodeStatusState()))
        assertEquals(SectionBody.Loading, StatusSections.telemetryBody(NodeStatusState(isLoadingTelemetry = true, telemetry = telemetryResponse())))
        assertEquals(SectionBody.Error(error), StatusSections.telemetryBody(NodeStatusState(telemetrySectionError = error)))
        assertEquals(
            SectionBody.Message(AppRemoteNodesStrings.remoteNodesStatusNoSensorData),
            StatusSections.telemetryBody(NodeStatusState(telemetry = telemetryResponse())),
        )
        val points = telemetryResponse().dataPoints
        assertEquals(SectionBody.Content, StatusSections.telemetryBody(NodeStatusState(telemetry = telemetryResponse(), cachedDataPoints = points)))
    }

    @Test
    fun `telemetry layout groups multiple channels and labels rows`() {
        val voltage = LPPDataPoint(2u, LPPSensorType.VOLTAGE, LPPValue.Float(3.85))
        val ambient = LPPDataPoint(2u, LPPSensorType.TEMPERATURE, LPPValue.Float(20.0))
        val mcu = LPPDataPoint(2u, LPPSensorType.TEMPERATURE, LPPValue.Float(30.0))
        val other = LPPDataPoint(1u, LPPSensorType.TEMPERATURE, LPPValue.Float(10.0))
        val grouped = StatusSections.telemetryLayout(NodeStatusState(cachedDataPoints = listOf(voltage, ambient, other, mcu)))
        assertEquals(
            TelemetryLayout.Grouped(listOf(TelemetryChannelGroup(1u, listOf(other)), TelemetryChannelGroup(2u, listOf(voltage, ambient, mcu)))),
            grouped,
        )
        val flat = StatusSections.telemetryLayout(NodeStatusState(cachedDataPoints = listOf(voltage, ambient, mcu)))
        assertEquals(
            TelemetryLayout.Flat(
                listOf(
                    TelemetryRowModel(voltage, LPPSensorType.VOLTAGE.localizedNameRes, 66),
                    TelemetryRowModel(ambient, AppRemoteNodesStrings.remoteNodesStatusSensorTemperature, null),
                    TelemetryRowModel(mcu, AppRemoteNodesStrings.remoteNodesStatusSensorMcuTemperature, null),
                ),
            ),
            flat,
        )
        assertNull(telemetryBatteryPercentage(LPPDataPoint(1u, LPPSensorType.VOLTAGE, LPPValue.Integer(4)), OCVPreset.LI_ION.ocvArray))
    }

    @Test
    fun `location route carries the fix and the session name`() {
        val fix = NodeLocationFix(37.7749, -122.4194, 42.0)
        val gps = LPPDataPoint(1u, LPPSensorType.GPS, LPPValue.Gps(37.7749, -122.4194, 42.0))
        val state = NodeStatusState(session = session(name = "Hilltop"), cachedDataPoints = listOf(gps))
        assertEquals(NodeStatusRoute.LocationMap(fix, "Hilltop"), StatusSections.locationRoute(state))
        assertNull(StatusSections.locationRoute(NodeStatusState()))
    }

    @Test
    fun `repeater sections handle owner info and discovery`() {
        val error = RemoteNodesText.Verbatim("e")
        assertEquals(SectionBody.Loading, StatusSections.ownerInfoBody(RepeaterStatusState(isLoadingOwnerInfo = true, ownerInfoError = error)))
        assertEquals(SectionBody.Error(error), StatusSections.ownerInfoBody(RepeaterStatusState(ownerInfoError = error)))
        assertEquals(SectionBody.Content, StatusSections.ownerInfoBody(RepeaterStatusState()))
        val noOwner = RemoteNodesText.Resource(AppRemoteNodesStrings.remoteNodesStatusNoOwnerInfo)
        assertEquals(noOwner, StatusSections.ownerInfoText(RepeaterStatusState(ownerInfo = "")))
        assertEquals(RemoteNodesText.Verbatim("hi"), StatusSections.ownerInfoText(RepeaterStatusState(ownerInfo = "hi")))

        assertEquals(SectionBody.Loading, StatusSections.neighborsBody(RepeaterStatusState(isLoadingNeighbors = true)))
        assertEquals(
            SectionBody.Message(AppRemoteNodesStrings.remoteNodesStatusNoNeighbors),
            StatusSections.neighborsBody(RepeaterStatusState()),
        )
        val discovering = RepeaterStatusState(isLoadingNeighbors = true, neighborsSectionError = error, isDiscovering = true, discoverySecondsRemaining = 42)
        assertEquals(SectionBody.Content, StatusSections.neighborsBody(discovering))
        assertFalse(StatusSections.neighborsReloadSpinning(discovering))
        assertEquals(
            RemoteNodesText.Resource(R.string.l10n_app_remotenodes_remotenodes_status_discoveringseconds, listOf(42)),
            StatusSections.discoverButtonText(discovering),
        )
        assertEquals(
            RemoteNodesText.Resource(AppRemoteNodesStrings.remoteNodesStatusDiscoverNeighbors),
            StatusSections.discoverButtonText(RepeaterStatusState()),
        )
    }

    @Test
    fun `neighbour rows resolve names, deltas, new badges and disappeared entries`() {
        val a = Bytes.of(0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6)
        val b = Bytes.of(0xB1, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6)
        val c = Bytes.of(0xC1, 0xC2, 0xC3, 0xC4, 0xC5, 0xC6)
        val named = contact(publicKey = c + Bytes(ByteArray(26)), name = "Charlie")
        val context = NeighborResolutionContext(listOf(named), emptyList(), null, us)
        val previous = NodeStatusSnapshotDTO(
            nodePublicKey = TEST_PUBLIC_KEY,
            neighborSnapshots = listOf(NeighborSnapshotEntry(a, 5.0, 10), NeighborSnapshotEntry(b, -2.25, 20)).snapshot(),
        )
        val neighbours = listOf(Neighbour(a, 30, 5.5), Neighbour(c, 125, 5.0))

        val rows = NeighborRows.rows(neighbours, previous, setOf(a, b), 2, context)
        val unknown = RemoteNodesText.Resource(AppRemoteNodesStrings.remoteNodesStatusUnknown)
        assertEquals(unknown, rows[0].displayName)
        assertEquals(NodeNameMatchKind.UNRESOLVED, rows[0].matchKind)
        assertEquals("A1A2", rows[0].keyHex)
        assertEquals(RemoteNodesText.Resource(R.string.l10n_app_remotenodes_remotenodes_status_secondsago, listOf(30)), rows[0].lastSeen)
        assertEquals(RemoteNodesText.Resource(R.string.l10n_app_remotenodes_remotenodes_status_snrformat, listOf("5.5")), rows[0].snrText)
        assertEquals(StatusDelta.snr(0.5), rows[0].snrDelta)
        assertFalse(rows[0].isNew)
        assertEquals(NodeStatusRoute.NeighborChart(unknown, a), rows[0].route)

        assertEquals(RemoteNodesText.Verbatim("Charlie"), rows[1].displayName)
        assertEquals(NodeNameMatchKind.EXACT, rows[1].matchKind)
        assertEquals(RemoteNodesText.Resource(R.string.l10n_app_remotenodes_remotenodes_status_minutesago, listOf(2)), rows[1].lastSeen)
        assertNull(rows[1].snrDelta)
        assertTrue(rows[1].isNew)

        val gone = NeighborRows.disappeared(neighbours, previous, 3, context)
        assertEquals(1, gone.size)
        assertEquals(RemoteNodesText.Verbatim("B1B2B3"), gone[0].displayName)
        assertEquals(RemoteNodesText.Resource(R.string.l10n_app_remotenodes_remotenodes_status_snrformat, listOf("-2.2")), gone[0].snrText)

        val firstVisit = NeighborRows.rows(neighbours, null, emptySet(), 2, context)
        assertFalse(firstVisit.any { it.isNew || it.snrDelta != null })
        assertTrue(NeighborRows.disappeared(neighbours, null, 2, context).isEmpty())
    }

    @Test
    fun `last seen and SNR delta thresholds`() {
        assertEquals(RemoteNodesText.Resource(R.string.l10n_app_remotenodes_remotenodes_status_secondsago, listOf(59)), NeighborRows.lastSeen(59))
        assertEquals(RemoteNodesText.Resource(R.string.l10n_app_remotenodes_remotenodes_status_minutesago, listOf(1)), NeighborRows.lastSeen(60))
        assertEquals(RemoteNodesText.Resource(R.string.l10n_app_remotenodes_remotenodes_status_minutesago, listOf(59)), NeighborRows.lastSeen(3599))
        assertEquals(RemoteNodesText.Resource(R.string.l10n_app_remotenodes_remotenodes_status_hoursago, listOf(2)), NeighborRows.lastSeen(7300))
        val previous = NeighborSnapshotEntry(Bytes.of(1), 5.0, 0)
        assertNull(NeighborRows.snrDelta(5.05, previous))
        assertEquals(StatusDelta.snr(-0.25), NeighborRows.snrDelta(4.75, previous))
        assertNull(NeighborRows.snrDelta(9.0, null))
    }

    @Test
    fun `repeater sheet start loads sources and OCV, dismiss tears down`() = runSuspend {
        val admin = FakeStatusAdmin()
        admin.setCLIHandler { _, _ -> }
        val contacts = FakeContactOcv(contact(ocvPreset = "lto"))
        val model = RepeaterStatusStateHolder(clock, FakeFaults, this)
        model.configure({ admin }, { contacts }, { null }, { null })
        val node = session()
        val stored = contact(name = "Stored")
        val store = FakeHistoryStore(contacts = listOf(stored))

        val sources = StatusSheetLifecycle.startRepeater(model, node, TEST_RADIO, store)
        assertEquals(NeighborSources(listOf(stored), emptyList()), sources)
        assertEquals(OCVPreset.LTO, model.helper.state.value.selectedOCVPreset)
        assertTrue(admin.statusSlot != null && admin.neighboursSlot != null && admin.telemetrySlot != null)
        assertEquals(stored, StatusSheetLifecycle.refreshRouteContact(node, store, null))

        store.failure = OtherFault()
        assertEquals(NeighborSources(emptyList(), emptyList()), StatusSheetLifecycle.startRepeater(model, node, TEST_RADIO, store))
        assertEquals(stored, StatusSheetLifecycle.refreshRouteContact(node, store, stored), "A failed refresh keeps the current contact")
        assertEquals(NeighborSources(emptyList(), emptyList()), StatusSheetLifecycle.startRepeater(model, node, null, store))

        StatusSheetLifecycle.dismissRepeater(model)
        assertNull(admin.statusSlot)
        assertNull(admin.cliSlot)
        assertFalse(model.state.value.isDiscovering)
    }

    @Test
    fun `room and telemetry sheet starts`() = runSuspend {
        val admin = FakeStatusAdmin()
        val contacts = FakeContactOcv(contact(ocvPreset = "niMH"))
        val room = RoomStatusStateHolder(clock, FakeFaults)
        room.configure({ admin }, { contacts }, { null })
        StatusSheetLifecycle.startRoom(room, session(), TEST_RADIO)
        assertEquals(OCVPreset.NI_MH, room.helper.state.value.selectedOCVPreset)
        assertTrue(admin.statusSlot != null && admin.telemetrySlot != null)
        StatusSheetLifecycle.dismissRoom(room)
        assertNull(admin.telemetrySlot)

        val telemetry = NodeTelemetryStateHolder(clock, FakeFaults)
        telemetry.configure({ null }, { contacts }, { null }, contact())
        StatusSheetLifecycle.startTelemetry(telemetry, contact(), null)
        assertTrue(telemetry.helper.state.value.telemetryExpanded)
        assertEquals(OCVPreset.LI_ION, telemetry.helper.state.value.selectedOCVPreset, "No radio: OCV is not loaded")
        assertEquals(TEST_PUBLIC_KEY.prefix(6), telemetry.helper.state.value.effectivePublicKeyPrefix)
    }
}
