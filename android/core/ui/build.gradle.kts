// AndroidOnly: WP-002 Shared explicit unavailable-state content, not product components.
plugins {
    id("mesh.android.library")
    id("mesh.android.compose")
}
dependencies {
    implementation(project(":core:contracts"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:l10n"))
}
