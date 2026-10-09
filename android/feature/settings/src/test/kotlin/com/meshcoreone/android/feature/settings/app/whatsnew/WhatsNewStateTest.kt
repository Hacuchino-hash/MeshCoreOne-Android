// PortedFrom: MC1Tests/State/WhatsNewStateTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.settings.app.whatsnew

import com.meshcoreone.android.core.designsystem.MeshSymbol
import com.meshcoreone.android.feature.settings.app.support.OriginalCase
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class WhatsNewStateTest {
    private class FakeBaseline(override var lastShownVersion: String? = null) : WhatsNewBaselineStore

    private fun version(text: String) = WhatsNewVersion.parse(text)!!

    private fun release(major: Int, minor: Int, items: Int = 1) = WhatsNewRelease(
        WhatsNewVersion(major, minor), (0 until items).map { WhatsNewItem(MeshSymbol.CHECK, it + 1, it + 100) }, "https://example.com/releases",
    )

    private fun resolve(current: String, baseline: String?, onboarded: Boolean, catalog: List<WhatsNewRelease>) =
        WhatsNewState.resolve(version(current), baseline, onboarded, catalog)

    @Test
    @OriginalCase("WhatsNewStateTests::new install (not onboarded, no baseline) suppresses()")
    fun `new install suppresses`() = assertNull(resolve("1.1", null, false, listOf(release(1, 1))))

    @Test
    @OriginalCase("WhatsNewStateTests::upgrader (onboarded, no baseline, entry exists) shows()")
    fun `upgrader shows`() = assertEquals(WhatsNewVersion(1, 1), resolve("1.1", null, true, listOf(release(1, 1)))?.version)

    @Test
    @OriginalCase("WhatsNewStateTests::patch bump suppresses()")
    fun `patch bump suppresses`() = assertNull(resolve("1.0.2", "1.0", true, listOf(release(1, 0))))

    @Test
    @OriginalCase("WhatsNewStateTests::minor bump shows()")
    fun `minor bump shows`() = assertEquals(WhatsNewVersion(1, 1), resolve("1.1", "1.0", true, listOf(release(1, 1)))?.version)

    @Test
    @OriginalCase("WhatsNewStateTests::major bump shows()")
    fun `major bump shows`() = assertEquals(WhatsNewVersion(2, 0), resolve("2.0", "1.9", true, listOf(release(2, 0)))?.version)

    @Test
    @OriginalCase("WhatsNewStateTests::skipped version shows the current release's entry()")
    fun `skipped version shows the current releases entry`() =
        assertEquals(WhatsNewVersion(1, 2), resolve("1.2", "1.0", true, listOf(release(1, 1), release(1, 2)))?.version)

    @Test
    @OriginalCase("WhatsNewStateTests::no catalog entry for current version suppresses()")
    fun `no catalog entry suppresses`() = assertNull(resolve("1.3", "1.0", true, listOf(release(1, 1), release(1, 2))))

    @Test
    @OriginalCase("WhatsNewStateTests::matched entry with empty items suppresses()")
    fun `matched entry with empty items suppresses`() = assertNull(resolve("1.1", null, true, listOf(release(1, 1, items = 0))))

    @Test
    @OriginalCase("WhatsNewStateTests::re-launch after a show (baseline equals current) does not re-show()")
    fun `relaunch after a show does not re-show`() = assertNull(resolve("1.1", "1.1", true, listOf(release(1, 1))))

    @Test
    @OriginalCase("WhatsNewStateTests::screenshot mode neither shows nor writes a baseline()")
    fun `screenshot mode neither shows nor writes a baseline`() {
        val baseline = FakeBaseline()
        val state = WhatsNewState(baseline, "1.1")
        state.evaluate(isOnboarded = true, isScreenshotMode = true, catalog = listOf(release(1, 1)))
        assertNull(state.pendingRelease.value)
        assertNull(baseline.lastShownVersion)
    }

    @Test
    @OriginalCase("WhatsNewStateTests::show path sets pendingRelease and defers the baseline to markShown()")
    fun `show path sets pending and defers the baseline`() {
        val baseline = FakeBaseline()
        val state = WhatsNewState(baseline, "1.1")
        state.evaluate(isOnboarded = true, isScreenshotMode = false, catalog = listOf(release(1, 1)))
        assertEquals(WhatsNewVersion(1, 1), state.pendingRelease.value?.version)
        assertNull(baseline.lastShownVersion)
    }

    @Test
    @OriginalCase("WhatsNewStateTests::suppress path finalizes the baseline immediately()")
    fun `suppress path finalizes the baseline immediately`() {
        val baseline = FakeBaseline()
        val state = WhatsNewState(baseline, "1.1")
        state.evaluate(isOnboarded = true, isScreenshotMode = false, catalog = emptyList())
        assertNull(state.pendingRelease.value)
        assertEquals("1.1", baseline.lastShownVersion)
    }

    @Test
    @OriginalCase("WhatsNewStateTests::unparseable version fails closed: neither shows nor writes a baseline()")
    fun `unparseable version fails closed`() {
        val baseline = FakeBaseline()
        val state = WhatsNewState(baseline, "unknown")
        state.evaluate(isOnboarded = true, isScreenshotMode = false, catalog = listOf(release(1, 1)))
        assertNull(state.pendingRelease.value)
        assertNull(baseline.lastShownVersion)
    }

    @Test
    @OriginalCase("WhatsNewStateTests::markShown persists the running version and clears the pending release()")
    fun `markShown persists the running version and clears pending`() {
        val baseline = FakeBaseline()
        val state = WhatsNewState(baseline, "1.1")
        state.evaluate(isOnboarded = true, isScreenshotMode = false, catalog = listOf(release(1, 1)))
        state.markShown()
        assertNull(state.pendingRelease.value)
        assertEquals("1.1", baseline.lastShownVersion)
    }

    @Test
    fun `an unparseable stored baseline fails closed`() = assertNull(resolve("1.1", "garbage", true, listOf(release(1, 1))))
}
