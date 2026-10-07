// AndroidOnly: WP-313 Native tests for the feature-local map mirrors (SNRQuality, midpoint, bounding region, filter, stable ids, badge).
package com.meshcoreone.android.feature.remotenodes.map

import com.meshcoreone.android.core.model.Coordinate
import com.meshcoreone.android.core.protocol.model.ContactType
import com.meshcoreone.android.feature.remotenodes.common.GeoDistance
import com.meshcoreone.android.feature.remotenodes.telemetry.MeasurementSystem
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

class MapPrimitivesTest {
    private fun assertNear(expected: Double, actual: Double, tolerance: Double = 1e-9) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")

    @Test
    fun snrQualityBucketsUseStrictGreaterThresholds() {
        assertEquals(SNRQuality.EXCELLENT, SNRQuality.of(6.25))
        assertEquals(SNRQuality.GOOD, SNRQuality.of(6.0))
        assertEquals(SNRQuality.GOOD, SNRQuality.of(0.25))
        assertEquals(SNRQuality.FAIR, SNRQuality.of(0.0))
        assertEquals(SNRQuality.FAIR, SNRQuality.of(-5.75))
        assertEquals(SNRQuality.POOR, SNRQuality.of(-6.0))
        assertEquals(SNRQuality.POOR, SNRQuality.of(Double.NaN))
        assertEquals(SNRQuality.UNKNOWN, SNRQuality.of(null))
        assertEquals(listOf(1.0, 0.75, 0.5, 0.25, 0.0), SNRQuality.entries.map { it.barLevel })
        assertEquals(MapLineStyle.TRACE_GOOD, MapLineStyle.forSNR(6.25))
    }

    @Test
    fun midpointAveragesOrdinaryPairsAndWrapsAcrossTheAntimeridian() {
        val ordinary = SnrBadges.midpoint(Coordinate(37.0, -122.0), Coordinate(37.1, -122.1))
        assertNear(37.05, ordinary.latitude, 1e-12)
        assertNear(-122.05, ordinary.longitude, 1e-12)
        assertNear(180.0, SnrBadges.midpoint(Coordinate(0.0, -179.0), Coordinate(0.0, 179.0)).longitude)
        assertNear(-175.0, SnrBadges.midpoint(Coordinate(0.0, 175.0), Coordinate(0.0, -165.0)).longitude)
        assertNear(-175.0, SnrBadges.midpoint(Coordinate(10.0, -165.0), Coordinate(20.0, 175.0)).longitude)
        assertNear(15.0, SnrBadges.midpoint(Coordinate(10.0, -165.0), Coordinate(20.0, 175.0)).latitude)
    }

    @Test
    fun snrBadgeIsAnUnclusteredBadgePinCarryingDistanceAndSnr() {
        val from = Coordinate(37.0, -122.0)
        val to = Coordinate(37.1, -122.1)
        val id = UUID(0L, 7L)
        val badge = SnrBadges.snrBadge(id, from, to, -3.0)
        assertEquals(id, badge.id)
        assertEquals(PinStyle.BADGE, badge.pinStyle)
        assertNull(badge.label)
        assertFalse(badge.isClusterable)
        assertNull(badge.hopIndex)
        assertEquals(SnrBadge(GeoDistance.meters(from, to), -3.0), badge.badge)
        // CLLocation.distance (WGS-84) gives 14222.849 m for this pair (oracle map_badge_ids); the
        // haversine stand-in stays within 0.5 % and formats identically.
        val distance = GeoDistance.meters(from, to)
        assertTrue(abs(distance - 14_222.849) / 14_222.849 < 0.005, "haversine $distance")
    }

