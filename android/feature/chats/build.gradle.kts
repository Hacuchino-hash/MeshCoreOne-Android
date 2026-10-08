// AndroidOnly: WP-002 Chats entry shell.
// AndroidOnly: WP-306 Chat list logic + Compose UI; JVM logic tests only (JUnit4 + kotlin-test are already locked).
plugins { id("mesh.android.feature") }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
}

rootProject.tasks.named("verifyScaffoldTests") { dependsOn(":feature:chats:testDebugUnitTest") }
tasks.named("check") { dependsOn("testDebugUnitTest") }
