// PortedFrom: MC1Tests/Localization/AirtimePercentLabelTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.l10n

import com.meshcoreone.android.core.l10n.generated.AppRemoteNodesStrings
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AirtimePercentLabelTest {
    @Test
    fun airtimePercentRendersLiteralPercentSign() {
        assertEquals("Airtime %", resourcesForLocale("en").getString(AppRemoteNodesStrings.remoteNodesStatusAirtimePercent))
    }

    @Test
    fun everyLocalizedLabelPreservesSourceCopyWithoutInventingAPercentSign() {
        sourceLocales.forEach { locale ->
            val raw = sourceRecords(locale).single { it.name == "l10n_app_remotenodes_remotenodes_status_airtimepercent" }
            assertEquals(
                referenceText(requireNotNull(raw.text), emptyArray()),
                resourcesForLocale(locale).getString(AppRemoteNodesStrings.remoteNodesStatusAirtimePercent),
                locale,
            )
        }
        assertEquals(
            "\u901a\u4fe1\u65f6\u95f4\u767e\u5206\u6bd4",
            resourcesForLocale("zh-Hans").getString(AppRemoteNodesStrings.remoteNodesStatusAirtimePercent),
        )
    }
}