    @Test
    fun snrBadgeTextMatchesFoundationForTheOracleDistances() {
        assertEquals("8.8 mi · -3.0 dB", SnrBadge(14_222.849, -3.0).text(Locale.US, MeasurementSystem.US, "dB"))
        assertEquals("14 km · -3,0 dB", SnrBadge(14_222.849, -3.0).text(Locale.GERMANY, MeasurementSystem.METRIC, "dB"))
        assertEquals("15 mi · 2.2 dB", SnrBadge(23_909.857, 2.25).text(Locale.UK, MeasurementSystem.UK, "dB"))
        assertEquals("223 km · -0.0 dB", SnrBadge(222_638.98, -0.04).text(Locale.US, MeasurementSystem.METRIC, "dB"))
        assertEquals("1,650 ft · 7.8 dB", SnrBadge(500.0, 7.75).text(Locale.US, MeasurementSystem.US, "dB"))
    }

    @Test
    fun stableIdsMatchTheSwiftOracleAndBuildUppercaseLineIds() {
        val key = prefixBytes(EXACT_PREFIX)
        // swiftc oracle map_badge_ids: SHA256([role] + key) first 16 bytes via UUID(uuid:).
        assertEquals(
            "45D55D09-18FD-E8E9-3F7F-006D42C5D966",
            NeighborSNRMapBuilder.stableId(NeighborSNRMapBuilder.PinRole.CENTER, key).toString().uppercase(Locale.ROOT),
        )
        assertEquals(
            UUID.fromString("DEAEDBBE-3709-EB73-F557-90B3E57157C9"),
            NeighborSNRMapBuilder.stableId(NeighborSNRMapBuilder.PinRole.NEIGHBOR, key),
        )
        assertEquals(
            UUID.fromString("E710BDC7-3A34-8DDB-FC88-308164B276C5"),
            NeighborSNRMapBuilder.stableId(NeighborSNRMapBuilder.PinRole.BADGE, key),
        )
        assertEquals("neighbor-DEAEDBBE-3709-EB73-F557-90B3E57157C9", NeighborSNRMapBuilder.stableLineId(key))
    }

    @Test
    fun boundingRegionPadsFloorsAndClamps() {
        assertNull(emptyList<Coordinate>().boundingRegion())

        val single = listOf(Coordinate(37.0, -122.0)).boundingRegion()
        assertEquals(CoordinateRegion(Coordinate(37.0, -122.0), 0.01, 0.01), single)

        val pair = assertNotNull(listOf(Coordinate(37.0, -122.0), Coordinate(37.2, -121.9)).boundingRegion())
        assertNear(37.1, pair.center.latitude)
        assertNear(-121.95, pair.center.longitude)
        assertNear(0.2 * 1.5, pair.latitudeDelta, 1e-12)
        assertNear(0.1 * 1.5, pair.longitudeDelta, 1e-12)

        val padded = assertNotNull(listOf(Coordinate(37.0, -122.0), Coordinate(37.2, -121.9)).boundingRegion(1.3))
        assertNear(0.2 * 1.3, padded.latitudeDelta, 1e-12)

        // Near the pole the latitude span is clamped to keep center ± span / 2 within ±90.
        val polar = assertNotNull(listOf(Coordinate(89.0, 0.0), Coordinate(80.0, 10.0)).boundingRegion())
        assertNear(11.0, polar.latitudeDelta)
        // A world-wide spread never exceeds a 360° longitude span.
        val wide = assertNotNull(listOf(Coordinate(0.0, -179.0), Coordinate(1.0, 179.0)).boundingRegion())
        assertEquals(360.0, wide.longitudeDelta)
    }

    @Test
    fun regionBracketsAndBoundsUseHalfSpans() {
        val region = CoordinateRegion(Coordinate(10.0, 20.0), 2.0, 4.0)
        assertTrue(region.brackets(Coordinate(11.0, 22.0)))
        assertFalse(region.brackets(Coordinate(11.01, 20.0)))
        assertEquals(CoordinateBounds(Coordinate(9.0, 18.0), Coordinate(11.0, 22.0)), region.bounds())
        assertEquals(CoordinateRegion(Coordinate(1.0, 2.0), 0.05, 0.05), CoordinateRegion.around(Coordinate(1.0, 2.0), 0.05))
    }

