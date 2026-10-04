// AndroidOnly: WP-002 Dev-only JVM harness boundary; no pretend working CLI entry point.
plugins { id("mesh.jvm.library") }
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
}
