// AndroidOnly: WP-212 Native assembly/formatting cases for LogExportService; the source has no LogExportService suite.
package com.meshcoreone.android.core.services.diagnostics

import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import com.meshcoreone.android.core.model.DebugLogEntryDTO
import com.meshcoreone.android.core.model.DebugLogLevel
import com.meshcoreone.android.core.model.DeviceDTO
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.SnapshotList
import com.meshcoreone.android.core.model.snapshot
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.BatteryInfo
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class LogExportServiceTest {
    private val now = Instant.parse("2026-03-04T05:06:07.890Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val environment = LogExportEnvironment("1.2.3", "45", "Pixel 9", "Android", "16")
    private val deviceId = UUID.fromString("0a1b2c3d-4e5f-6789-abcd-ef0123456789")

    private fun device(isConnected: Boolean = true, frequency: UInt = 915_000u) = DeviceDTO(
        id = deviceId, radioId = RadioId(UUID.randomUUID()), publicKey = Bytes(ByteArray(32)), nodeName = "Base",
        firmwareVersion = 9u, firmwareVersionString = "v1.9.0", manufacturerName = "Heltec", buildDate = "2025-01-01",
        maxContacts = 350u, maxChannels = 40u, frequency = frequency, bandwidth = 250_000u, spreadingFactor = 11u,
        codingRate = 5u, txPower = 22, maxTxPower = 30, manualAddContacts = isConnected, multiAcks = 1u,
        lastConnected = Instant.parse("2026-03-01T10:20:30.999Z"),
    )

    private class ExportState(
        override val connectedDevice: DeviceDTO?,
        override val connectionState: DeviceConnectionState = DeviceConnectionState.READY,
        override val connectionIntentSummary: String = "wantsConnection",
        override val lastDisconnectDiagnostic: String? = "peer reset",
        override val deviceBattery: BatteryInfo? = null,
        private val bleSummary: String = "BLE: idle",
    ) : LogExportAppState {
        override suspend fun currentBLEDiagnosticsSummary(): String = bleSummary
    }

    private class ExportStore(
        var active: DeviceDTO? = null,
        var entries: List<DebugLogEntryDTO> = emptyList(),
        var deviceFailure: Exception? = null,
        var logFailure: Exception? = null,
        val events: MutableList<String> = mutableListOf(),
    ) : LogExportStore {
        var since: Instant? = null
        var limit: Long? = null
        override suspend fun fetchActiveDevice(): DeviceDTO? {
            events += "fetchActiveDevice"
            deviceFailure?.let { throw it }
            return active
        }
        override suspend fun fetchDebugLogEntries(since: Instant, limit: Long): SnapshotList<DebugLogEntryDTO> {
            events += "fetchDebugLogEntries"
            this.since = since
            this.limit = limit
            logFailure?.let { throw it }
            return entries.snapshot()
        }
    }

    private fun service(
        store: ExportStore,
        locale: Locale = Locale.US,
        diagnostics: LogExportDiagnostics = LogExportDiagnostics.NONE,
        flush: DebugLogFlushing? = DebugLogFlushing { store.events += "flush" },
    ) = LogExportService(environment, store, flush, clock, ZoneOffset.UTC, locale, diagnostics)

    private fun logEntry(level: DebugLogLevel, category: String, message: String, at: Instant) =
        DebugLogEntryDTO.create(level, "com.mc1", category, message, timestamp = at)

    private fun case(name: String, body: suspend () -> Unit): DynamicTest =
        DynamicTest.dynamicTest("WP-212::$name") { runBlocking { body() } }

    @TestFactory
    fun cases(): List<DynamicTest> = listOf(
        case("connected export assembles header, connection, device, battery and logs in source order") {
            val store = ExportStore(entries = listOf(
                logEntry(DebugLogLevel.INFO, "BLE", "connected", Instant.parse("2026-03-04T01:02:03.004Z")),
                logEntry(DebugLogLevel.ERROR, "Sync", "failed: x", Instant.parse("2026-03-04T01:02:04.5Z")),
            ))
            val text = service(store).generateExport(ExportState(device(), deviceBattery = BatteryInfo(3_912)))
            val expected = listOf(
                "=== MeshCore One Debug Export ===\n" +
                    "Exported: 2026-03-04T05:06:07Z\n" +
                    "App Version: 1.2.3 (45)\n" +
                    "Device: Pixel 9, Android 16",
                "=== Connection ===\n" +
                    "State: ready\n" +
                    "Intent: wantsConnection\n" +
                    "Last Disconnect Diagnostic: peer reset\n" +
                    "BLE: idle\n" +
                    "Device: Base (0A1B2C3D...)\n" +
                    "Last Connected: 2026-03-01T10:20:30Z",
                "=== Device Info ===\n" +
                    "Name: Base\n" +
                    "Firmware: v1.9.0 (v9)\n" +
                    "Manufacturer: Heltec\n" +
                    "Build Date: 2025-01-01\n" +
                    "Radio: 915.000 MHz, BW 250000 kHz, SF11, CR5\n" +
                    "TX Power: 22 dBm (max 30)\n" +
                    "Max Nodes: 350\n" +
                    "Max Channels: 40\n" +
                    "Manual Add Nodes: true\n" +
                    "Multi-ACKs: 1",
                "=== Battery ===\n" +
                    "Level: 76%\n" +
                    "Voltage: 3.91 V\n" +
                    "Raw: 3912 mV",
                "=== Logs (Last 7 Days) ===\n" +
                    "2026-03-04 01:02:03.004 [INFO] BLE: connected\n" +
                    "2026-03-04 01:02:04.500 [ERROR] Sync: failed: x\n" +
                    "\n" +
                    "Total entries: 2",
            ).joinToString("\n\n")
            assertEquals(expected, text)
            assertEquals(listOf("flush", "fetchDebugLogEntries"), store.events, "flush precedes the log fetch; no active-device read")
            assertEquals(now.minus(Duration.ofDays(7)), store.since)
            assertEquals(50_000L, store.limit)
        },
        case("disconnected export falls back to the active device and omits the battery section") {
            val store = ExportStore(active = device(isConnected = false))
            val text = service(store).generateExport(ExportState(
                connectedDevice = null, connectionState = DeviceConnectionState.DISCONNECTED,
                connectionIntentSummary = "none", lastDisconnectDiagnostic = null,
            ))
            assertTrue("=== Device Info (Last Connected) ===\nName: Base" in text)
            assertTrue("State: disconnected\nIntent: none\nLast Disconnect Diagnostic: " +
                LogExportService.UNAVAILABLE_DISCONNECT_DIAGNOSTIC in text)
            assertTrue("Manual Add Nodes: false" in text)
            assertTrue("=== Battery ===" !in text)
            assertTrue(text.endsWith("=== Logs (Last 7 Days) ===\n(No logs found)"))
            assertEquals(listOf("flush", "fetchActiveDevice", "fetchDebugLogEntries"), store.events)
        },
        case("store failures degrade to no device section and an inline log fetch failure") {
            val errors = mutableListOf<String>()
            val store = ExportStore(deviceFailure = IllegalStateException("no db"), logFailure = IOException("locked"))
            val text = service(store, diagnostics = { errors += it }, flush = null).generateExport(ExportState(null))
            assertTrue("=== Device Info" !in text)
            assertTrue("Device: Base" !in text)
            assertTrue(text.endsWith("=== Logs (Last 7 Days) ===\n(Failed to fetch logs: locked)"))
            assertEquals(listOf("Failed to fetch active device for export: no db", "Debug log fetch failed: locked"), errors)
        },
        case("each connection state exports its source label") {
            val labels = DeviceConnectionState.entries.map { state ->
                service(ExportStore()).generateExport(ExportState(null, connectionState = state))
                    .lines().first { it.startsWith("State: ") }
            }
            assertEquals(
                listOf("State: disconnected", "State: connecting", "State: connected", "State: syncing", "State: ready"),
                labels,
            )
        },
        case("numbers use locale grouping and fixed fraction digits; battery percentage clamps") {
            val germanStore = ExportStore()
            val german = service(germanStore, locale = Locale.GERMANY).generateExport(
                ExportState(device(frequency = 2_400_000u), deviceBattery = BatteryInfo(4_500)),
            )
            assertTrue("Radio: 2.400,000 MHz" in german)
            assertTrue("Level: 100%\nVoltage: 4,50 V\nRaw: 4500 mV" in german)
            val us = service(ExportStore()).generateExport(ExportState(device(frequency = 2_400_000u), deviceBattery = BatteryInfo(2_000)))
            assertTrue("Radio: 2,400.000 MHz" in us)
            assertTrue("Level: 0%\nVoltage: 2.00 V" in us)
        },
        case("createExportFile writes UTF-8 to the sink and names it with the local timestamp") {
            val sink = ByteArrayOutputStream()
            val exporter = LogExportService(environment, ExportStore(), null, clock, ZoneId.of("America/Chicago"), Locale.US)
            val name = exporter.createExportFile(ExportState(null, bleSummary = "BLE: café")) { fileName ->
                assertEquals("MeshCore-One-Debug-2026-03-03-230607.txt", fileName)
                sink
            }
            assertEquals("MeshCore-One-Debug-2026-03-03-230607.txt", name)
            val written = sink.toString(Charsets.UTF_8)
            assertTrue(written.startsWith("=== MeshCore One Debug Export ===\nExported: 2026-03-04T05:06:07Z"))
            assertTrue("BLE: café" in written)
        },
        case("createExportFile returns null and logs when the sink fails") {
            val errors = mutableListOf<String>()
            val exporter = service(ExportStore(), diagnostics = { errors += it })
            val failing = object : OutputStream() {
                override fun write(b: Int) = throw IOException("disk full")
            }
            assertNull(exporter.createExportFile(ExportState(null)) { failing })
            assertEquals(listOf("Failed to write export file: disk full"), errors)
        },
        case("cancellation from the store propagates instead of being logged") {
            val errors = mutableListOf<String>()
            val store = ExportStore(deviceFailure = CancellationException("stop"))
            val failure = try {
                service(store, diagnostics = { errors += it }).generateExport(ExportState(null))
                null
            } catch (cancelled: CancellationException) {
                cancelled
            }
            assertTrue(failure is CancellationException)
            assertTrue(errors.isEmpty())
        },
        case("internet date-time drops fractional seconds and always uses UTC Z") {
            assertEquals("1970-01-01T00:00:00Z", LogExportService.internetDateTime(Instant.ofEpochMilli(999)))
            assertEquals("2026-03-04T05:06:07Z", LogExportService.internetDateTime(now))
        },
    )
}
