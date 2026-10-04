// AndroidOnly: WP-002 Widget/tile module shell without platform registration or fake radio status.
plugins { id("mesh.android.library") }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:l10n"))
}
