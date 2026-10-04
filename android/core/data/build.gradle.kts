// AndroidOnly: WP-002 Repository module shell; no backup codec or destructive fallback.
plugins { id("mesh.android.library") }
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
}
