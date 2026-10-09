// AndroidOnly: WP-318 Adapter from core:designsystem's ThemeService to the feature seam (designsystem is a permitted feature dependency).
package com.meshcoreone.android.feature.settings.app.appearance

import com.meshcoreone.android.core.designsystem.AppColorSchemePreference
import com.meshcoreone.android.core.designsystem.ThemeId
import com.meshcoreone.android.core.designsystem.ThemeRegistry
import com.meshcoreone.android.core.designsystem.ThemeService
import com.meshcoreone.android.core.designsystem.ThemeServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Until the service is ready (or after it closes without a prior value) the default theme/system scheme is shown. */
class ThemeServiceAppearanceController(private val service: ThemeService, scope: CoroutineScope) : AppearanceController {
    override val selection: StateFlow<AppearanceSelection> = service.state
        .map(::selectionOf)
        .stateIn(scope, SharingStarted.Eagerly, selectionOf(service.state.value))

    override suspend fun setTheme(id: ThemeId) = service.setCurrent(id)

    override suspend fun setColorScheme(preference: AppColorSchemePreference) = service.setColorSchemePreference(preference)

    private fun selectionOf(state: ThemeServiceState): AppearanceSelection {
        val current = when (state) {
            is ThemeServiceState.Ready -> state.selection
            is ThemeServiceState.Failed -> state.previous
            is ThemeServiceState.Closed -> state.previous
            ThemeServiceState.Loading -> null
        }
        return AppearanceSelection(
            current?.current?.id ?: ThemeRegistry.default.id,
            current?.colorSchemePreference ?: AppColorSchemePreference.SYSTEM,
        )
    }
}
