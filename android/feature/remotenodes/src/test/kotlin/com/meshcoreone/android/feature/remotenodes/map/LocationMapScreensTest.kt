// AndroidOnly: WP-313 Native tests for location history preview, full-screen location map state and report callout logic.
package com.meshcoreone.android.feature.remotenodes.map

import com.meshcoreone.android.core.l10n.generated.AppRemoteNodesStrings
import com.meshcoreone.android.core.model.Coordinate
import com.meshcoreone.android.core.model.NodeStatusSnapshotDTO
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

class LocationMapScreensTest {
    private val snapshots = listOf(
        locationSnapshot(-180.0, 37.0, -122.0, altitude = 5.0),
        locationSnapshot(-150.0, null, null),
        locationSnapshot(-120.0, 37.2, -121.8),
        locationSnapshot(-90.0, 0.0, 0.0),
    )

    // LocationHistorySection

    @Test
    fun previewPlotsTheWholePathOrOnlyTheLatestFix() {
        assertEquals(
            listOf(Coordinate(37.0, -122.0), Coordinate(37.2, -121.8)),
            LocationHistoryPreview.plottedCoordinates(snapshots, showsFullPath = true),
        )
        assertEquals(listOf(Coordinate(37.2, -121.8)), LocationHistoryPreview.plottedCoordinates(snapshots, showsFullPath = false))
        assertTrue(LocationHistoryPreview.plottedCoordinates(snapshots.subList(1, 2), showsFullPath = true).isEmpty())
    }

    @Test
    fun previewRegionUsesAFixedSpanForOneFixAndPaddingForSeveral() {
        assertEquals(
            CoordinateRegion(Coordinate(37.2, -121.8), 0.02, 0.02),
            LocationHistoryPreview.currentRegion(snapshots, showsFullPath = false),
        )
        val full = LocationHistoryPreview.currentRegion(snapshots, showsFullPath = true)
        assertEquals(listOf(Coordinate(37.0, -122.0), Coordinate(37.2, -121.8)).boundingRegion(1.3), full)
        assertTrue(abs(full.latitudeDelta - 0.26) < 1e-9)
        assertEquals(LocationHistoryPreview.FALLBACK_REGION, LocationHistoryPreview.currentRegion(emptyList(), showsFullPath = true))
        assertNull(LocationHistoryPreview.region(emptyList()))
    }

    @Test
    fun reportsListIsEveryValidFixNewestFirst() {
        val reports = LocationHistoryPreview.locationReports(snapshots)
        assertEquals(listOf(snapshots[2].id, snapshots[0].id), reports.map { it.id })
        assertTrue(LocationHistoryPreview.isLatest(reports[0], reports))
        assertFalse(LocationHistoryPreview.isLatest(reports[1], reports))
        assertEquals(LocationMapLaunch(snapshots[0].id), LocationHistoryPreview.rowTap(snapshots[0]))
        assertEquals(LocationMapLaunch(null), LocationHistoryPreview.expandTap())
    }

    @Test
    fun rebuildOnlyWhenPlottedCoordinatesChange() {
        val ids = SequentialIds()
        val first = LocationHistoryPreview.rebuild(LocationPreviewState(), snapshots, showsFullPath = true, idFactory = ids::next)
        assertEquals(1, first.cameraVersion)
        assertEquals(2, first.path?.points?.size)
        assertEquals(1, first.path?.lines?.size)

        // A re-render with the same coordinates keeps the built path and its pin ids.
        val altitudeOnly = snapshots.map { it.copy(altitude = 99.0) }
        assertSame(first, LocationHistoryPreview.rebuild(first, altitudeOnly, showsFullPath = true, idFactory = ids::next))

        // Latest-fix mode is a lone hero pin with no report.
        val latest = LocationHistoryPreview.rebuild(first, snapshots, showsFullPath = false, idFactory = ids::next)
        assertEquals(2, latest.cameraVersion)
        assertEquals(listOf(PinStyle.LOCATION_FIX_LATEST), latest.path?.points?.map { it.pinStyle })
        assertNull(latest.path?.points?.single()?.hopIndex)
        assertTrue(latest.path?.reports.orEmpty().isEmpty())

        // Nothing plottable clears the path without bumping the camera.
        val cleared = LocationHistoryPreview.rebuild(latest, emptyList(), showsFullPath = true, idFactory = ids::next)
        assertNull(cleared.path)
        assertEquals(2, cleared.cameraVersion)
    }

    @Test
    fun previewDecimatesWhileTheFullScreenMapKeepsEveryReport() {
        val track = (0 until 120).map { locationSnapshot(it * 10.0, 37.0 + it * 0.001, -122.0) }
        val preview = LocationHistoryPreview.rebuild(LocationPreviewState(), track, showsFullPath = true)
        assertTrue((preview.path?.points?.size ?: 0) <= LocationPathMapBuilder.MAX_PINS)

        val content = NodeLocationMapContent.fromSnapshots(track, initialSelection = track[3].id)
        assertEquals(120, content.points.size)
        assertEquals(120, content.reports.size)
        assertEquals(track[3].id, content.initialSelectionId)
        assertEquals(AppRemoteNodesStrings.remoteNodesStatusLocationMapTitle, content.titleResource)
    }

