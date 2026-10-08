// AndroidOnly: WP-313 Native coverage of the status display formatters against the swiftc oracle (oracles/status_foundation.swift.txt).
package com.meshcoreone.android.feature.remotenodes.status

import com.meshcoreone.android.core.l10n.R
import com.meshcoreone.android.core.l10n.generated.AppRemoteNodesStrings
import com.meshcoreone.android.core.model.NodeStatusSnapshotDTO
import com.meshcoreone.android.core.model.OCVPreset
import com.meshcoreone.android.feature.remotenodes.common.RemoteNodesText
import com.meshcoreone.android.feature.remotenodes.support.EPOCH
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class NodeStatusDisplayTest {
    private val us = Locale.US

    private fun res(id: Int, vararg args: Any) = RemoteNodesText.Resource(id, args.toList())

    @Test
    fun `missing status renders the em dash everywhere`() {
        assertEquals(RemoteNodesText.Verbatim("—"), NodeStatusDisplay.uptime(null))
        assertNull(NodeStatusDisplay.airtime(null))
        assertEquals("—", NodeStatusDisplay.airtimePercent(null, us))
        assertEquals("—", NodeStatusDisplay.battery(null, OCVPreset.LI_ION.ocvArray, us))
        assertEquals("—", NodeStatusDisplay.lastRssi(null))
        assertEquals("—", NodeStatusDisplay.lastSnr(null, us))
        assertEquals("—", NodeStatusDisplay.noiseFloor(null))
        assertEquals("—", NodeStatusDisplay.packetsSent(null, us))
        assertEquals("—", NodeStatusDisplay.duplicates(null, us))
    }

    @Test
    fun `durations pick the minutes, hours, one-day and days forms`() {
        assertEquals(res(R.string.l10n_app_remotenodes_remotenodes_status_uptimeminutes, 59), NodeStatusDisplay.formatDuration(3599u))
        assertEquals(res(R.string.l10n_app_remotenodes_remotenodes_status_uptimehours, 1, 0), NodeStatusDisplay.formatDuration(3600u))
        assertEquals(res(R.string.l10n_app_remotenodes_remotenodes_status_uptime1day, 2, 3), NodeStatusDisplay.formatDuration(86_400u + 7200u + 180u))
        assertEquals(res(R.string.l10n_app_remotenodes_remotenodes_status_uptimedays, 2, 0, 0), NodeStatusDisplay.formatDuration(172_800u))
        assertEquals(res(R.string.l10n_app_remotenodes_remotenodes_status_uptimeminutes, 0), NodeStatusDisplay.formatDuration(0u))
        assertEquals(
            res(R.string.l10n_app_remotenodes_remotenodes_status_uptimedays, 49710, 6, 28),
            NodeStatusDisplay.formatDuration(UInt.MAX_VALUE),
        )
    }

    @Test
    fun `status rows format like Foundation`() {
        val status = statusResponse()
        assertEquals(res(R.string.l10n_app_remotenodes_remotenodes_status_uptimehours, 1, 0), NodeStatusDisplay.uptime(status))
        val airtime = NodeStatusDisplay.airtime(status)
        assertEquals(res(R.string.l10n_app_remotenodes_remotenodes_status_uptimeminutes, 1), airtime?.tx)
        assertEquals("TX a / RX a", airtime?.compose { "a" })
        assertEquals("TX 2.8% / RX 2.8%", NodeStatusDisplay.airtimePercent(status, us))
        assertEquals("—", NodeStatusDisplay.airtimePercent(statusResponse(uptime = 0u), us))
        assertEquals("3.850V (66%)", NodeStatusDisplay.battery(status, OCVPreset.LI_ION.ocvArray, us))
        assertEquals("3.850V (70%)", NodeStatusDisplay.battery(status, listOf(4200L), us))
        assertEquals("65.535V (100%)", NodeStatusDisplay.battery(statusResponse(battery = 70_000), OCVPreset.LI_ION.ocvArray, us))
        assertEquals("-87 dBm", NodeStatusDisplay.lastRssi(status))
        assertEquals("8.5 dB", NodeStatusDisplay.lastSnr(status, us))
        assertEquals("-120 dBm", NodeStatusDisplay.noiseFloor(status))
        assertEquals("1,000", NodeStatusDisplay.packetsReceived(status, us))
        assertEquals("500", NodeStatusDisplay.packetsSent(status, us))
        assertEquals("0", NodeStatusDisplay.sentDirect(status, us))
        assertEquals("12", NodeStatusDisplay.duplicates(status.copy(directDuplicates = 5, floodDuplicates = 7), us))
        assertEquals("1.234.567", NodeStatusDisplay.count(1_234_567u, Locale.GERMANY))
    }

    @Test
    fun `previous snapshot timestamp picks minutes, hours or the month and day`() {
        fun text(previous: Instant, now: Instant, locale: Locale = us) = NodeStatusDisplay.previousSnapshotTimestamp(
            NodeStatusSnapshotDTO(timestamp = previous, nodePublicKey = TEST_PUBLIC_KEY), now, ZoneOffset.UTC, locale,
        )
        val now = EPOCH.plus(Duration.ofDays(9))
        assertNull(NodeStatusDisplay.previousSnapshotTimestamp(null, now, ZoneOffset.UTC, us))
        assertEquals(res(R.string.l10n_app_remotenodes_remotenodes_history_vsminutesago, 30), text(now.minusSeconds(1830), now))
        assertEquals(res(R.string.l10n_app_remotenodes_remotenodes_history_vsminutesago, 59), text(now.minusSeconds(3599), now))
        assertEquals(res(R.string.l10n_app_remotenodes_remotenodes_history_vshoursago, 1), text(now.minusSeconds(3600), now))
        assertEquals(res(R.string.l10n_app_remotenodes_remotenodes_history_vshoursago, 23), text(now.minusSeconds(86_399), now))
        // A snapshot from the future (clock skew) truncates toward zero, as Swift `Int(-1.5)` does.
        assertEquals(res(R.string.l10n_app_remotenodes_remotenodes_history_vsminutesago, -1), text(now.plusSeconds(90), now))
        val jan5 = EPOCH.plus(Duration.ofDays(4)).plusSeconds(3600)
        assertEquals(res(R.string.l10n_app_remotenodes_remotenodes_history_vsdate, "Jan 5"), text(jan5, now))
        assertEquals(res(R.string.l10n_app_remotenodes_remotenodes_history_vsdate, "5. Jan."), text(jan5, now, Locale.GERMANY))
    }

    @Test
    fun `month and day match Apple's MMMd output except es`() {
        val jan5 = EPOCH.plus(Duration.ofDays(4)).plusSeconds(3600)
        val apple = mapOf(
            "en-US" to "Jan 5", "en-GB" to "5 Jan", "de-DE" to "5. Jan.", "fr-FR" to "5 janv.", "it-IT" to "5 gen",
            "nl-NL" to "5 jan", "pl-PL" to "5 sty", "pt-BR" to "5 de jan.", "ru-RU" to "5 янв.", "uk-UA" to "5 січ.",
            "ko-KR" to "1월 5일", "zh-Hans-CN" to "1月5日", "ja-JP" to "1月5日",
        )
        apple.forEach { (tag, expected) ->
            assertEquals(expected, MonthDayFormat.format(jan5, ZoneOffset.UTC, Locale.forLanguageTag(tag)), tag)
        }
        // Documented deviation: Apple renders "5 ene"; CLDR's long pattern keeps the "de" literal.
        assertEquals("5 de ene", MonthDayFormat.format(jan5, ZoneOffset.UTC, Locale.forLanguageTag("es-ES")))
    }

    @Test
    fun `state deltas subtract the previous status snapshot`() {
        val previous = NodeStatusSnapshotDTO(
            nodePublicKey = TEST_PUBLIC_KEY, batteryMillivolts = 3865u, lastSNR = 9.0, lastRSSI = -90, noiseFloor = -117,
        )
        val state = NodeStatusState(status = statusResponse(), previousStatusSnapshot = previous)
        assertEquals(-15, state.batteryDeltaMV)
        assertEquals(-0.5, state.snrDelta)
        assertEquals(3L, state.rssiDelta)
        assertEquals(-3L, state.noiseFloorDelta)
        val empty = NodeStatusState(status = statusResponse())
        assertNull(empty.batteryDeltaMV)
        assertNull(empty.snrDelta)
        assertNull(empty.rssiDelta)
        assertNull(empty.noiseFloorDelta)
        assertNull(NodeStatusState(previousStatusSnapshot = previous).batteryDeltaMV)
    }

    @Test
    fun `status delta classifies, labels and describes the change`() {
        val battery = StatusDelta.battery(-15)
        assertEquals(StatusDelta(-0.015, true, " V", 3), battery)
        assertEquals(false, battery?.pointsUp)
        assertEquals(StatusDeltaTone.DEGRADED, battery?.tone)
        assertEquals("0.015 V", battery?.label(us))
        assertEquals(
            res(
                R.string.l10n_app_remotenodes_remotenodes_history_a11y_deltadescription,
                RemoteNodesText.Resource(AppRemoteNodesStrings.remoteNodesHistoryA11yDegraded),
                RemoteNodesText.Resource(AppRemoteNodesStrings.remoteNodesHistoryA11yDecreased),
                "0.015", " V",
            ),
            battery?.accessibilityDescription(us),
        )
        val noise = StatusDelta.noiseFloor(-3)
        assertEquals(StatusDeltaTone.IMPROVED, noise?.tone)
        assertEquals("3 dBm", noise?.label(us))
        val rssi = StatusDelta.rssi(4)
        assertEquals(true, rssi?.pointsUp)
        assertEquals(StatusDeltaTone.IMPROVED, rssi?.tone)
        assertEquals(StatusDeltaTone.NEUTRAL, StatusDelta.snr(0.005)?.tone)
        assertEquals(false, StatusDelta.snr(0.0)?.pointsUp)
        assertEquals("0.0 dB", StatusDelta.snr(-0.05)?.label(us))
        assertEquals("0.2 dB", StatusDelta.snr(0.15)?.label(us))
        assertNull(StatusDelta.battery(null))
    }

    @Test
    fun `OCV percentage matches the Swift oracle`() {
        val liIon = OCVPreset.LI_ION.ocvArray
        val expected = mapOf(
            0L to (0 to 0), 2999L to (0 to 0), 3100L to (0 to 8), 3101L to (0 to 8), 3150L to (3 to 12),
            3200L to (5 to 16), 3205L to (5 to 17), 3850L to (66 to 70), 3845L to (65 to 70), 4000L to (82 to 83),
            4189L to (100 to 99), 4190L to (100 to 99), 5000L to (100 to 100), 65535L to (100 to 100),
        )
        expected.forEach { (millivolts, pair) ->
            assertEquals(pair.first, OcvBatteryPercentage.percentage(millivolts, liIon), "ocv $millivolts")
            assertEquals(pair.second, OcvBatteryPercentage.percentage(millivolts, listOf(4200L)), "linear $millivolts")
        }
        val flat = listOf(4000L, 4000, 3900, 3800, 3700, 3600, 3500, 3400, 3300, 3200, 3100)
        assertEquals(85, OcvBatteryPercentage.percentage(3950, flat))
        assertEquals(0, OcvBatteryPercentage.linear(-100))
        assertEquals(0, OcvBatteryPercentage.linear(3001))
        assertEquals(49, OcvBatteryPercentage.linear(3599))
    }

    @Test
    fun `custom OCV strings parse like Swift split, whitespace trim and Int`() {
        val cases = mapOf(
            "4190,4050,3990,3890,3800,3720,3630,3530,3420,3300,3100" to OCVPreset.LI_ION.ocvArray.toList(),
            " 4190 , 4050,\t3990,3890 ,3800,3720,3630,3530,3420,3300,3100" to OCVPreset.LI_ION.ocvArray.toList(),
            "4190,,4050" to listOf(4190L, 4050L),
            "+4190,-5,0042" to listOf(4190L, -5L, 42L),
            "4190\n,4050\r,\n3990" to emptyList(),
            " 4190 ,　4050" to listOf(4190L, 4050L),
            "\u000B4190,\u000C4050,\u00853990, 3890" to emptyList(),
            "٤١٩٠,4190" to listOf(4190L),
            "99999999999999999999,4190" to listOf(4190L),
            "4190.0,4190" to listOf(4190L),
            "" to emptyList(),
            "-9223372036854775808,9223372036854775807,9223372036854775808" to listOf(Long.MIN_VALUE, Long.MAX_VALUE),
            "+,-,+-5" to emptyList(),
        )
        cases.forEach { (input, expected) -> assertEquals(expected, OcvCustomCurve.parse(input), input) }
        assertEquals("1,2,3", OcvCustomCurve.format(listOf(1L, 2L, 3L)))
    }
}
