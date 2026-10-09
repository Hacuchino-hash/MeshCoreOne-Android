// AndroidOnly: WP-318 Log export formatting and flow (the Swift LogExportService has no original test; behavior pinned from its source).
package com.meshcoreone.android.feature.settings.app.diagnostics

import com.meshcoreone.android.feature.settings.app.support.scenario
import com.meshcoreone.android.feature.settings.app.support.settle
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test

class LogExportTest {
    private val exportedAt = Instant.parse("2026-04-19T14:30:05.900Z")
    private val connection = ExportConnection("ready", "autoReconnect", null, "BLE: ok")

    private fun device(connected: Boolean = true) = ExportDevice(
        "Node A", "1a2b3c4d", Instant.parse("2026-04-19T14:00:00Z"), 9, "v1.9.0", "Heltec", "01 Jan 2026",
        910_525, 250, 11, 5, 20, 22, 350, 8, false, 2, connected,
    )

    private fun makeSnapshot(
        device: ExportDevice? = device(),
        battery: ExportBattery? = ExportBattery(87, 4.1234, 4123),
        logs: ExportLogs = ExportLogs.Entries(listOf(ExportLogEntry(Instant.parse("2026-04-19T14:29:00.123Z"), "info", "BLE", "connected"))),
        connection: ExportConnection = this.connection,
    ) = LogExportSnapshot(exportedAt, "1.5.0", "42", "Pixel 8", "15", connection, device, battery, logs)

    @Test
    fun `full export has the five sections in order`() {
        val text = LogExportFormatter.format(makeSnapshot(), ZoneOffset.UTC)
        val headers = Regex("^=== .* ===$", RegexOption.MULTILINE).findAll(text).map { it.value }.toList()
        assertEquals(
            listOf("=== MeshCore One Debug Export ===", "=== Connection ===", "=== Device Info ===", "=== Battery ===", "=== Logs (Last 7 Days) ==="),
            headers,
        )
        assertTrue(text.contains("Exported: 2026-04-19T14:30:05Z"))
        assertTrue(text.contains("App Version: 1.5.0 (42)"))
        assertTrue(text.contains("Device: Pixel 8, Android 15"))
        assertTrue(text.contains("Device: Node A (1a2b3c4d...)"))
        assertTrue(text.contains("Radio: 910.525 MHz, BW 250 kHz, SF11, CR5"))
        assertTrue(text.contains("TX Power: 20 dBm (max 22)"))
        assertTrue(text.contains("Manual Add Nodes: false"))
        assertTrue(text.contains("Voltage: 4.12 V"))
        assertTrue(text.contains("Raw: 4123 mV"))
        assertTrue(text.contains("2026-04-19 14:29:00.123 [info] BLE: connected"))
        assertTrue(text.endsWith("Total entries: 1"))
    }

    @Test
    fun `last-connected device, missing disconnect diagnostic and absent sections`() {
        val text = LogExportFormatter.format(makeSnapshot(device(false), battery = null, logs = ExportLogs.Entries(emptyList())), ZoneOffset.UTC)
        assertTrue(text.contains("=== Device Info (Last Connected) ==="))
        assertTrue(text.contains("Last Disconnect Diagnostic: ${LogExportFormatter.NO_DISCONNECT_CALLBACK}"))
        assertFalse(text.contains("=== Battery ==="))
        assertTrue(text.endsWith("(No logs found)"))
        val noDevice = LogExportFormatter.format(makeSnapshot(device = null), ZoneOffset.UTC)
        assertFalse(noDevice.contains("Device Info"))
        assertFalse(noDevice.contains("Last Connected:"))
    }

    @Test
    fun `log fetch failure is reported inline`() {
        val text = LogExportFormatter.format(makeSnapshot(logs = ExportLogs.FetchFailed("db closed")), ZoneOffset.UTC)
        assertTrue(text.endsWith("(Failed to fetch logs: db closed)"))
    }

    @Test
    fun `file name uses the local time zone stamp`() {
        assertEquals("MeshCore-One-Debug-2026-04-19-093005.txt", LogExportFormatter.fileName(Instant.parse("2026-04-19T14:30:05Z"), ZoneOffset.ofHours(-5)))
    }

