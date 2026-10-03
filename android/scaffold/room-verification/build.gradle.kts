// AndroidOnly: WP-002 Unpackaged verification fixture proving the Room/KSP convention.
plugins {
    id("mesh.android.room")
    id("mesh.android.robolectric")
}
dependencies {
    testImplementation(libs.kotlinx.coroutines.test)
}
