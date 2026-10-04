// AndroidOnly: WP-002 Stable neutral tab/feature IDs; no future domain DTOs or lifecycle implementations.
package com.meshcoreone.android.core.contracts

enum class AppTab(val sourceIndex: Int) {
    CHATS(0),
    NODES(1),
    MAP(2),
    TOOLS(3),
    SETTINGS(4),
}

enum class FeatureId(val stableId: String, val tab: AppTab?) {
    ONBOARDING("onboarding.root", null),
    CHATS("chats.root", AppTab.CHATS),
    NODES("nodes.root", AppTab.NODES),
    REMOTE_NODES("remotenodes.root", null),
    MAP("map.root", AppTab.MAP),
    TOOLS("tools.root", AppTab.TOOLS),
    SETTINGS("settings.root", AppTab.SETTINGS),
    ;

    companion object {
        fun forTab(tab: AppTab): FeatureId = entries.single { it.tab == tab }
    }
}

data class FeatureRoute(val feature: FeatureId)

enum class ScaffoldAvailability {
    NOT_YET_PORTED,
}

data class FeatureShellState(val route: FeatureRoute) {
    val availability: ScaffoldAvailability = ScaffoldAvailability.NOT_YET_PORTED
    val connectionActionsEnabled: Boolean = false
}
