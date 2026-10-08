// AndroidOnly: WP-212 Binds the RX log and log-export logging seams to the ported PersistentLogger, as the source services log through it.
package com.meshcoreone.android.core.services.diagnostics

import com.meshcoreone.android.core.model.DebugLogLevel
import java.util.concurrent.ConcurrentHashMap

/** Source subsystem shared by the MC1Services loggers. */
const val DIAGNOSTICS_SUBSYSTEM = "com.mc1"

/**
 * [RxLogDiagnostics] that writes through one [PersistentLogger] per category, so RX log lines reach the
 * platform log and (INFO and above) the persisted debug log exactly as the source `PersistentLogger` calls do.
 */
fun persistentRxLogDiagnostics(
    subsystem: String = DIAGNOSTICS_SUBSYSTEM,
    loggerFor: (String) -> PersistentLogger = { category -> PersistentLogger(subsystem, category) },
): RxLogDiagnostics {
    val loggers = ConcurrentHashMap<String, PersistentLogger>()
    return RxLogDiagnostics { level, category, message ->
        val logger = loggers.computeIfAbsent(category, loggerFor)
        when (level) {
            DebugLogLevel.DEBUG -> logger.debug(message)
            DebugLogLevel.INFO -> logger.info(message)
            DebugLogLevel.NOTICE -> logger.notice(message)
            DebugLogLevel.WARNING -> logger.warning(message)
            DebugLogLevel.ERROR -> logger.error(message)
            DebugLogLevel.FAULT -> logger.fault(message)
        }
    }
}

/** [LogExportDiagnostics] that reports export failures through the source `LogExportService` category. */
fun persistentLogExportDiagnostics(
    logger: PersistentLogger = PersistentLogger(DIAGNOSTICS_SUBSYSTEM, "LogExportService"),
): LogExportDiagnostics = LogExportDiagnostics { message -> logger.error(message) }
