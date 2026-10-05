// PortedFrom: MC1/Theme/AppColors.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Extensions/SNRQuality+Color.swift@db14559b39d32322b06477c6ae676112f583db50
// Native adaptation: accessible Material roles are separate from immutable source palette roles.
package com.meshcoreone.android.core.designsystem

object AppColors {
    object Radio {
        val repeatMode = ThemeColor.hex(0xFF9500)
        val connecting = ThemeColor.hex(0x007AFF)
        val ready = ThemeColor.hex(0x34C759)
    }
    object Message {
        val outgoingBubble = ThemeColor.hex(0x2463EB)
        fun outgoingBubbleFailed(highContrast: Boolean): ThemeColor =
            if (highContrast) ThemeColor.hex(0xFF3B30) else ThemeColor.hex(0xFF3B30).copy(alpha = 0.8)
    }
}

enum class SignalColorRole(val barLevel: Double) {
    EXCELLENT(1.0), GOOD(0.75), FAIR(0.5), POOR(0.25), UNKNOWN(0.0);
}

enum class RadioColorRole { DISCONNECTED, CONNECTING, CONNECTED, SYNCING, READY, REPEAT, FAILED }

data class MaterialRoles(
    val primary: ThemeColor,
    val onPrimary: ThemeColor,
    val primaryContainer: ThemeColor,
    val onPrimaryContainer: ThemeColor,
    val secondary: ThemeColor,
    val onSecondary: ThemeColor,
    val secondaryContainer: ThemeColor,
    val onSecondaryContainer: ThemeColor,
    val tertiary: ThemeColor,
    val onTertiary: ThemeColor,
    val tertiaryContainer: ThemeColor,
    val onTertiaryContainer: ThemeColor,
    val background: ThemeColor,
    val onBackground: ThemeColor,
    val surface: ThemeColor,
    val onSurface: ThemeColor,
    val surfaceVariant: ThemeColor,
    val onSurfaceVariant: ThemeColor,
    val outline: ThemeColor,
    val outlineVariant: ThemeColor,
    val error: ThemeColor,
    val onError: ThemeColor,
    val errorContainer: ThemeColor,
    val onErrorContainer: ThemeColor,
    val outgoingBubble: ThemeColor,
    val onOutgoingBubble: ThemeColor,
    val incomingBubble: ThemeColor,
    val onIncomingBubble: ThemeColor,
    val incomingHashtag: ThemeColor,
) {
    companion object {
        fun from(frame: ThemeFrame): MaterialRoles {
            val floor = WCAGContrast.floor(frame.highContrast)
            val primary = accessibleForeground(frame.accent, frame.canvas, floor)
            val primaryContainer = frame.canvas.mix(frame.accent, if (frame.highContrast) 0.10 else 0.15)
            val secondaryContainer = frame.card
            val secondary = accessibleForeground(frame.hashtag, frame.canvas, floor)
            val tertiarySeed = frame.categoryAvatarColor(AvatarCategory.ROOM)
            val tertiary = accessibleForeground(tertiarySeed, frame.canvas, floor)
            val tertiaryContainer = frame.canvas.mix(tertiarySeed, 0.12)
            val error = accessibleForeground(ThemeColor.hex(0xFF3B30), frame.canvas, floor)
            val errorContainer = frame.canvas.mix(ThemeColor.hex(0xFF3B30), 0.10)
            return MaterialRoles(
                primary, readableGlyph(frame.outgoingText, primary), primaryContainer,
                accessibleForeground(primary, primaryContainer, floor),
                secondary, readableGlyph(ThemeColor.WHITE, secondary), secondaryContainer,
                accessibleForeground(secondary, secondaryContainer, floor),
                tertiary, readableGlyph(ThemeColor.WHITE, tertiary), tertiaryContainer,
                accessibleForeground(tertiary, tertiaryContainer, floor),
                frame.canvas, accessibleForeground(ThemeColor.BLACK, frame.canvas, floor),
                frame.card, accessibleForeground(ThemeColor.BLACK, frame.card, floor),
                frame.incomingBubble, accessibleForeground(ThemeColor.BLACK, frame.incomingBubble, floor),
                accessibleForeground(frame.accent, frame.canvas, 3.0), frame.canvas.mix(primary, 0.25),
                error, readableGlyph(ThemeColor.WHITE, error), errorContainer,
                accessibleForeground(error, errorContainer, floor),
                frame.accent, readableGlyph(frame.outgoingText, frame.accent), frame.incomingBubble,
                accessibleForeground(ThemeColor.BLACK, frame.incomingBubble, floor),
                accessibleForeground(frame.hashtag, frame.incomingBubble, WCAGContrast.AA_FLOOR),
            )
        }
    }

    fun signalColor(role: SignalColorRole): ThemeColor = accessibleForeground(
        when (role) {
            SignalColorRole.EXCELLENT, SignalColorRole.GOOD -> AppColors.Radio.ready
            SignalColorRole.FAIR -> ThemeColor.hex(0xFFCC00)
            SignalColorRole.POOR -> ThemeColor.hex(0xFF3B30)
            SignalColorRole.UNKNOWN -> onSurfaceVariant
        },
        background, WCAGContrast.AA_FLOOR,
    )

    fun radioColor(role: RadioColorRole): ThemeColor = accessibleForeground(
        when (role) {
            RadioColorRole.DISCONNECTED -> onSurfaceVariant
            RadioColorRole.CONNECTING, RadioColorRole.CONNECTED, RadioColorRole.SYNCING -> AppColors.Radio.connecting
            RadioColorRole.READY -> AppColors.Radio.ready
            RadioColorRole.REPEAT -> AppColors.Radio.repeatMode
            RadioColorRole.FAILED -> error
        },
        background, WCAGContrast.AA_FLOOR,
    )
}

fun readableGlyph(preferred: ThemeColor, background: ThemeColor): ThemeColor {
    if (WCAGContrast.contrastRatio(preferred, background) >= WCAGContrast.AA_FLOOR + 0.05) return preferred
    return if (WCAGContrast.contrastRatio(ThemeColor.WHITE, background) >=
        WCAGContrast.contrastRatio(ThemeColor.BLACK, background)
    ) ThemeColor.WHITE else ThemeColor.BLACK
}

fun accessibleForeground(preferred: ThemeColor, background: ThemeColor, floor: Double): ThemeColor {
    require(floor.isFinite() && floor in 1.0..21.0 && background.alpha == 1.0)
    if (WCAGContrast.contrastRatio(preferred, background) >= floor + 0.05) return preferred
    val target = readableGlyph(ThemeColor.TRANSPARENT, background)
    require(WCAGContrast.contrastRatio(target, background) >= floor) {
        "The requested contrast is physically unreachable on this surface"
    }
    val desired = minOf(floor + 0.05, WCAGContrast.contrastRatio(target, background))
    var low = 0.0
    var high = 1.0
    repeat(32) {
        val middle = (low + high) / 2
        if (WCAGContrast.contrastRatio(preferred.mix(target, middle), background) < desired) low = middle else high = middle
    }
    return preferred.mix(target, high)
}