    // NodeLocationMapView

    private fun content(snapshots: List<NodeStatusSnapshotDTO>, selection: UUID?) =
        NodeLocationMapContent.fromSnapshots(snapshots, selection, SequentialIds()::next)

    @Test
    fun styleLoadFitsOnceAndAutoSelectsTheRowTappedReport() {
        val holder = NodeLocationMapStateHolder(content(snapshots, snapshots[2].id))
        holder.onAppear()
        holder.onStyleLoaded(false)
        assertEquals(0, holder.state.value.cameraRegionVersion)

        holder.onStyleLoaded(true)
        val fitted = holder.state.value
        assertEquals(1, fitted.cameraRegionVersion)
        assertEquals(listOf(Coordinate(37.0, -122.0), Coordinate(37.2, -121.8)).boundingRegion(1.3), fitted.cameraRegion)
        // snapshots[2] is the hero, the second plotted pin.
        assertEquals(UUID(0, 2), fitted.pendingSelectionPointId)
        assertEquals(1, fitted.selectionRequestVersion)

        holder.onStyleLoaded(true)
        assertEquals(fitted, holder.state.value)
    }

    @Test
    fun aSingleFixFitsWithTheWiderSpanAndUnknownSelectionsRequestNothing() {
        val holder = NodeLocationMapStateHolder(content(snapshots.subList(0, 1), UUID(9, 9)))
        holder.onAppear()
        holder.onStyleLoaded(true)
        val state = holder.state.value
        assertEquals(CoordinateRegion(Coordinate(37.0, -122.0), 0.05, 0.05), state.cameraRegion)
        assertNull(state.pendingSelectionPointId)
        assertEquals(0, state.selectionRequestVersion)
    }

    @Test
    fun appearSnapshotsContentOnlyOnce() {
        val holder = NodeLocationMapStateHolder(content(snapshots, null))
        assertTrue(holder.state.value.displayPoints.isEmpty())
        holder.onAppear()
        val afterFirst = holder.state.value
        assertEquals(2, afterFirst.displayPoints.size)
        assertEquals(1, afterFirst.displayLines.size)
        holder.onAppear()
        assertSame(afterFirst, holder.state.value)
    }

    @Test
    fun pinTapsShowTheCalloutAndMapOrCameraChangesDismissIt() {
        val holder = NodeLocationMapStateHolder(content(snapshots, null))
        holder.onAppear()
        holder.onPointTap(UUID(0, 1))
        val selected = holder.state.value.selectedReport
        assertEquals(UUID(0, 1), selected?.pointId)
        assertEquals(LocationReportCalloutContent(snapshots[0].timestamp, 5.0), selected?.callout)

        holder.onPointTap(UUID(7, 7))
        assertEquals(selected, holder.state.value.selectedReport)

        holder.onMapTap()
        assertNull(holder.state.value.selectedReport)

        holder.onPointTap(UUID(0, 2))
        val moved = CoordinateRegion(Coordinate(1.0, 2.0), 3.0, 4.0)
        holder.onCameraRegionChange(moved)
        assertNull(holder.state.value.selectedReport)
        assertEquals(moved, holder.state.value.cameraRegion)
        assertEquals(0, holder.state.value.cameraRegionVersion)
    }

    @Test
    fun centerAllRefitsAndIsDisabledWithoutPins() {
        val holder = NodeLocationMapStateHolder(content(snapshots, null))
        holder.onAppear()
        holder.onCenteredOnUserChange(true)
        holder.centerAll()
        assertFalse(holder.state.value.isCenteredOnUser)
        assertEquals(1, holder.state.value.cameraRegionVersion)
        assertTrue(holder.state.value.isCenterAllEnabled)

        val empty = NodeLocationMapStateHolder(content(emptyList(), null))
        empty.onAppear()
        empty.centerAll()
        assertFalse(empty.state.value.isCenterAllEnabled)
        assertEquals(0, empty.state.value.cameraRegionVersion)
        assertNull(empty.state.value.cameraRegion)
    }

    // LocationReportCallout

    @Test
    fun calloutDetailAppendsAltitudeOnlyWhenKnown() {
        val timestamp = Instant.ofEpochSecond(1_000)
        val absolute = { instant: Instant -> "T${instant.epochSecond}" }
        val altitude = { meters: Double -> "${meters.toInt()} m" }
        assertEquals("T1000 · 42 m", LocationReportCalloutContent(timestamp, 42.0).detail(absolute, altitude))
        assertEquals("T1000", LocationReportCalloutContent(timestamp, null).detail(absolute, altitude))
        assertEquals(
            "1000->2000",
            LocationReportCalloutContent(timestamp, null).headline(Instant.ofEpochSecond(2_000)) { at, now ->
                "${at.epochSecond}->${now.epochSecond}"
            },
        )
    }
}
