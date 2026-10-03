// AndroidOnly: WP-002 Preference/secret module boundary without fake storage.
plugins { id("mesh.android.library") }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
}
