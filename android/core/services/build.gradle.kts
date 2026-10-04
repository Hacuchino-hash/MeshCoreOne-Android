// AndroidOnly: WP-002 Android-free service boundary; consumes contracts, never concrete runtime/data.
plugins { id("mesh.jvm.library") }
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
}
