// AndroidOnly: WP-305 Onboarding feature (welcome, just-in-time permissions, pairing, WiFi, region/preset, demo).
plugins { id("mesh.android.feature") }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
}

// Logic tests only (JUnit4 + kotlin-test are already locked); register them with the scaffold gate.
rootProject.tasks.named("verifyScaffoldTests") { dependsOn(":feature:onboarding:testDebugUnitTest") }
tasks.named("check") { dependsOn("testDebugUnitTest") }
