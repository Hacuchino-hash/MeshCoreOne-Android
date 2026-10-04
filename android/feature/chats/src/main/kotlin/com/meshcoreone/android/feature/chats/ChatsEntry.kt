// AndroidOnly: WP-002 Registered unavailable messaging entry; no fake contacts/messages/send result.
package com.meshcoreone.android.feature.chats

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.meshcoreone.android.core.contracts.FeatureId
import com.meshcoreone.android.core.contracts.FeatureRoute
import com.meshcoreone.android.core.l10n.R
import com.meshcoreone.android.core.ui.FeatureShellCopy
import com.meshcoreone.android.core.ui.ScaffoldFeatureContent

@Composable
fun ChatsEntry(route: FeatureRoute, onNavigate: (FeatureRoute) -> Unit) {
    ScaffoldFeatureContent(
        FeatureId.CHATS, route,
        FeatureShellCopy(
            stringResource(R.string.tab_chats),
            stringResource(R.string.scaffold_chats_description),
            stringResource(R.string.scaffold_send_message),
        ),
        onNavigate,
    )
}
