// AndroidOnly: WP-002 Neutral shell contract assertions, not original feature parity.
package com.meshcoreone.android.core.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FeatureRouteTest {
    @Test
    fun `five source tab indices stay stable`() {
        assertEquals(listOf(0, 1, 2, 3, 4), AppTab.entries.map { it.sourceIndex })
        assertEquals(listOf("CHATS", "NODES", "MAP", "TOOLS", "SETTINGS"), AppTab.entries.map { it.name })
    }

    @Test
    fun `seven entry IDs are unique and stable`() {
        assertEquals(
            setOf("onboarding.root", "chats.root", "nodes.root", "remotenodes.root", "map.root", "tools.root", "settings.root"),
            FeatureId.entries.map { it.stableId }.toSet(),
        )
        assertEquals(7, FeatureId.entries.map { it.stableId }.distinct().size)
    }

    @Test
    fun `only five features own tabs`() {
        assertEquals(5, FeatureId.entries.count { it.tab != null })
        AppTab.entries.forEach { assertEquals(it, FeatureId.forTab(it).tab) }
        assertEquals(null, FeatureId.ONBOARDING.tab)
        assertEquals(null, FeatureId.REMOTE_NODES.tab)
    }

    @Test
    fun `every shell is explicitly incomplete and cannot perform connection actions`() {
        FeatureId.entries.forEach { id ->
            val state = FeatureShellState(FeatureRoute(id))
            assertEquals(ScaffoldAvailability.NOT_YET_PORTED, state.availability)
            assertFalse(state.connectionActionsEnabled, id.stableId)
        }
    }
}
