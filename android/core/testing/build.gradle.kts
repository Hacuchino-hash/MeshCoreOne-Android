// AndroidOnly: WP-002 Empty test-helper shell; forbidden in production dependency configurations.
plugins { id("mesh.android.library") }
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
}
