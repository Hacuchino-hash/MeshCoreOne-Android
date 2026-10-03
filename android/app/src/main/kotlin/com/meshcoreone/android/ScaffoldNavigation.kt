// AndroidOnly: WP-002 Small shell navigation model; not WP-302 Nav3 per-tab/detail behavior.
package com.meshcoreone.android

import com.meshcoreone.android.core.contracts.AppTab
import com.meshcoreone.android.core.contracts.FeatureId
import com.meshcoreone.android.core.contracts.FeatureRoute

internal data class ScaffoldNavigation(
    val selectedTab: AppTab = AppTab.CHATS,
    val auxiliary: FeatureId? = null,
) {
    init {
        require(auxiliary == null || auxiliary.tab == null) { "A tab cannot be an auxiliary route" }
    }

    val route: FeatureRoute
        get() = FeatureRoute(auxiliary ?: FeatureId.forTab(selectedTab))

    val canGoBack: Boolean
        get() = auxiliary != null || selectedTab != AppTab.CHATS

    fun navigate(route: FeatureRoute): ScaffoldNavigation {
        val tab = route.feature.tab
        return if (tab == null) copy(auxiliary = route.feature) else ScaffoldNavigation(selectedTab = tab)
    }

    fun back(): ScaffoldNavigation {
        check(canGoBack) { "Root Back belongs to the Android system" }
        return if (auxiliary != null) copy(auxiliary = null) else ScaffoldNavigation()
    }
}
