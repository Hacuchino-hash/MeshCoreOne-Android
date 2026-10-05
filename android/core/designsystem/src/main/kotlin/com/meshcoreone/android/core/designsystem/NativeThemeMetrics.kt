// AndroidOnly: WP-301 Native neutral tiers, >=48dp controls, and duration-scale-aware motion policies.
package com.meshcoreone.android.core.designsystem

object NativeThemeMetrics {
    const val MINIMUM_TOUCH_TARGET = 49
    const val FAST_MOTION_MILLIS = 150
    const val STANDARD_MOTION_MILLIS = 200
    fun duration(millis: Int, scale: Float): Int {
        require(millis >= 0 && scale.isFinite() && scale >= 0)
        val value = millis.toDouble() * scale
        require(value <= Int.MAX_VALUE)
        return value.toInt()
    }
}

data class NativeSystemSurfaces(val canvas: ThemeColor, val card: ThemeColor, val incomingBubble: ThemeColor) {
    companion object {
        fun forAppearance(scheme: ColorScheme, highContrast: Boolean): NativeSystemSurfaces = when (scheme) {
            ColorScheme.LIGHT -> NativeSystemSurfaces(
                if (highContrast) ThemeColor.WHITE else ThemeColor.hex(0xFAFAFC),
                ThemeColor.WHITE, ThemeColor.hex(0xE5E5EA),
            )
            ColorScheme.DARK -> NativeSystemSurfaces(
                if (highContrast) ThemeColor.BLACK else ThemeColor.hex(0x121318),
                ThemeColor.hex(0x24252B), ThemeColor.hex(0x2C2C2E),
            )
        }
    }
}
