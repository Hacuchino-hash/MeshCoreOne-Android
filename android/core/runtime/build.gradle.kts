// AndroidOnly: WP-002 Android-free lifecycle boundary; no concrete service graph or ready flag.
plugins { id("mesh.jvm.library") }
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
}
