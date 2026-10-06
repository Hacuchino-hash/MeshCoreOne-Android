// AndroidOnly: WP-212 Replaces Apple os.Logger with an injectable platform sink backed by java.util.logging.
package com.meshcoreone.android.core.services.diagnostics

import com.meshcoreone.android.core.model.DebugLogLevel
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Non-persistent platform log output, the Android stand-in for Apple's `os.Logger`.
 *
 * `core:services` is a pure JVM module (no Android's `Log`), so the default sink is
 * [JavaUtilLoggingSink]. On Android the runtime installs a `java.util.logging` handler that
 * forwards to logcat, so records still reach the device log; on the JVM they reach the
 * configured JUL handlers. Tests inject a recording sink instead.
 */
fun interface DebugLogPlatformSink {
    fun write(level: DebugLogLevel, subsystem: String, category: String, message: String)
}

/**
 * Default [DebugLogPlatformSink]. Logger name is `"<subsystem>.<category>"`.
 *
 * Level mapping (os.Logger -> JUL): debug -> FINE (hidden at the default INFO threshold, like
 * os.Logger debug which is not retained by default), info/notice -> INFO, warning -> WARNING,
 * error/fault -> SEVERE. The message is set verbatim on a [LogRecord] with no parameters, so
 * braces in log text are never interpreted as `MessageFormat` placeholders.
 */
object JavaUtilLoggingSink : DebugLogPlatformSink {
    override fun write(level: DebugLogLevel, subsystem: String, category: String, message: String) {
        val logger = Logger.getLogger("$subsystem.$category")
        val julLevel = julLevel(level)
        if (!logger.isLoggable(julLevel)) return
        val record = LogRecord(julLevel, message).apply { loggerName = logger.name }
        logger.log(record)
    }

    internal fun julLevel(level: DebugLogLevel): Level = when (level) {
        DebugLogLevel.DEBUG -> Level.FINE
        DebugLogLevel.INFO, DebugLogLevel.NOTICE -> Level.INFO
        DebugLogLevel.WARNING -> Level.WARNING
        DebugLogLevel.ERROR, DebugLogLevel.FAULT -> Level.SEVERE
    }
}
