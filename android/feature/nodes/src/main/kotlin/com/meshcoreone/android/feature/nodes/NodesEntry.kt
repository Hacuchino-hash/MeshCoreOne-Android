// AndroidOnly: WP-002 Registered unavailable nodes entry; auxiliary navigation uses neutral routes only.
package com.meshcoreone.android.feature.nodes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.meshcoreone.android.core.contracts.FeatureId
import com.meshcoreone.android.core.contracts.FeatureRoute
import com.meshcoreone.android.core.l10n.R
import com.meshcoreone.android.core.ui.FeatureShellCopy
import com.meshcoreone.android.core.ui.ScaffoldFeatureContent
import com.meshcoreone.android.core.ui.ScaffoldLink

@Composable
fun NodesEntry(route: FeatureRoute, onNavigate: (FeatureRoute) -> Unit) {
    ScaffoldFeatureContent(
        FeatureId.NODES, route,
        FeatureShellCopy(
            stringResource(R.string.tab_nodes),
            stringResource(R.string.scaffold_nodes_description),
            stringResource(R.string.scaffold_discover_nodes),
        ),
        onNavigate,
        links = listOf(ScaffoldLink(stringResource(R.string.scaffold_remote_nodes_title), FeatureRoute(FeatureId.REMOTE_NODES))),
    )
}
