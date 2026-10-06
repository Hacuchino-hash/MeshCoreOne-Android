// AndroidOnly: WP-212 The RX log/export seams route through PersistentLogger and the buffer satisfies DebugLogFlushing.
package com.meshcoreone.android.core.services.diagnostics

import com.meshcoreone.android.core.model.DebugLogEntryDTO
import com.meshcoreone.android.core.model.DebugLogLevel
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsWiringTest {
    private class RecordingSink : DebugLogPlatformSink {
        val lines = mutableListOf<Triple<DebugLogLevel, String, String>>()
        override fun write(level: DebugLogLevel, subsystem: String, category: String, message: String) {
            lines += Triple(level, category, message)
        }
    }

    @TestFactory
    fun wiringCases() = listOf(
        DynamicTest.dynamicTest("WP-212::RX log diagnostics persist info and above per category and keep debug log-only") {
            val sink = RecordingSink()
            val recorded = mutableListOf<DebugLogEntryDTO>()
            val diagnostics = persistentRxLogDiagnostics(loggerFor = { category ->
                PersistentLogger(DIAGNOSTICS_SUBSYSTEM, category, platformSink = sink, recorder = { recorded += it })
            })
            diagnostics.log(DebugLogLevel.DEBUG, "RxLogService", "trace")
            diagnostics.log(DebugLogLevel.WARNING, "RxLogService", "decrypt failed")
            diagnostics.log(DebugLogLevel.ERROR, "RxLogRegion", "reprocess failed")
            assertEquals(3, sink.lines.size)
            assertEquals(listOf("decrypt failed", "reprocess failed"), recorded.map { it.message })
            assertEquals(listOf("RxLogService", "RxLogRegion"), recorded.map { it.category })
            assertTrue(recorded.all { it.subsystem == DIAGNOSTICS_SUBSYSTEM })
        },
        DynamicTest.dynamicTest("WP-212::the debug log buffer is the export flush seam") {
            assertTrue(DebugLogFlushing::class.java.isAssignableFrom(DebugLogBuffer::class.java))
        },
    )
}