    private class Rig {
        var snapshot: suspend () -> LogExportSnapshot = { throw AssertionError("snapshot not set") }
        var clear: suspend () -> Unit = {}
        var save: suspend (String, String) -> LogSaveOutcome = { name, _ -> LogSaveOutcome.Saved(name) }
        val saved = mutableListOf<Pair<String, String>>()
        val reports = mutableListOf<String>()
        val dependencies = LogExportDependencies(
            object : LogExportDataSource {
                override suspend fun snapshot() = snapshot.invoke()
                override suspend fun clearLogs() = clear.invoke()
            },
            object : LogDocumentPort {
                override suspend fun saveText(suggestedName: String, content: String): LogSaveOutcome {
                    saved += suggestedName to content
                    return save.invoke(suggestedName, content)
                }
            },
            object : LogExportStrings {
                override fun exportFailed() = "Failed to create export file"
                override fun genericFailure(failure: Throwable) = "generic:${failure.message}"
            },
            LogExportDiagnostics { message, _ -> reports += message },
        )
    }

    private fun holder(rig: Rig, scope: kotlinx.coroutines.CoroutineScope) =
        LogExportStateHolder(rig.dependencies, scope, Clock.fixed(Instant.parse("2026-04-19T14:30:05Z"), ZoneOffset.UTC), ZoneOffset.UTC)

    @Test
    fun `export saves the formatted text under the timestamped name and clears the spinner`() = scenario {
        val rig = Rig().apply { snapshot = { makeSnapshot() } }
        val holder = holder(rig, scope)
        holder.exportLogs()
        settle()
        assertEquals("MeshCore-One-Debug-2026-04-19-143005.txt", rig.saved.single().first)
        assertTrue(rig.saved.single().second.startsWith("=== MeshCore One Debug Export ==="))
        assertEquals("MeshCore-One-Debug-2026-04-19-143005.txt", holder.state.value.savedFileName)
        assertFalse(holder.state.value.isExporting)
        assertNull(holder.state.value.errorMessage)
    }

    @Test
    fun `cancelled picker leaves no error and no saved name`() = scenario {
        val rig = Rig().apply { snapshot = { makeSnapshot() }; save = { _, _ -> LogSaveOutcome.Cancelled } }
        val holder = holder(rig, scope)
        holder.exportLogs()
        settle()
        assertNull(holder.state.value.savedFileName)
        assertNull(holder.state.value.errorMessage)
        assertFalse(holder.state.value.isExporting)
    }

    @Test
    fun `low space or snapshot failure shows the export-failed message`() = scenario {
        val lowSpace = Rig().apply { snapshot = { makeSnapshot() }; save = { _, _ -> throw IOException("No space left on device") } }
        val a = holder(lowSpace, scope)
        a.exportLogs()
        settle()
        assertEquals("Failed to create export file", a.state.value.errorMessage)
        assertFalse(a.state.value.isExporting)
        val broken = Rig().apply { snapshot = { throw IllegalStateException("flush failed") } }
        val b = holder(broken, scope)
        b.exportLogs()
        settle()
        assertEquals("Failed to create export file", b.state.value.errorMessage)
        assertTrue(broken.saved.isEmpty())
    }

    @Test
    fun `a second export while one runs is ignored`() = scenario {
        val gate = CompletableDeferred<LogExportSnapshot>()
        val rig = Rig().apply { snapshot = { gate.await() } }
        val holder = holder(rig, scope)
        holder.exportLogs()
        assertNull(holder.exportLogs())
        assertTrue(holder.state.value.isExporting)
        gate.complete(makeSnapshot())
        settle()
        assertEquals(1, rig.saved.size)
    }

    @Test
    fun `clear failure shows a generic message and success shows nothing`() = scenario {
        val ok = Rig()
        val a = holder(ok, scope)
        a.clearLogs()
        settle()
        assertNull(a.state.value.errorMessage)
        val failing = Rig().apply { clear = { throw IOException("locked") } }
        val b = holder(failing, scope)
        b.clearLogs()
        settle()
        assertEquals("generic:locked", b.state.value.errorMessage)
        b.dismissError()
        assertNull(b.state.value.errorMessage)
    }
}
