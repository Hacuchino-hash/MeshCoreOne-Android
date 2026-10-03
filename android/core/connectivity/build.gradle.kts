// AndroidOnly: WP-002 Platform connection shell; association/FGS behavior is not implemented.
plugins { id("mesh.android.library") }
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(project(":core:ble"))
}
