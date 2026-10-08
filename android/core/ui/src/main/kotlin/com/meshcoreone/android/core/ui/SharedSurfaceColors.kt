// AndroidOnly: WP-304 Fit semantic foregrounds to the actual painted Material surface without changing source palettes.
package com.meshcoreone.android.core.ui

import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import com.meshcoreone.android.core.designsystem.LocalMeshTheme
import com.meshcoreone.android.core.designsystem.ThemeColor
import com.meshcoreone.android.core.designsystem.WCAGContrast
import com.meshcoreone.android.core.designsystem.accessibleForeground
import com.meshcoreone.android.core.designsystem.toComposeColor
import com.meshcoreone.android.core.designsystem.toThemeColor

val SharedPaintedSurface = SemanticsPropertyKey<Color>("SharedPaintedSurface")
var SemanticsPropertyReceiver.sharedPaintedSurface by SharedPaintedSurface
val SharedEmittedForeground = SemanticsPropertyKey<Color>("SharedEmittedForeground")
var SemanticsPropertyReceiver.sharedEmittedForeground by SharedEmittedForeground
val SharedEmittedIcon = SemanticsPropertyKey<Color>("SharedEmittedIcon")
var SemanticsPropertyReceiver.sharedEmittedIcon by SharedEmittedIcon

@Composable
fun sharedPaintedSurface(): Color =
    MaterialTheme.colorScheme.surfaceColorAtElevation(LocalAbsoluteTonalElevation.current)

fun surfaceForeground(preferred: ThemeColor, paintedSurface: Color, highContrast: Boolean): Color =
    accessibleForeground(preferred, paintedSurface.toThemeColor(), WCAGContrast.floor(highContrast)).toComposeColor()

@Composable
fun sharedErrorForeground(paintedSurface: Color): Color =
    surfaceForeground(ThemeColor.hex(0xFF3B30), paintedSurface, LocalMeshTheme.current.frame.highContrast)

enum class SharedStatusColorRole(val sourceSeed: ThemeColor?) {
    PRIMARY(null),
    GREEN(ThemeColor.hex(0x34C759)),
    YELLOW(ThemeColor.hex(0xFFCC00)),
    ORANGE(ThemeColor.hex(0xFF9500)),
    RED(ThemeColor.hex(0xFF3B30));
}

val RSSITuning.SignalTier.colorRole: SharedStatusColorRole get() = when (this) {
    RSSITuning.SignalTier.STRONG -> SharedStatusColorRole.GREEN
    RSSITuning.SignalTier.MEDIUM -> SharedStatusColorRole.YELLOW
    RSSITuning.SignalTier.WEAK -> SharedStatusColorRole.RED
}

val StatusPillState.textColorRole: SharedStatusColorRole get() = when (this) {
    StatusPillState.Disconnected -> SharedStatusColorRole.ORANGE
    is StatusPillState.Failed -> SharedStatusColorRole.RED
    else -> SharedStatusColorRole.PRIMARY
}

val StatusPillState.iconColorRole: SharedStatusColorRole get() = when (this) {
    StatusPillState.Ready -> SharedStatusColorRole.GREEN
    else -> textColorRole
}

fun statusForeground(role: SharedStatusColorRole, primary: Color, background: Color, highContrast: Boolean): Color =
    surfaceForeground(role.sourceSeed ?: primary.toThemeColor(), background, highContrast)
