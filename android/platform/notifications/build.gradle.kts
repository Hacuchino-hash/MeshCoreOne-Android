// AndroidOnly: WP-002 Delivery adapter boundary without notifications or permission requests.
plugins { id("mesh.android.library") }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(project(":core:l10n"))
}
