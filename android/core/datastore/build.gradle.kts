// AndroidOnly: WP-204 Real DataStore/Keystore persistence and source-case verification.
plugins {
    id("mesh.android.library")
    id("mesh.android.robolectric")
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

dependencyLocking {
    lockFile.set(layout.projectDirectory.file("gradle.lockfile"))
}

val repository = rootProject.projectDir.parentFile
tasks.withType<Test>().configureEach {
    systemProperty("repositoryDirectory", repository.absolutePath)
}

val verifyPreferenceTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Require every WP-204 original family and complete nonzero persistence JUnit evidence."
    dependsOn("testDebugUnitTest")
    workingDir(repository)
    commandLine(
        "python", repository.resolve("docs").resolve("android").resolve("evidence")
            .resolve("WP-204").resolve("collect_evidence.py").absolutePath,
    )
}

rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyPreferenceTests) }
tasks.named("check") { dependsOn(verifyPreferenceTests) }
