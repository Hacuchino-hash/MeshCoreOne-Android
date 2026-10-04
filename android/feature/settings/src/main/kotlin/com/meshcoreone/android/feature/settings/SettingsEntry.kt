// AndroidOnly: WP-002 Registered unavailable settings/translation; no billing, engine or backup fallback.
package com.meshcoreone.android.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.meshcoreone.android.core.contracts.FeatureId
import com.meshcoreone.android.core.contracts.FeatureRoute
import com.meshcoreone.android.core.l10n.R
import com.meshcoreone.android.core.ui.FeatureShellCopy
import com.meshcoreone.android.core.ui.ScaffoldFeatureContent

@Composable
fun SettingsEntry(route: FeatureRoute, onNavigate: (FeatureRoute) -> Unit) {
    ScaffoldFeatureContent(
        FeatureId.SETTINGS, route,
        FeatureShellCopy(
            stringResource(R.string.tab_settings),
            stringResource(R.string.scaffold_settings_description),
            stringResource(R.string.scaffold_translate),
        ),
        onNavigate,
    )
}
