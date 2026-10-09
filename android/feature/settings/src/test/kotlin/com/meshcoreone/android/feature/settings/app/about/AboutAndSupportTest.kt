// AndroidOnly: WP-318 About, licenses and IAP-free support content (no Swift unit test exists for these views).
package com.meshcoreone.android.feature.settings.app.about

import com.meshcoreone.android.core.designsystem.ThemeRegistry
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AboutAndSupportTest {
    @Test
    fun `about rows keep the source order and every external link is https`() {
        assertEquals(
            listOf(AboutDestination.SUPPORT, AboutDestination.FEEDBACK),
            AboutLinks.rows.filterIsInstance<AboutRow.Destination>().map { it.destination },
        )
        assertIs<AboutRow.Destination>(AboutLinks.rows[0])
        assertIs<AboutRow.Destination>(AboutLinks.rows[1])
        val external = AboutLinks.rows.filterIsInstance<AboutRow.ExternalLink>().map { it.url }
        assertEquals(
            listOf("https://meshcore.io", "https://map.meshcore.io/", "https://github.com/Avi0n/MeshCoreOne", "https://meshcoreone.com/privacy.html"),
            external,
        )
    }

    @Test
    fun `feedback and support links carry issues sponsors and the developer email`() {
        assertEquals(listOf("https://github.com/Avi0n/MeshCoreOne/issues", "mailto:info@meshcoreone.com"), AboutLinks.feedbackLinks.map { it.url })
        assertEquals(listOf("https://github.com/sponsors/Avi0n", "mailto:info@meshcoreone.com"), AboutLinks.supportLinks.map { it.url })
        assertEquals("mailto:${AboutLinks.CONTACT_EMAIL}", AboutLinks.CONTACT_MAILTO)
    }

    @Test
    fun `support screen is billing-free and shows the all-unlocked card for the full registry`() {
        val model = SupportScreenModel.create()
        assertTrue(model.allThemesUnlocked)
        assertEquals(ThemeRegistry.allThemes.size, model.themes.size)
        assertTrue(model.links.none { it.url.contains("apps.apple.com") || it.url.contains("play.google.com") })
    }

    @Test
    fun `a partial available set is not reported as all unlocked`() {
        val partial = SupportScreenModel.create(available = ThemeRegistry.allThemes.take(1))
        assertEquals(false, partial.allThemesUnlocked)
    }

    @Test
    fun `license catalog has unique ids, the app and MeshCore first and omits Apple-only packages`() {
        val ids = LicenseCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf("meshcore-one", "meshcore"), ids.take(2))
        assertEquals(LicenseKind.GPL_3, LicenseCatalog.entry("meshcore-one")?.kind)
        assertEquals(LicenseKind.MIT, LicenseCatalog.entry("meshcore")?.kind)
        val names = LicenseCatalog.entries.map { it.name }
        assertTrue(LicenseCatalog.appleOnlyOmitted.none { it in names })
    }

    @Test
    fun `license text resolution reports missing or blank text instead of inventing it`() = runBlocking<Unit> {
        val entry = LicenseCatalog.entries.first()
        assertIs<LicenseText.Loaded>(LicenseTextSource { "GPL text" }.resolve(entry))
        assertIs<LicenseText.Missing>(LicenseTextSource { null }.resolve(entry))
        assertIs<LicenseText.Missing>(LicenseTextSource { "  " }.resolve(entry))
        assertIs<LicenseText.Missing>(LicenseTextSource { throw java.io.IOException("asset gone") }.resolve(entry))
    }
}
