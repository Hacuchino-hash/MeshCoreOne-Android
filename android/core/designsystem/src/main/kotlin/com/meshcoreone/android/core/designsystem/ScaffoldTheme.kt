// AndroidOnly: WP-301 Backward-compatible launcher seam; the app graph/navigation remain later WPs.
package com.meshcoreone.android.core.designsystem

import androidx.compose.runtime.Composable

@Composable
fun ScaffoldTheme(content: @Composable () -> Unit) {
    MeshCoreTheme(content = content)
}
