// AndroidOnly: WP-002 Registered unavailable setup entry; no pairing, permissions or onboarding completion.
package com.meshcoreone.android.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.meshcoreone.android.core.contracts.FeatureId
import com.meshcoreone.android.core.contracts.FeatureRoute
import com.meshcoreone.android.core.l10n.R
import com.meshcoreone.android.core.ui.FeatureShellCopy
import com.meshcoreone.android.core.ui.ScaffoldFeatureContent

@Composable
fun OnboardingEntry(route: FeatureRoute, onNavigate: (FeatureRoute) -> Unit) {
    ScaffoldFeatureContent(
        FeatureId.ONBOARDING, route,
        FeatureShellCopy(
            stringResource(R.string.scaffold_onboarding_title),
            stringResource(R.string.scaffold_onboarding_description),
            stringResource(R.string.scaffold_connect_radio),
        ),
        onNavigate,
    )
}
