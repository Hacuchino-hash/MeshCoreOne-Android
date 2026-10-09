// PortedFrom: MC1/Views/Onboarding/OnboardingMetrics.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.onboarding.ui

import androidx.compose.ui.unit.dp

/** Named layout constants; 48dp replaces the 44pt iOS hit target to meet Android's minimum. */
object OnboardingMetrics {
    val heroSize = 130.dp
    val iconSize = 60.dp
    val cardCornerRadius = 12.dp
    val compactSpacing = 4.dp
    val titleStackSpacing = 8.dp
    val mediumSpacing = 12.dp
    val cardSpacing = 16.dp
    val contentPadding = 20.dp
    val largeSpacing = 24.dp
    val sheetTopPadding = 32.dp
    val minHitTarget = 48.dp
    val headerTopPadding = 40.dp
    /** Keeps content readable on tablets and unfolded foldables. */
    val maxContentWidth = 560.dp
    val meshAnimationHeight = 150.dp
}
