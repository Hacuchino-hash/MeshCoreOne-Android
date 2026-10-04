// AndroidOnly: WP-002 Neutral IDs only; domain contracts wait for WP-101/106/201.
plugins { id("mesh.jvm.library") }
dependencies {
    api(project(":core:model"))
    api(project(":core:protocol"))
}
