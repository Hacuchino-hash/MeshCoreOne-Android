// AndroidOnly: WP-201 Immutable application values and source-derived domain contract assertions.
plugins { id("mesh.jvm.library") }
dependencies {
    api(project(":core:protocol"))
    testImplementation(project(":core:contracts"))
    testImplementation(libs.kotlinx.coroutines.test)
}

dependencyLocking {
    lockFile.set(layout.projectDirectory.file("gradle.lockfile"))
}

tasks.test {
    inputs.dir(rootProject.layout.projectDirectory.dir("core/testing/fixtures/reference-codec"))
    systemProperty("referenceCodecFixtures", rootProject.layout.projectDirectory.dir("core/testing/fixtures/reference-codec").asFile.absolutePath)
}

rootProject.tasks.named("verifyScaffoldTests") { dependsOn(":core:model:test") }
