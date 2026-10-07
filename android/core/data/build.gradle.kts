// AndroidOnly: WP-202 Admitted Room repository dependencies and complete native assertion hook.
import java.io.File
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:runtime"))
    testImplementation(project(":core:services"))
}
dependencyLocking {
    lockFile.set(layout.projectDirectory.file("gradle.lockfile"))
}

tasks.withType<Test>().configureEach {
    systemProperty("wp203.interop.output", layout.buildDirectory.dir("reports/wp203/interop").get().asFile.absolutePath)
    providers.gradleProperty("wp203InteropInputDir").orNull?.let { systemProperty("wp203.interop.input", it) }
}

val repository = rootProject.projectDir.parentFile
val resolvePersistenceDependencies by tasks.registering {
    group = "verification"
    description = "Resolve every owned data configuration strictly and retain the actual component graph."
    notCompatibleWithConfigurationCache("Inspects only the owning data module configuration model")
    val report = layout.buildDirectory.file("reports/wp202/dependency-graphs.tsv")
    outputs.file(report)
    outputs.upToDateWhen { false }
    doLast {
        check(project.path == ":core:data") { "Persistence dependency resolution is restricted to its owner" }
        val resolved = buildString {
            appendLine("module\tconfiguration\tcomponent")
            for (configuration in project.configurations.filter { it.isCanBeResolved }.sortedBy { it.name }) {
                val result = configuration.incoming.resolutionResult
                val unresolved = result.allDependencies.filterIsInstance<UnresolvedDependencyResult>()
                if (unresolved.isNotEmpty()) {
                    throw GradleException(
                        "Unresolved owned graph ${project.path}:${configuration.name}: " +
                            unresolved.joinToString { it.attempted.displayName },
                        unresolved.first().failure,
                    )
                }
                for (component in result.allComponents.sortedBy { it.id.displayName }) {
                    appendLine("${project.path}\t${configuration.name}\t${component.id.displayName}")
                }
            }
        }
        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText(resolved)
        }
    }
}

val verifyPersistenceRepositoryTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Require every WP-202 original disposition and nonzero, unskipped actual Room JUnit evidence."
    dependsOn("testDebugUnitTest", resolvePersistenceDependencies)
    workingDir(repository)
    commandLine("python", repository.resolve("docs").resolve("android").resolve("evidence")
        .resolve("WP-202").resolve("collect_evidence.py").absolutePath)
}

rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyPersistenceRepositoryTests) }
tasks.named("check") { dependsOn(verifyPersistenceRepositoryTests) }

val verifyBackupTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Require all WP-203 source families and nonzero, unskipped real codec/Room backup assertions."
    dependsOn("testDebugUnitTest")
    workingDir(repository)
    commandLine("python", repository.resolve("docs").resolve("android").resolve("evidence")
        .resolve("WP-203").resolve("collect_evidence.py").absolutePath)
}

rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyBackupTests) }
tasks.named("check") { dependsOn(verifyBackupTests) }

// AndroidOnly: WP-208 Exact owning assertions and raw-first finalizers over both actual test runners.
fun messagingRawCaptureRequired(
    forwardedInvocation: Boolean,
    verifierSelected: Boolean,
    servicesRunnerSelected: Boolean,
    directlyRequested: Boolean,
): Boolean = forwardedInvocation || verifierSelected || servicesRunnerSelected || directlyRequested

fun messagingRawCaptureSelected(rawTaskName: String): Boolean = messagingRawCaptureRequired(
    providers.gradleProperty("meshCliInvocationFile").isPresent ||
        providers.gradleProperty("wp208LocalEvidenceDirectory").isPresent,
    gradle.taskGraph.hasTask(":core:data:verifyMessagingTests"),
    gradle.taskGraph.hasTask(":core:services:test"),
    gradle.startParameter.taskNames.any { it.substringAfterLast(':') == rawTaskName },
)

fun messagingEvidenceCommand(captureOnly: Boolean, completion: String): List<String> {
    val invocation = providers.gradleProperty("meshCliInvocationFile").orNull?.let(::File)
    val localDirectory = providers.gradleProperty("wp208LocalEvidenceDirectory").orNull?.let(::File)
    check(invocation == null || localDirectory == null) { "Local messaging cannot borrow hosted execution identity" }
    val localHead = providers.gradleProperty("wp208LocalExpectedHead").orNull
    val evidence = if (localDirectory != null) {
        check(localDirectory.isAbsolute && localHead?.matches(Regex("[0-9a-f]{40}")) == true) {
            "WP-208 local evidence requires an absolute private directory and exact committed HEAD"
        }
        localDirectory
    } else {
        val actual = invocation
            ?: throw GradleException("WP-208 requires the actual forwarded meshCliInvocationFile; no guessed execution binding")
        check(actual.isAbsolute && localHead == null) { "WP-208 requires an absolute actual invocation, not a worker override" }
        File(actual.parentFile, "wp208-native")
    }
    return buildList {
        add("python")
        add("-B")
        add(repository.resolve("docs").resolve("android").resolve("evidence").resolve("WP-208")
            .resolve("collect_evidence.py").absolutePath)
        if (captureOnly) add("--capture-only")
        addAll(listOf("--output", File(evidence, completion).absolutePath))
        if (localDirectory != null) {
            addAll(listOf("--local", "--expected-head", checkNotNull(localHead)))
        } else {
            addAll(listOf("--invocation", checkNotNull(invocation).absolutePath))
        }
    }
}

