// AndroidOnly: WP-002 Translation module reservation; WP-406 permission is still required.
plugins { id("mesh.android.library") }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
}
