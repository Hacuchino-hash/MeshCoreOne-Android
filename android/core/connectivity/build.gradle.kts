// AndroidOnly: WP-206 Platform connectivity (CDM, bonding, permissions, connectedDevice FGS, presence, LAN binding).
plugins { id("mesh.android.library") }
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(project(":core:ble"))
    // Test-only: ported source cases drive the real process runtime (same pattern as core:data).
    testImplementation(project(":core:runtime"))
}

rootProject.tasks.named("verifyScaffoldTests") { dependsOn(":core:connectivity:testDebugUnitTest") }
tasks.named("check") { dependsOn("testDebugUnitTest") }
