// AndroidOnly: WP-002 Shared map boundary; no provider, tile download or fabricated map.
plugins {
    id("mesh.android.library")
    id("mesh.android.compose")
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:l10n"))
}
