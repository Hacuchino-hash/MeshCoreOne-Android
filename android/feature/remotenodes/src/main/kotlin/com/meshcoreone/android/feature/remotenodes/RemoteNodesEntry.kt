// AndroidOnly: WP-002 Registered unavailable remote-node entry; no extra tab or login/admin success.
package com.meshcoreone.android.feature.remotenodes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.meshcoreone.android.core.contracts.FeatureId
import com.meshcoreone.android.core.contracts.FeatureRoute
import com.meshcoreone.android.core.l10n.R
import com.meshcoreone.android.core.ui.FeatureShellCopy
import com.meshcoreone.android.core.ui.ScaffoldFeatureContent

@Composable
fun RemoteNodesEntry(route: FeatureRoute, onNavigate: (FeatureRoute) -> Unit) {
    ScaffoldFeatureContent(
        FeatureId.REMOTE_NODES, route,
        FeatureShellCopy(
            stringResource(R.string.scaffold_remote_nodes_title),
            stringResource(R.string.scaffold_remote_nodes_description),
            stringResource(R.string.scaffold_manage_remote_node),
        ),
        onNavigate,
    )
}
