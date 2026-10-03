// AndroidOnly: WP-002 Native system-appearance scaffold using the incumbent blue, not ten-theme parity.
package com.meshcoreone.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val lightScheme = lightColorScheme(
    primary = Color(0xFF2463EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF00164F),
    secondary = Color(0xFF465C81),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E3F8),
    onSecondaryContainer = Color(0xFF102B4F),
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFAFAFC),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE1E4EB),
    onSurfaceVariant = Color(0xFF424750),
    outline = Color(0xFF737780),
)

private val darkScheme = darkColorScheme(
    primary = Color(0xFFB7C9FF),
    onPrimary = Color(0xFF003586),
    primaryContainer = Color(0xFF1649B3),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = Color(0xFFAFC4E9),
    onSecondary = Color(0xFF183153),
    secondaryContainer = Color(0xFF304969),
    onSecondaryContainer = Color(0xFFD9E3F8),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E2E8),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E2E8),
    surfaceVariant = Color(0xFF424750),
    onSurfaceVariant = Color(0xFFC2C6D0),
    outline = Color(0xFF8C919B),
)

@Composable
fun ScaffoldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkScheme else lightScheme,
        content = content,
    )
}
