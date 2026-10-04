// AndroidOnly: WP-002 Registered explicitly unavailable map; no fabricated map/provider/location.
package com.meshcoreone.android.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.meshcoreone.android.core.contracts.FeatureId
import com.meshcoreone.android.core.contracts.FeatureRoute
import com.meshcoreone.android.core.l10n.R
import com.meshcoreone.android.core.ui.FeatureShellCopy
import com.meshcoreone.android.core.ui.ScaffoldFeatureContent

@Composable
fun MapEntry(route: FeatureRoute, onNavigate: (FeatureRoute) -> Unit) {
    ScaffoldFeatureContent(
        FeatureId.MAP, route,
        FeatureShellCopy(
            stringResource(R.string.tab_map),
            stringResource(R.string.scaffold_map_description),
            stringResource(R.string.scaffold_open_map),
        ),
        onNavigate,
    )
}
