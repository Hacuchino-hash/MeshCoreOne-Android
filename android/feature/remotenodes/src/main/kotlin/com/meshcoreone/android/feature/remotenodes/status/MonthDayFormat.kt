// AndroidOnly: WP-313 java.time approximation of Foundation `.dateTime.month().day()`; Android API 31 has no skeleton formatter in java.time.
package com.meshcoreone.android.feature.remotenodes.status

import java.time.Instant
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Abbreviated month plus day ("Jan 5", "5. Jan.", "1月5日"). Built from the locale's LONG date pattern
 * with the year field (and the separators or literals tied to it) removed and the month abbreviated.
 * Matches Apple's `MMMd` skeleton output for en, de, fr, it, nl, pl, pt-BR, ru, uk, ko, zh and ja; es
 * keeps CLDR's "de" ("5 de ene" vs Apple "5 ene"). Deviation recorded in the WP-313 report.
 */
object MonthDayFormat {
    private const val QUOTE = '\''

    fun format(instant: Instant, zone: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofPattern(pattern(locale), locale).withZone(zone).format(instant)

    /** The derived month-day pattern for [locale]. */
    fun pattern(locale: Locale): String {
        val long = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.LONG, null, IsoChronology.INSTANCE, locale)
        return withoutYear(tokens(long)).joinToString("") { it.text }.replace("MMMM", "MMM").trim()
    }

    private data class Token(val text: String, val isField: Boolean, val isYear: Boolean)

    private fun withoutYear(tokens: List<Token>): List<Token> {
        val yearIndex = tokens.indexOfFirst { it.isYear }
        if (yearIndex < 0) return tokens
        val fieldsBefore = tokens.subList(0, yearIndex).indexOfLast { it.isField }
        val fieldsAfter = tokens.drop(yearIndex + 1).indexOfFirst { it.isField }
        return when {
            // Year leads ("y年M月d日", "y. M. d."): drop it and the literals up to the next field.
            fieldsBefore < 0 && fieldsAfter >= 0 -> tokens.drop(yearIndex + 1 + fieldsAfter)
            // Year trails ("MMMM d, y", "d MMMM y 'г'."): keep everything up to the last field before it.
            fieldsBefore >= 0 && fieldsAfter < 0 -> tokens.take(fieldsBefore + 1)
            // Year between fields: drop it and its following literals.
            fieldsAfter >= 0 -> tokens.take(yearIndex) + tokens.drop(yearIndex + 1 + fieldsAfter)
            else -> tokens
        }
    }

    private fun tokens(pattern: String): List<Token> {
        val result = mutableListOf<Token>()
        var index = 0
        while (index < pattern.length) {
            val start = index
            val character = pattern[index]
            when {
                character == QUOTE -> index = quotedEnd(pattern, index)
                character.isAsciiLetter() -> while (index < pattern.length && pattern[index] == character) index++
                else -> index++
            }
            val text = pattern.substring(start, index)
            val isField = character.isAsciiLetter()
            result += Token(text, isField, isField && (character == 'y' || character == 'u'))
        }
        return result
    }

    private fun quotedEnd(pattern: String, quoteIndex: Int): Int {
        var index = quoteIndex + 1
        while (index < pattern.length) {
            if (pattern[index] == QUOTE) {
                if (index + 1 < pattern.length && pattern[index + 1] == QUOTE) index += 2 else return index + 1
            } else {
                index++
            }
        }
        return index
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
}
