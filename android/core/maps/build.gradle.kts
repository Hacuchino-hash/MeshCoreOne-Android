// AndroidOnly: WP-312 Provider-neutral shared map contracts; provider admission remains human-gated.
plugins {
    id("mesh.android.library")
    id("mesh.android.compose")
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:l10n"))
    implementation(libs.kotlinx.coroutines.core)
}

rootProject.tasks.named("verifyScaffoldTests") { dependsOn(":core:maps:testDebugUnitTest") }
tasks.named("check") { dependsOn("testDebugUnitTest") }
