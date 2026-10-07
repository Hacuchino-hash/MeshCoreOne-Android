// AndroidOnly: WP-002 Android-free service boundary; consumes contracts, never concrete runtime/data.
// AndroidOnly: WP-212 Registers this module's actual unit runner with the scaffold verify stage.
plugins { id("mesh.jvm.library") }
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
}

// core:services now has sources, so the controller requires its JUnit reports from the verify stage
// (module_junit.collect_module_tests: "actual unit runner for active module").
rootProject.tasks.named("verifyScaffoldTests") { dependsOn(tasks.named("test")) }
