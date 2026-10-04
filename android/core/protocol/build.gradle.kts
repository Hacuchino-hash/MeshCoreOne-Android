// AndroidOnly: WP-101 Pure JVM protocol and coroutine transport contracts.
plugins { id("mesh.jvm.library") }

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
