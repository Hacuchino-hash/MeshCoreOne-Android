// AndroidOnly: WP-004 Executable test helpers; forbidden in production dependency configurations.
plugins { id("mesh.android.library") }
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    api(libs.kotlinx.coroutines.test)
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("test").resources.srcDir("fixtures")
}
