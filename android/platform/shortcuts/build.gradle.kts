// AndroidOnly: WP-002 Shortcut boundary without external/device-changing actions.
plugins { id("mesh.android.library") }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(project(":core:l10n"))
}
