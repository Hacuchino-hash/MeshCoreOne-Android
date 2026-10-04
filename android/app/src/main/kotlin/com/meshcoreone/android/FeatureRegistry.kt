// AndroidOnly: WP-002 App-owned seven-entry registration; never feature-to-feature concrete imports.
package com.meshcoreone.android

import androidx.compose.runtime.Composable
import com.meshcoreone.android.core.contracts.FeatureId
import com.meshcoreone.android.core.contracts.FeatureRoute
import com.meshcoreone.android.feature.chats.ChatsEntry
import com.meshcoreone.android.feature.map.MapEntry
import com.meshcoreone.android.feature.nodes.NodesEntry
import com.meshcoreone.android.feature.onboarding.OnboardingEntry
import com.meshcoreone.android.feature.remotenodes.RemoteNodesEntry
import com.meshcoreone.android.feature.settings.SettingsEntry
import com.meshcoreone.android.feature.tools.ToolsEntry

internal data class FeatureRegistration(
    val id: FeatureId,
    val content: @Composable (FeatureRoute, (FeatureRoute) -> Unit) -> Unit,
)

internal val featureRegistry: Map<FeatureId, FeatureRegistration> = listOf(
    FeatureRegistration(FeatureId.ONBOARDING) { route, navigate -> OnboardingEntry(route, navigate) },
    FeatureRegistration(FeatureId.CHATS) { route, navigate -> ChatsEntry(route, navigate) },
    FeatureRegistration(FeatureId.NODES) { route, navigate -> NodesEntry(route, navigate) },
    FeatureRegistration(FeatureId.REMOTE_NODES) { route, navigate -> RemoteNodesEntry(route, navigate) },
    FeatureRegistration(FeatureId.MAP) { route, navigate -> MapEntry(route, navigate) },
    FeatureRegistration(FeatureId.TOOLS) { route, navigate -> ToolsEntry(route, navigate) },
    FeatureRegistration(FeatureId.SETTINGS) { route, navigate -> SettingsEntry(route, navigate) },
).let { registrations ->
    check(registrations.map { it.id }.distinct().size == registrations.size) { "Duplicate feature registration" }
    registrations.associateBy { it.id }.also {
        check(it.keys == FeatureId.entries.toSet()) { "Missing feature registration" }
    }
}
