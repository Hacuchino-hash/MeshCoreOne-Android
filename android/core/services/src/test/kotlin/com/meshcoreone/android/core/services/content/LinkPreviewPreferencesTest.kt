// PortedFrom: MC1Tests/Models/LinkPreviewPreferencesTests.swift@db14559b39d32322b06477c6ae676112f583db50
// Uses InMemoryLinkPreviewPreferencesSource in place of the Swift test's per-test UserDefaults
// suite (test.<UUID>) - the port's unit boundary is the policy, not the storage mechanism.
package com.meshcoreone.android.core.services.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkPreviewPreferencesTest {
    @Test
    fun `Has expected defaults, previews off, auto-resolve on`() {
        val prefs = LinkPreviewPreferences(InMemoryLinkPreviewPreferencesSource())
        assertFalse(prefs.previewsEnabled)
        assertTrue(prefs.autoResolveDM)
        assertTrue(prefs.autoResolveChannels)
    }

    @Test
    fun `shouldAutoResolve for DM respects settings`() {
        val prefs = LinkPreviewPreferences(InMemoryLinkPreviewPreferencesSource())

        prefs.previewsEnabled = true
        prefs.autoResolveDM = true
        assertTrue(prefs.shouldAutoResolve(isChannelMessage = false))

        prefs.autoResolveDM = false
        assertFalse(prefs.shouldAutoResolve(isChannelMessage = false))

        prefs.previewsEnabled = false
        prefs.autoResolveDM = true
        assertFalse(prefs.shouldAutoResolve(isChannelMessage = false))
    }

    @Test
    fun `shouldAutoResolve for channel respects settings`() {
        val prefs = LinkPreviewPreferences(InMemoryLinkPreviewPreferencesSource())

        prefs.previewsEnabled = true
        prefs.autoResolveChannels = true
        assertTrue(prefs.shouldAutoResolve(isChannelMessage = true))

        prefs.autoResolveChannels = false
        assertFalse(prefs.shouldAutoResolve(isChannelMessage = true))
    }

    @Test
    fun `shouldShowPreview reflects global toggle`() {
        val prefs = LinkPreviewPreferences(InMemoryLinkPreviewPreferencesSource())

        prefs.previewsEnabled = true
        assertTrue(prefs.shouldShowPreview)

        prefs.previewsEnabled = false
        assertFalse(prefs.shouldShowPreview)
    }

    @Test
    fun `Master off overrides channel auto-resolve too`() {
        val prefs = LinkPreviewPreferences(InMemoryLinkPreviewPreferencesSource())

        prefs.previewsEnabled = false
        prefs.autoResolveChannels = true
        assertFalse(prefs.shouldAutoResolve(isChannelMessage = true))
    }

    @Test
    fun `Underlying source mutations are reflected through the wrapper`() {
        val source = InMemoryLinkPreviewPreferencesSource()
        val prefs = LinkPreviewPreferences(source)

        source.previewsEnabled = true
        assertEquals(true, prefs.previewsEnabled)
    }
}