    @Test
    fun filterHostsSeedAndCapabilitiesMirrorSwift() {
        assertEquals(MapFilterState(), MapFilterState.seed(MapFilterHost.MAIN_MAP))
        assertEquals(MapFilterState(), MapFilterState.seed(MapFilterHost.NEIGHBOR_SNR))
        assertEquals(MapFilterState(showDiscovered = true), MapFilterState.seed(MapFilterHost.TRACE_PATH))
        assertTrue(MapFilterHost.MAIN_MAP.includesTypes)
        assertFalse(MapFilterHost.NEIGHBOR_SNR.includesTypes)
        assertFalse(MapFilterHost.TRACE_PATH.includesTypes)
    }

    @Test
    fun effectiveShowDiscoveredFreezesUnderFavorites() {
        assertTrue(MapFilterState(showDiscovered = true).effectiveShowDiscovered)
        assertFalse(MapFilterState(favoritesOnly = true, showDiscovered = true).effectiveShowDiscovered)
        assertFalse(MapFilterState().effectiveShowDiscovered)
    }

    @Test
    fun sanitizedRestoresTypesOnlyForTypeCapableHosts() {
        val noTypes = MapFilterState(showChat = false, showRepeater = false, showRoom = false)
        assertEquals(MapFilterState(), noTypes.sanitized(MapFilterHost.MAIN_MAP))
        assertSame(noTypes, noTypes.sanitized(MapFilterHost.NEIGHBOR_SNR))
        val valid = MapFilterState(showChat = false)
        assertSame(valid, valid.sanitized(MapFilterHost.MAIN_MAP))
    }

    @Test
    fun settersApplySwiftFreezeRules() {
        val favorites = MapFilterState(favoritesOnly = true)
        assertEquals(favorites, favorites.withShowDiscovered(true))
        assertEquals(favorites, favorites.withShowChat(false, MapFilterHost.MAIN_MAP))
        assertEquals(MapFilterState(showDiscovered = true), MapFilterState().withShowDiscovered(true))
        assertEquals(MapFilterState(favoritesOnly = true), MapFilterState().withFavoritesOnly(true))

        // Type toggles are ignored on hosts without type controls.
        assertEquals(MapFilterState(), MapFilterState().withShowRoom(false, MapFilterHost.NEIGHBOR_SNR))
        // The last enabled type cannot be switched off.
        val onlyRoom = MapFilterState(showChat = false, showRepeater = false)
        assertEquals(onlyRoom, onlyRoom.withShowRoom(false, MapFilterHost.MAIN_MAP))
        assertEquals(MapFilterState(showChat = false), MapFilterState().withShowChat(false, MapFilterHost.MAIN_MAP))
        assertEquals(MapFilterState(showRepeater = false), MapFilterState().withShowRepeater(false, MapFilterHost.MAIN_MAP))
        assertEquals(MapFilterState(), onlyRoom.withShowChat(true, MapFilterHost.MAIN_MAP).withShowRepeater(true, MapFilterHost.MAIN_MAP))
    }

    @Test
    fun differsFromSeedOnlyCountsOfferedControlsAndTypeGateBypassesUnderFavorites() {
        assertFalse(MapFilterState().differsFromSeed(MapFilterHost.NEIGHBOR_SNR))
        assertTrue(MapFilterState(showDiscovered = true).differsFromSeed(MapFilterHost.NEIGHBOR_SNR))
        assertFalse(MapFilterState(showDiscovered = true).differsFromSeed(MapFilterHost.TRACE_PATH))
        assertFalse(MapFilterState(showChat = false).differsFromSeed(MapFilterHost.NEIGHBOR_SNR))
        assertTrue(MapFilterState(showChat = false).differsFromSeed(MapFilterHost.MAIN_MAP))

        val chatOff = MapFilterState(showChat = false)
        assertFalse(chatOff.allowsContactType(ContactType.CHAT))
        assertTrue(chatOff.allowsContactType(ContactType.REPEATER))
        assertTrue(chatOff.withFavoritesOnly(true).allowsContactType(ContactType.CHAT))
    }
}
