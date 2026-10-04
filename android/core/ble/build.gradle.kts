// AndroidOnly: WP-205 Native GATT transport; permissions and process ownership belong to the host.
plugins {
    id("mesh.android.library")
    id("mesh.android.robolectric")
}
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    testImplementation(project(":core:testing"))
}

rootProject.tasks.named("verifyScaffoldTests") { dependsOn(":core:ble:testDebugUnitTest") }
tasks.named("check") { dependsOn("testDebugUnitTest") }
