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

fun Test.observeActualFailures(module: String) {
    val testLogger = logger
    addTestListener(object : org.gradle.api.tasks.testing.TestListener {
        override fun beforeSuite(suite: org.gradle.api.tasks.testing.TestDescriptor) = Unit
        override fun afterSuite(suite: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) = Unit
        override fun beforeTest(test: org.gradle.api.tasks.testing.TestDescriptor) = Unit
        override fun afterTest(test: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
            if (result.resultType == org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE) {
                testLogger.error("Actual $module test failed: ${test.className} :: ${test.name}")
                result.exceptions.forEach { testLogger.error(it.stackTraceToString()) }
            }
        }
    })
}

tasks.named<Test>("test") {
    systemProperty("meshcli.runtimeClasspath", sourceSets.main.get().runtimeClasspath.asPath)
    systemProperty("meshcli.repository", repository.absolutePath)
    observeActualFailures("meshcli")
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

tasks.named("check") { dependsOn(":core:protocol:test", "test") }
rootProject.tasks.named("verifyScaffoldTests") { dependsOn(":core:protocol:test", "${project.path}:test") }
gradle.projectsEvaluated {
    rootProject.project(":core:protocol").tasks.named<Test>("test") {
        observeActualFailures("protocol")
    }
}
