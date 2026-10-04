// AndroidOnly: WP-002 App registration and basic Back assertions, not product navigation parity.
package com.meshcoreone.android

import com.meshcoreone.android.core.contracts.AppTab
import com.meshcoreone.android.core.contracts.FeatureId
import com.meshcoreone.android.core.contracts.FeatureRoute
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.Test

class ScaffoldNavigationTest {
    @Test
    fun allSevenEntriesAreRegisteredExactlyOnce() {
        assertEquals(FeatureId.entries.toSet(), featureRegistry.keys)
        assertEquals(7, featureRegistry.size)
        featureRegistry.forEach { (id, entry) -> assertEquals(id, entry.id) }
    }

    @Test
    fun auxiliaryEntryPreservesSelectedTabAndBackReturnsToIt() {
        val nodes = ScaffoldNavigation(selectedTab = AppTab.NODES)
        val remote = nodes.navigate(FeatureRoute(FeatureId.REMOTE_NODES))
        assertEquals(AppTab.NODES, remote.selectedTab)
        assertEquals(FeatureId.REMOTE_NODES, remote.route.feature)
        assertEquals(nodes, remote.back())
        assertEquals(ScaffoldNavigation(), nodes.back())
    }

    @Test
    fun switchingTabsClearsAuxiliaryEntry() {
        val setup = ScaffoldNavigation().navigate(FeatureRoute(FeatureId.ONBOARDING))
        val map = setup.navigate(FeatureRoute(FeatureId.MAP))
        assertEquals(AppTab.MAP, map.selectedTab)
        assertEquals(null, map.auxiliary)
        assertEquals(FeatureId.MAP, map.route.feature)
    }

    @Test
    fun rootDoesNotInterceptSystemBack() {
        assertFalse(ScaffoldNavigation().canGoBack)
        assertFailsWith<IllegalStateException> { ScaffoldNavigation().back() }
    }

    @Test
    fun invalidAuxiliaryTabCannotBeSilentlyAccepted() {
        assertFailsWith<IllegalArgumentException> { ScaffoldNavigation(auxiliary = FeatureId.CHATS) }
    }
}
