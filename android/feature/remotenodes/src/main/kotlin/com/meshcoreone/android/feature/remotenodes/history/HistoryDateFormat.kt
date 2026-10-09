// AndroidOnly: WP-313 java.time approximation of Apple's .dateTime.month(.abbreviated).day()[.hour().minute()] format styles for history charts and location rows.
package com.meshcoreone.android.feature.remotenodes.history

import java.time.Instant
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Apple builds these strings from date-format skeletons ("MMMd", "MMMdjmm"), which java.time on
 * minSdk 31 cannot do. The approximation takes the locale's MEDIUM date (and SHORT time) pattern and
 * removes the year field with the separator that belongs to it, so en_US gives "Jul 13" / "Jul 13, 7:41 AM"
 * where iOS gives "Jul 13" / "Jul 13 at 7:41 AM" (oracle `history_location.swift.txt`, ABS lines).
 * Documented deviation: month length (numeric vs abbreviated) and the date/time glue follow the
 * locale's MEDIUM pattern rather than Apple's skeleton output.
 */
object HistoryDateFormat {
    private const val QUOTE = '\''
    private val YEAR_FIELDS = setOf('y', 'u', 'Y')

    /** Swift `.dateTime.month(.abbreviated).day()` (chart x-axis labels). */
    fun monthDay(date: Instant, locale: Locale, zone: ZoneId): String =
        formatter(FormatStyle.MEDIUM, null, locale).withZone(zone).format(date)

    /** Swift `.dateTime.month(.abbreviated).day().hour().minute()` (scrub readout, location rows). */
    fun monthDayTime(date: Instant, locale: Locale, zone: ZoneId): String =
        formatter(FormatStyle.MEDIUM, FormatStyle.SHORT, locale).withZone(zone).format(date)

    private fun formatter(dateStyle: FormatStyle, timeStyle: FormatStyle?, locale: Locale): DateTimeFormatter {
        val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(dateStyle, timeStyle, IsoChronology.INSTANCE, locale)
        return DateTimeFormatter.ofPattern(withoutYear(pattern), locale)
    }

    /** Removes the year field and the literal run that attaches it to the rest of the pattern. */
    internal fun withoutYear(pattern: String): String {
        val tokens = tokenize(pattern)
        val yearIndex = tokens.indexOfFirst { it.isField && it.text.first() in YEAR_FIELDS }
        if (yearIndex < 0) return pattern
        val before = tokens.getOrNull(yearIndex - 1)?.takeIf { !it.isField }
        val after = tokens.getOrNull(yearIndex + 1)?.takeIf { !it.isField }
        val separatorIndex = when {
            before == null || after?.hasLetter == true -> after?.let { yearIndex + 1 }
            else -> yearIndex - 1
        }
        val dropped = setOfNotNull(yearIndex, separatorIndex)
        return tokens.filterIndexed { index, _ -> index !in dropped }.joinToString("") { it.text }.trim()
    }

    private class Token(val isField: Boolean, val text: String) {
        /** A literal run carrying a word (a quoted 'г' or an unquoted 年) belongs to the field before it. */
        val hasLetter: Boolean get() = !isField && text.any { it.isLetter() }
    }

    /** Splits a java.time pattern into field runs and merged literal runs (quotes kept verbatim). */
    private fun tokenize(pattern: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val literal = StringBuilder()
        var index = 0
        while (index < pattern.length) {
            val character = pattern[index]
            when {
                character == QUOTE -> {
                    val close = pattern.indexOf(QUOTE, index + 1).let { if (it < 0) pattern.length - 1 else it }
                    literal.append(pattern, index, close + 1)
                    index = close + 1
                }
                character in 'a'..'z' || character in 'A'..'Z' -> {
                    if (literal.isNotEmpty()) tokens += Token(false, literal.toString()).also { literal.clear() }
                    var end = index + 1
                    while (end < pattern.length && pattern[end] == character) end++
                    tokens += Token(true, pattern.substring(index, end))
                    index = end
                }
                else -> {
                    literal.append(character)
                    index++
                }
            }
        }
        if (literal.isNotEmpty()) tokens += Token(false, literal.toString())
        return tokens
    }
}
