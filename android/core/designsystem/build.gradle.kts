// AndroidOnly: WP-002 Minimal Material scaffold theme, not the WP-301 ten-theme port.
plugins {
    id("mesh.android.library")
    id("mesh.android.compose")
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:l10n"))
}
