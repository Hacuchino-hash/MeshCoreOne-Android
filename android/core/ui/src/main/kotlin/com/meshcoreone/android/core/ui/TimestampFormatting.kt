// PortedFrom: MC1/Extensions/Date+RelativeFormatting.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Components/ConversationTimestamp.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Components/RelativeTimestampText.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.ui

import android.content.res.Resources
import android.icu.text.DateFormat
import android.icu.text.DateTimePatternGenerator
import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import com.meshcoreone.android.core.l10n.generated.AppChatsStrings
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

class TimestampFormatting(private val locale: Locale, private val zone: ZoneId) {
    private fun relativeFormatter() = RelativeDateTimeFormatter.getInstance(
        ULocale.forLocale(locale), null, RelativeDateTimeFormatter.Style.SHORT,
        DisplayContext.CAPITALIZATION_NONE,
    )

    fun relative(date: Instant, now: Instant): String {
        val duration = Duration.between(now, date)
        val seconds = duration.seconds.toDouble() + duration.nano / 1_000_000_000.0
        val absolute = kotlin.math.abs(seconds)
        val (amount, unit) = when {
            absolute < 60 -> seconds to RelativeDateTimeFormatter.RelativeDateTimeUnit.SECOND
            absolute < 3600 -> seconds / 60 to RelativeDateTimeFormatter.RelativeDateTimeUnit.MINUTE
            absolute < 86400 -> seconds / 3600 to RelativeDateTimeFormatter.RelativeDateTimeUnit.HOUR
            else -> seconds / 86400 to RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY
        }
        return relativeFormatter().format(amount, unit)
    }

    fun abbreviatedDate(date: Instant): String {
        val pattern = DateTimePatternGenerator.getInstance(locale).getBestPattern("MMMd")
        val formatter = SimpleDateFormat(pattern, locale)
        formatter.timeZone = android.icu.util.TimeZone.getTimeZone(zone.id)
        return formatter.format(Date.from(date))
    }

    fun relativeTimestamp(date: Instant, now: Instant, resources: Resources): String {
        val age = Duration.between(date, now)
        return when {
            age < Duration.ofSeconds(60) -> resources.getString(AppChatsStrings.chatsTimestampNow)
            age >= Duration.ofSeconds(604_800) -> abbreviatedDate(date)
            else -> relative(date, now)
        }
    }

    fun relativeTimestamp(timestamp: UInt, now: Instant, resources: Resources): String =
        relativeTimestamp(Instant.ofEpochSecond(timestamp.toLong()), now, resources)

    fun conversationTimestamp(date: Instant, now: Instant): String {
        val dateDay = date.atZone(zone).toLocalDate()
        val nowDay = now.atZone(zone).toLocalDate()
        return when (dateDay) {
            nowDay -> DateFormat.getTimeInstance(DateFormat.SHORT, locale).also {
                it.timeZone = android.icu.util.TimeZone.getTimeZone(zone.id)
            }.format(Date.from(date))
            nowDay.minusDays(1) -> relativeFormatter().format(
                -1.0, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY,
            )
            else -> abbreviatedDate(date)
        }
    }
}
