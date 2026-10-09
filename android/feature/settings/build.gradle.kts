// AndroidOnly: WP-002 Settings entry shell; WP-318 adds appearance, backup, about and support logic (JVM tests: JUnit4 + kotlin-test already locked).
plugins { id("mesh.android.feature") }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
}

rootProject.tasks.named("verifyScaffoldTests") { dependsOn(":feature:settings:testDebugUnitTest") }
tasks.named("check") { dependsOn("testDebugUnitTest") }
