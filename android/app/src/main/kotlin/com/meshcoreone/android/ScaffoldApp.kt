// AndroidOnly: WP-002 Native operational shell with current-width navigation and explicit unavailable entries.
package com.meshcoreone.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshcoreone.android.core.contracts.AppTab
import com.meshcoreone.android.core.contracts.FeatureId
import com.meshcoreone.android.core.contracts.FeatureRoute
import com.meshcoreone.android.core.l10n.R as Strings

private fun AppTab.titleResource(): Int = when (this) {
    AppTab.CHATS -> Strings.string.tab_chats
    AppTab.NODES -> Strings.string.tab_nodes
    AppTab.MAP -> Strings.string.tab_map
    AppTab.TOOLS -> Strings.string.tab_tools
    AppTab.SETTINGS -> Strings.string.tab_settings
}

private fun AppTab.iconResource(): Int = when (this) {
    AppTab.CHATS -> R.drawable.ic_chats
    AppTab.NODES -> R.drawable.ic_nodes
    AppTab.MAP -> R.drawable.ic_map
    AppTab.TOOLS -> R.drawable.ic_tools
    AppTab.SETTINGS -> R.drawable.ic_settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScaffoldApp() {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.CHATS) }
    var auxiliary by rememberSaveable { mutableStateOf<FeatureId?>(null) }
    val navigation = ScaffoldNavigation(selectedTab, auxiliary)
    val navigate: (FeatureRoute) -> Unit = { route ->
        val next = navigation.navigate(route)
        selectedTab = next.selectedTab
        auxiliary = next.auxiliary
    }
    val back: () -> Unit = {
        val next = navigation.back()
        selectedTab = next.selectedTab
        auxiliary = next.auxiliary
    }
    BackHandler(enabled = navigation.canGoBack, onBack = back)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier.heightIn(min = 64.dp * fontScale),
                    title = { Text(stringResource(Strings.string.app_name), maxLines = 2) },
                    navigationIcon = {
                        if (navigation.canGoBack) {
                            IconButton(onClick = back) {
                                Icon(painterResource(R.drawable.ic_back), stringResource(Strings.string.scaffold_back))
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { navigate(FeatureRoute(FeatureId.ONBOARDING)) },
                            modifier = Modifier.testTag("open-radio-setup"),
                        ) {
                            Icon(painterResource(R.drawable.ic_radio), stringResource(Strings.string.scaffold_onboarding_title))
                        }
                    },
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        modifier = Modifier.heightIn(min = 80.dp * fontScale).testTag("bottom-navigation"),
                    ) {
                        AppTab.entries.forEach { tab ->
                            NavigationBarItem(
                                modifier = Modifier.testTag("tab:${tab.name}"),
                                selected = navigation.selectedTab == tab,
                                onClick = { navigate(FeatureRoute(FeatureId.forTab(tab))) },
                                icon = { Icon(painterResource(tab.iconResource()), contentDescription = null) },
                                label = { Text(stringResource(tab.titleResource()), maxLines = 2) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(if (fontScale >= 1.5f) 128.dp else 96.dp)
                            .verticalScroll(rememberScrollState())
                            .testTag("navigation-rail"),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        AppTab.entries.forEach { tab ->
                            NavigationRailItem(
                                modifier = Modifier.heightIn(min = 72.dp * fontScale).testTag("tab:${tab.name}"),
                                selected = navigation.selectedTab == tab,
                                onClick = { navigate(FeatureRoute(FeatureId.forTab(tab))) },
                                icon = { Icon(painterResource(tab.iconResource()), contentDescription = null) },
                                label = { Text(stringResource(tab.titleResource()), maxLines = 2) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    featureRegistry.getValue(navigation.route.feature).content(navigation.route, navigate)
                }
            }
        }
    }
}
