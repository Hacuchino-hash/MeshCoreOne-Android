// AndroidOnly: WP-201 Initial production Room schema and real SDK31 SQLite assertions.
plugins {
    id("mesh.android.room")
    id("mesh.android.robolectric")
}
dependencies {
    implementation(project(":core:model"))
    testImplementation(project(":core:contracts"))
    testImplementation(libs.kotlinx.coroutines.test)
}
dependencyLocking {
    lockFile.set(layout.projectDirectory.file("gradle.lockfile"))
}
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.layout.projectDirectory.dir("core/testing/fixtures/reference-codec"))
    systemProperty("referenceCodecFixtures", rootProject.layout.projectDirectory.dir("core/testing/fixtures/reference-codec").asFile.absolutePath)
    systemProperty("roomSchemaDirectory", layout.projectDirectory.dir("schemas").asFile.absolutePath)
}

val repository = rootProject.projectDir.parentFile
val verifyDomainRoomTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Require every WP-201 source disposition, nonzero native JUnit and actual Room v1 schema."
    dependsOn("testDebugUnitTest", ":core:model:test", ":core:contracts:test")
    workingDir(repository)
    commandLine("python", repository.resolve("docs").resolve("android").resolve("evidence")
        .resolve("WP-201").resolve("collect_evidence.py").absolutePath)
}

rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyDomainRoomTests) }
tasks.named("check") { dependsOn(verifyDomainRoomTests) }
