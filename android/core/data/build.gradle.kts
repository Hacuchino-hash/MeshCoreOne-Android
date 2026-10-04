// AndroidOnly: WP-202 Admitted Room repository dependencies and complete native assertion hook.
plugins {
    id("mesh.android.library")
    id("mesh.android.robolectric")
}
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
dependencyLocking {
    lockFile.set(layout.projectDirectory.file("gradle.lockfile"))
}

val repository = rootProject.projectDir.parentFile
val verifyPersistenceRepositoryTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Require every WP-202 original disposition and nonzero, unskipped actual Room JUnit evidence."
    dependsOn("testDebugUnitTest")
    workingDir(repository)
    commandLine("python", repository.resolve("docs").resolve("android").resolve("evidence")
        .resolve("WP-202").resolve("collect_evidence.py").absolutePath)
}

rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyPersistenceRepositoryTests) }
tasks.named("check") { dependsOn(verifyPersistenceRepositoryTests) }
