// AndroidOnly: WP-005 Native adaptation boundaries, per-app numeric locale and typed consumer assertions.
package com.meshcoreone.android.core.l10n

import android.content.res.Resources
import android.icu.text.PluralRules
import com.meshcoreone.android.core.l10n.generated.AppContactsStrings
import com.meshcoreone.android.core.l10n.generated.AppLocalizableStrings
import com.meshcoreone.android.core.l10n.generated.AppSettingsStrings
import com.meshcoreone.android.core.l10n.generated.WidgetLocalizableStrings
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class L10nFormattingTest {
    @Test
    fun pluralPartialRemovalSelectsUsingTotalArgumentTwo() {
        val actual = AppSettingsStrings.dangerZoneAlertRemoveUnfavoritedPartial(resourcesForLocale("en"), 0, 1)
        assertEquals("Removed 0 of 1 node. The connection was interrupted \u2014 tap the button again to retry.", actual)
        assertTrue(AppSettingsStrings.dangerZoneAlertRemoveUnfavoritedPartial(resourcesForLocale("en"), 1, 2).startsWith("Removed 1 of 2 nodes."))
    }

    @Test
    fun fixedPrecisionFloatUsesPerAppLocaleNotTheJvmDefault() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertEquals("12,50", L10nFormatting.format(resourcesForLocale("de"), "%1$.2f", arrayOf(12.5)))
            assertEquals("12,50", L10nFormatting.format(resourcesForLocale("fr"), "%1$.2f", arrayOf(12.5)))
            assertEquals("12.50", L10nFormatting.format(resourcesForLocale("en"), "%1$.2f", arrayOf(12.5)))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun nonfiniteFloatingPointCannotBecomeDifferentPlatformCopy() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            assertFailsWith<IllegalArgumentException> {
                L10nFormatting.format(resourcesForLocale("en"), "%1\$f", arrayOf(value))
            }
        }
    }

    @Test
    fun displayedLongRetainsSigned64BitBoundaries() {
        val resources = resourcesForLocale("en")
        assertEquals("9223372036854775807 packets per minute", WidgetLocalizableStrings.lldPacketsPerMinute(resources, Long.MAX_VALUE))
        assertEquals("-9223372036854775808 packets per minute", WidgetLocalizableStrings.lldPacketsPerMinute(resources, Long.MIN_VALUE))
        assertEquals("3000000001 unread messages", WidgetLocalizableStrings.lldUnreadMessages(resources, 3_000_000_001))
    }

    @Test
    fun largeIntegerPluralSelectorsPreserveTheTwelveLocalesCategoryRules() {
        val quantities = longArrayOf(3_000_000_000, 3_000_000_001, 3_000_000_002, 3_000_000_005, 3_000_000_011, 3_000_000_012, 3_000_000_021, Long.MAX_VALUE, Long.MIN_VALUE)
        sourceLocales.forEach { locale ->
            val rules = PluralRules.forLocale(Locale.forLanguageTag(if (locale == "pt") "pt-PT" else locale))
            quantities.forEach { quantity ->
                val selected = rules.select(L10nFormatting.quantitySelector(quantity).toDouble())
                val expected = if (quantity in -9_007_199_254_740_991L..9_007_199_254_740_991L) {
                    rules.select(quantity.toDouble())
                } else integerQuantityCategory(locale, quantity)
                assertEquals(expected, selected, "$locale/$quantity")
            }
        }
    }

    @Test
    fun nonnegativeSelectorsRemainNativeAndNegativeSelectorsRetainMagnitude() {
        listOf(0, 1, 2, 11, 21, Int.MAX_VALUE).forEach { quantity ->
            assertEquals(quantity, L10nFormatting.quantitySelector(quantity.toLong()))
        }
        listOf(-1L, -2L, -Int.MAX_VALUE.toLong()).forEach { quantity ->
            assertEquals((-quantity).toInt(), L10nFormatting.quantitySelector(quantity))
        }
    }

    @Test
    fun polishRussianAndUkrainianPluralVectorsRetainOneFewMany() {
        listOf("pl", "ru", "uk").forEach { locale ->
            val resources = resourcesForLocale(locale)
            val record = sourceRecords(locale).single { it.name == "l10n_widget_localizable_lld_unread_messages" }
            listOf(0L, 1L, 2L, 5L, 11L, 21L, 22L, 25L, 101L).forEach { quantity ->
                assertEquals(referencePlural(record, locale, quantity, arrayOf(quantity)), WidgetLocalizableStrings.lldUnreadMessages(resources, quantity), "$locale/$quantity")
            }
        }
    }

    @Test
    fun frenchZeroAndPortugueseVariantUseActualConfigurationPluralRules() {
        val french = sourceRecords("fr").single { it.name == "l10n_widget_localizable_lld_unread_messages" }
        assertEquals(referencePlural(french, "fr", 0, arrayOf(0L)), WidgetLocalizableStrings.lldUnreadMessages(resourcesForLocale("fr"), 0))
        val portuguese = sourceRecords("pt").single { it.name == "l10n_widget_localizable_lld_unread_messages" }
        assertEquals(referencePlural(portuguese, "pt", 0, arrayOf(0L)), WidgetLocalizableStrings.lldUnreadMessages(resourcesForTag("pt-PT"), 0))
        assertEquals(referencePlural(portuguese, "pt-BR", 0, arrayOf(0L)), WidgetLocalizableStrings.lldUnreadMessages(resourcesForTag("pt-BR"), 0))
    }

    @Test
    fun chineseIntegerTypoHasOneExplicitNativeCorrectionAndUnchangedCopy() {
        assertEquals("\u5bf9\u6bd4 13 \u6beb\u79d2\u5728 DATE", AppContactsStrings.contactsResultsComparison(resourcesForLocale("zh-Hans"), 13, "DATE"))
        assertEquals("vs. 13 ms on DATE", AppContactsStrings.contactsResultsComparison(resourcesForLocale("en"), 13, "DATE"))
    }

    @Test
    fun simplifiedChineseScriptAndMainlandAliasResolveWithoutGenericZh() {
        assertEquals("\u5df2\u65ad\u5f00", resourcesForTag("zh-Hans").getString(WidgetLocalizableStrings.disconnected))
        assertEquals("\u5df2\u65ad\u5f00", resourcesForTag("zh-CN").getString(WidgetLocalizableStrings.disconnected))
        assertEquals("Disconnected", resourcesForTag("zh-Hant").getString(WidgetLocalizableStrings.disconnected))
    }

    @Test
    fun unsupportedLanguageUsesCompleteEnglishResources() {
        assertEquals("Disconnected", resourcesForTag("ja").getString(WidgetLocalizableStrings.disconnected))
        assertEquals("13 unread messages", WidgetLocalizableStrings.lldUnreadMessages(resourcesForTag("ja"), 13))
    }

    @Test
    fun argumentPercentQuotesXmlCharactersAndNewlinesAreNeverReinterpreted() {
        val argument = "100% %@ <&> \"quoted\" 'apostrophe'\n\\path"
        assertEquals("Network error: $argument", AppLocalizableStrings.commonErrorNetworkError(resourcesForLocale("en"), argument))
    }

    @Test
    fun missingResourceFailsExplicitlyInsteadOfReturningAKeyOrEmptyString() {
        val resources = resourcesForLocale("en")
        assertFailsWith<Resources.NotFoundException> { L10nFormatting.string(resources, 0, "argument") }
        assertFailsWith<Resources.NotFoundException> { L10nFormatting.plural(resources, 0, 1, 1) }
    }
}
