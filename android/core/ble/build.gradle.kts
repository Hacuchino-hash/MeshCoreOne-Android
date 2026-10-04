// AndroidOnly: WP-002 BLE boundary only; no hardware library, connection or permission request.
plugins { id("mesh.android.library") }
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
}
