// PortedFrom: MC1Tests/Views/RelativeTimestampTextTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meshcoreone.android.core.l10n.generated.AppChatsStrings
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "en-rUS")
class RelativeTimestampTest : SourceCaseProof() {
    private val now = Instant.ofEpochSecond(1_700_000_000)
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources
    private val formatter = TimestampFormatting(Locale.US, ZoneOffset.UTC)
    private fun format(secondsAgo: Long) = formatter.relativeTimestamp((now.epochSecond - secondsAgo).toUInt(), now, resources)
    private fun nowText() = resources.getString(AppChatsStrings.chatsTimestampNow)

    @OriginalCase("RelativeTimestampTextTests::Returns 'Now' for timestamps under 60 seconds()")
    @Test fun immediate() = prove { assertEquals(nowText(), format(0)) }
    @OriginalCase("RelativeTimestampTextTests::Returns 'Now' at 59 seconds ago()")
    @Test fun lastNowSecond() = prove { assertEquals(nowText(), format(59)) }
    @OriginalCase("RelativeTimestampTextTests::Returns relative format at exactly 60 seconds()")
    @Test fun exactMinute() = prove { assertNotEquals(nowText(), format(60)); assertTrue(format(60).isNotEmpty()) }
    @OriginalCase("RelativeTimestampTextTests::Returns non-empty string for minutes ago()")
    @Test fun minutes() = prove { assertTrue(format(120).isNotEmpty()) }
    @OriginalCase("RelativeTimestampTextTests::Returns non-empty string for hours ago()")
    @Test fun hours() = prove { assertTrue(format(3600).isNotEmpty()) }
    @OriginalCase("RelativeTimestampTextTests::Returns non-empty string for yesterday()")
    @Test fun yesterday() = prove { assertTrue(format(86400).isNotEmpty()) }
    @OriginalCase("RelativeTimestampTextTests::Returns non-empty string for days ago()")
    @Test fun days() = prove { assertTrue(format(172_800).isNotEmpty()) }
    @OriginalCase("RelativeTimestampTextTests::Returns abbreviated date format for 7+ days ago()")
    @Test fun week() = prove { assertTrue(' ' in format(604_800)) }
    @OriginalCase("RelativeTimestampTextTests::Returns abbreviated date format for old dates()")
    @Test fun old() = prove { assertTrue(' ' in format(2_592_000)) }
    @OriginalCase("RelativeTimestampTextTests::Uses relative format just before week threshold()")
    @Test fun lastRelativeSecond() = prove { assertTrue(format(604_799).isNotEmpty()) }
    @OriginalCase("RelativeTimestampTextTests::Uses date format at exactly week threshold()")
    @Test fun exactWeek() = prove { assertTrue(' ' in format(604_800)) }

    @Test fun futureFractionalBoundaryAndCivilYesterdayAcrossDstUseTheInjectedClockAndZone() {
        assertEquals(nowText(), formatter.relativeTimestamp(now.plusSeconds(3600), now, resources))
        assertEquals(nowText(), formatter.relativeTimestamp(now.minusSeconds(59).minusNanos(999_999_999), now, resources))
        val zone = ZoneId.of("America/New_York")
        val current = Instant.parse("2024-03-11T04:30:00Z")
        val previousDay = Instant.parse("2024-03-10T05:30:00Z")
        val text = TimestampFormatting(Locale.US, zone).conversationTimestamp(previousDay, current)
        assertTrue(text.lowercase(Locale.ROOT).contains("yesterday"), text)
        for (locale in listOf(Locale.CHINA, Locale.KOREA, Locale.GERMAN, Locale("ar"))) {
            assertTrue(TimestampFormatting(locale, zone).relative(previousDay, current).isNotEmpty())
        }
    }
}
