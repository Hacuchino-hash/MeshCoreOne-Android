// AndroidOnly: WP-109 Dev-only executable JVM harness and fail-closed module-owned parity hooks.
plugins {
    id("mesh.jvm.library")
    application
}

dependencyLocking {
    lockFile.set(layout.projectDirectory.file("gradle/dependency-locks/meshcli.lockfile"))
}
buildscript {
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.STRICT)
        lockFile.set(project.layout.projectDirectory.file("gradle/dependency-locks/meshcli-buildscript.lockfile"))
    }
}

dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
}

application {
    mainClass.set("com.meshcoreone.android.tools.meshcli.MeshCli")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Xmx128m")
}

val repository = rootProject.projectDir.parentFile
val collector = layout.projectDirectory.file("verification/collect_evidence.py").asFile

tasks.named<Test>("test") {
    systemProperty("meshcli.runtimeClasspath", sourceSets.main.get().runtimeClasspath.asPath)
    systemProperty("meshcli.repository", repository.absolutePath)
}

distributions {
    main {
        contents {
            from(repository.resolve("android/app/src/main/assets/licenses")) {
                into("licenses")
                include("GPL-3.0.txt", "MeshCore-MIT.txt", "BouncyCastle-MIT.txt", "Apache-2.0.txt")
            }
            from(layout.projectDirectory.file("README.md"))
        }
    }
}

val verifyMeshCliCollector by tasks.registering(Exec::class) {
    group = "verification"
    description = "Execute nonzero, unskipped regressions for the complete raw-evidence/source-case reader."
    workingDir(repository)
    commandLine("python", collector.absolutePath, "self-test")
}

val verifyProtocolParity by tasks.registering(Exec::class) {
    group = "verification"
    description = "Require complete original MeshCore families, all baseline identities and actual executable CLI tests."
    dependsOn(":core:protocol:test", "test", verifyMeshCliCollector)
    notCompatibleWithConfigurationCache("Binds current Git inputs and complete executed JUnit to an explicit invocation")
    workingDir(repository)
    doFirst {
        val destination = providers.gradleProperty("meshCliEvidenceDirectory").orNull
            ?: layout.buildDirectory.dir("reports/wp109").get().asFile.absolutePath
        val invocation = providers.gradleProperty("meshCliInvocationFile").orNull
        val arguments = mutableListOf("python", collector.absolutePath, "collect", "--output", destination)
        if (invocation != null) arguments += listOf("--invocation", invocation)
        commandLine(arguments)
    }
}

tasks.named("check") { dependsOn(verifyProtocolParity) }
rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyProtocolParity) }
gradle.projectsEvaluated {
    rootProject.project(":core:protocol").tasks.named("test") { finalizedBy(verifyProtocolParity) }
}
