// AndroidOnly: WP-313 Native LocationPathMapBuilder tests (the Swift suite MC1Tests/Views/LocationPathMapBuilderTests.swift is WP-312-owned).
package com.meshcoreone.android.feature.remotenodes.map

import com.meshcoreone.android.core.model.Coordinate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class LocationPathMapBuilderTest {
    private fun track(count: Int, spacingMinutes: Double, decimate: Boolean = true): LocationPathMapBuilder.PlottedPath =
        LocationPathMapBuilder.build(
            (0 until count).map { locationSnapshot(it * spacingMinutes, 37.0 + it * 0.001, -122.0) },
            decimatePins = decimate,
        )

    @Test
    fun emptyInputYieldsNoPath() {
        val built = LocationPathMapBuilder.build(emptyList())
        assertTrue(built.points.isEmpty())
        assertTrue(built.lines.isEmpty())
        assertTrue(built.reports.isEmpty())
    }

    @Test
    fun aSingleFixIsALoneHeroPinWithItsReport() {
        val ids = SequentialIds()
        val snapshot = locationSnapshot(-60.0, 37.0, -122.0, altitude = 12.0)
        val built = LocationPathMapBuilder.build(listOf(snapshot), idFactory = ids::next)

        assertTrue(built.lines.isEmpty())
        assertEquals(
            listOf(MapPoint(UUID(0, 1), Coordinate(37.0, -122.0), PinStyle.LOCATION_FIX_LATEST, null, false, null, null)),
            built.points,
        )
        assertEquals(
            mapOf(UUID(0, 1) to LocationPathMapBuilder.LocationReport(snapshot.id, snapshot.timestamp, 12.0)),
            built.reports,
        )
    }

    @Test
    fun snapshotsWithoutAValidFixAreExcluded() {
        val built = LocationPathMapBuilder.build(
            listOf(
                locationSnapshot(-180.0, null, null),
                locationSnapshot(-120.0, 0.0, 0.0),
                locationSnapshot(-90.0, 95.0, -122.0),
                locationSnapshot(-60.0, 37.0, -122.0),
            ),
        )
        assertTrue(built.lines.isEmpty())
        assertEquals(1, built.points.size)
    }

    @Test
    fun twoFixesYieldATrailADotAndTheHero() {
        val built = LocationPathMapBuilder.build(
            listOf(locationSnapshot(-120.0, 37.0, -122.0), locationSnapshot(-60.0, 37.1, -122.1)),
        )
        assertEquals(1, built.lines.size)
        assertEquals(MapLine("location-trail-0", listOf(Coordinate(37.0, -122.0), Coordinate(37.1, -122.1)), MapLineStyle.LOCATION_TRAIL, 1.0), built.lines[0])
        assertEquals(listOf(PinStyle.LOCATION_FIX, PinStyle.LOCATION_FIX_LATEST), built.points.map { it.pinStyle })
        assertTrue(built.points.none { it.isClusterable })
        assertEquals(listOf(0, null), built.points.map { it.hopIndex })
    }

    @Test
    fun gapsOverAnHourSplitTheTrailAndLoneFixesDrawNoSegment() {
        val split = LocationPathMapBuilder.build(
            listOf(
                locationSnapshot(-200.0, 37.0, -122.0), locationSnapshot(-190.0, 37.1, -122.0),
                locationSnapshot(-50.0, 38.0, -123.0), locationSnapshot(-40.0, 38.1, -123.0),
            ),
        )
        assertEquals(listOf("location-trail-0", "location-trail-1"), split.lines.map { it.id })
        assertTrue(split.lines.all { it.coordinates.size == 2 })

        // Exactly 60 minutes apart stays connected (strictly greater splits).
        val boundary = LocationPathMapBuilder.build(
            listOf(locationSnapshot(-120.0, 37.0, -122.0), locationSnapshot(-60.0, 37.1, -122.0)),
        )
        assertEquals(1, boundary.lines.size)

        val lone = LocationPathMapBuilder.build(
            listOf(
                locationSnapshot(-200.0, 37.0, -122.0), locationSnapshot(-190.0, 37.1, -122.0),
                locationSnapshot(-10.0, 38.0, -123.0),
            ),
        )
        assertEquals(1, lone.lines.size)
        assertEquals(2, lone.lines[0].coordinates.size)
        assertEquals(3, lone.points.size)
    }

    @Test
    fun everyPinMapsToItsSourceReport() {
        val snapshots = listOf(
            locationSnapshot(-30.0, 37.0, -122.0, altitude = 10.0),
            locationSnapshot(-15.0, 37.1, -122.1),
            locationSnapshot(-1.0, 37.2, -122.2, altitude = 42.0),
        )
        val built = LocationPathMapBuilder.build(snapshots)
        assertEquals(built.points.map { it.id }, built.reports.keys.toList())
        assertEquals(snapshots.map { it.id }.toSet(), built.reports.values.map { it.id }.toSet())
        val hero = built.reports.getValue(built.points.last().id)
        assertEquals(42.0, hero.altitude)
        assertEquals(snapshots.last().id, hero.id)
    }

    @Test
    fun decimationKeepsTheTrailWholeAndCapsPinsWithACeilingStride() {
        // 198 interior fixes over a 58-pin budget: stride ceil(198 / 58) = 4 gives 50 + first + hero.
        val built = track(200, 10.0)
        assertEquals(52, built.points.size)
        assertEquals(200, built.lines.single().coordinates.size)

        assertEquals(200, track(200, 10.0, decimate = false).points.size)

        for (count in 2..400) {
            val pins = track(count, 10.0).points.size
            assertTrue(pins <= LocationPathMapBuilder.MAX_PINS, "$count fixes produced $pins pins")
            if (count <= LocationPathMapBuilder.MAX_PINS) assertEquals(count, pins, "$count fixes")
        }
    }

    @Test
    fun recencyBucketsSpanTheRampAndRoundHalvesAwayFromZero() {
        // Swift oracle map_badge_ids: lastDot 7 -> [0,1,1,2,2,3,3,4], lastDot 8 -> [0,1,1,2,2,3,3,4,4]
        // (2.5 rounds to 3, which half-even rounding would get wrong).
        val nine = track(9, 10.0, decimate = false).points
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4, null), nine.map { it.hopIndex })
        val ten = track(10, 10.0, decimate = false).points
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4, 4, null), ten.map { it.hopIndex })
        assertTrue(ten.dropLast(1).all { it.pinStyle == PinStyle.LOCATION_FIX })
        assertEquals(PinStyle.LOCATION_FIX_LATEST, ten.last().pinStyle)
        assertEquals(LocationPathMapBuilder.RECENCY_BUCKET_COUNT, nine.mapNotNull { it.hopIndex }.toSet().size)
    }

    @Test
    fun latestFixSkipsTrailingInvalidSnapshots() {
        val latest = LocationPathMapBuilder.latestFix(
            listOf(
                locationSnapshot(-180.0, 37.0, -122.0),
                locationSnapshot(-120.0, 38.0, -123.0),
                locationSnapshot(-60.0, null, null),
            ),
        )
        assertEquals(Coordinate(38.0, -123.0), latest)
        assertNull(LocationPathMapBuilder.latestFix(listOf(locationSnapshot(-60.0, 0.0, 0.0))))
    }

    @Test
    fun injectedIdsAreDeterministicInPinOrder() {
        val ids = SequentialIds()
        val built = LocationPathMapBuilder.build(
            (0 until 3).map { locationSnapshot(it * 10.0, 37.0 + it, -122.0) },
            idFactory = ids::next,
        )
        assertEquals(listOf(UUID(0, 1), UUID(0, 2), UUID(0, 3)), built.points.map { it.id })
    }
}