val retainMessagingServicesRawEvidence by tasks.registering(Exec::class) {
    group = "verification"
    description = "Retain full services/data XML and actual execution inputs after the services runner, even on failure."
    workingDir(repository)
    outputs.upToDateWhen { false }
    notCompatibleWithConfigurationCache("Inspects actual messaging verification task selection")
    onlyIf("WP-208 capture requires its verification graph, a forwarded invocation or a direct capture request") {
        messagingRawCaptureSelected(name)
    }
    doFirst { commandLine(messagingEvidenceCommand(true, "raw/services-completion")) }
}

val retainMessagingRoomRawEvidence by tasks.registering(Exec::class) {
    group = "verification"
    description = "Retain full services/data XML and actual execution inputs after the Room runner, even on failure."
    workingDir(repository)
    outputs.upToDateWhen { false }
    notCompatibleWithConfigurationCache("Inspects actual messaging verification task selection")
    onlyIf("WP-208 capture requires its verification graph, a forwarded invocation or a direct capture request") {
        messagingRawCaptureSelected(name)
    }
    doFirst { commandLine(messagingEvidenceCommand(true, "raw/room-completion")) }
}

val verifyMessagingTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Require all WP-208 original/native identities, actual Room consumers and exact Linux run binding."
    dependsOn(":core:services:test", "testDebugUnitTest")
    workingDir(repository)
    outputs.upToDateWhen { false }
    doFirst { commandLine(messagingEvidenceCommand(false, "validated")) }
}

val verifyMessagingEvidenceReaders by tasks.registering(Exec::class) {
    group = "verification"
    description = "Execute positive and adversarial source/raw-evidence reader assertions with nonzero discovery."
    workingDir(repository)
    doFirst {
        check(!messagingRawCaptureRequired(false, false, false, false))
        check(messagingRawCaptureRequired(true, false, false, false))
        check(messagingRawCaptureRequired(false, true, false, false))
        check(messagingRawCaptureRequired(false, false, true, false))
        check(messagingRawCaptureRequired(false, false, false, true))
        check(messagingRawCaptureRequired(true, true, true, true))
        logger.lifecycle("WP208_HOOK_SCOPE_ASSERTIONS|6")
    }
    commandLine("python", "-B", "-m", "unittest", "discover", "-s",
        repository.resolve("docs").resolve("android").resolve("evidence").resolve("WP-208").absolutePath,
        "-p", "test_collect_evidence.py", "-v")
}

tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest") {
        finalizedBy(retainMessagingRoomRawEvidence)
        doFirst { logger.error("WP208_RUNNER_START|$path") }
        val progressLogger = logger
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) = Unit
            override fun afterSuite(suite: TestDescriptor, result: TestResult) = Unit
            override fun beforeTest(testDescriptor: TestDescriptor) {
                progressLogger.error("WP208_ROOM_CASE_START|${testDescriptor.className}|${testDescriptor.name}")
            }
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                progressLogger.error("WP208_ROOM_CASE_END|${testDescriptor.className}|${testDescriptor.name}|${result.resultType}")
                result.exceptions.forEach { progressLogger.error("WP208 Room failure", it) }
            }
        })
    }
}
rootProject.project(":core:services").tasks.withType<Test>().configureEach {
    if (name == "test") {
        finalizedBy(retainMessagingServicesRawEvidence)
        doFirst { logger.error("WP208_RUNNER_START|$path") }
        val progressLogger = logger
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) = Unit
            override fun afterSuite(suite: TestDescriptor, result: TestResult) = Unit
            override fun beforeTest(testDescriptor: TestDescriptor) {
                progressLogger.error("WP208_SERVICES_CASE_START|${testDescriptor.className}|${testDescriptor.name}")
            }
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                progressLogger.error("WP208_SERVICES_CASE_END|${testDescriptor.className}|${testDescriptor.name}|${result.resultType}")
            }
        })
    }
}
rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyMessagingTests, verifyMessagingEvidenceReaders) }
tasks.named("check") { dependsOn(verifyMessagingTests, verifyMessagingEvidenceReaders) }
