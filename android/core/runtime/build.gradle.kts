// AndroidOnly: WP-207 Pure-JVM runtime, owned dependency lock and complete source-family assertion hook.
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

plugins { id("mesh.jvm.library") }
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    testImplementation(libs.kotlinx.coroutines.test)
}

dependencyLocking {
    lockFile.set(layout.projectDirectory.file("gradle.lockfile"))
}

val repository = rootProject.projectDir.parentFile
tasks.withType<Test>().configureEach {
    systemProperty("repositoryDirectory", repository.absolutePath)
}

val resolveRuntimeDependencies by tasks.registering {
    group = "verification"
    description = "Resolve only the owning JVM runtime configurations and retain their actual component graph."
    notCompatibleWithConfigurationCache("Inspects only the owning runtime configuration model")
    doLast {
        val report = layout.buildDirectory.file("reports/wp207/dependency-graphs.tsv").get().asFile
        report.parentFile.mkdirs()
        report.writeText(buildString {
            appendLine("module\tconfiguration\tcomponent")
            for (configuration in project.configurations.filter { it.isCanBeResolved }.sortedBy { it.name }) {
                val graph = configuration.incoming.resolutionResult
                val unresolved = graph.allDependencies.filterIsInstance<UnresolvedDependencyResult>()
                if (unresolved.isNotEmpty()) {
                    throw GradleException(
                        "Unresolved runtime graph ${configuration.name}: " +
                            unresolved.joinToString { it.attempted.displayName },
                        unresolved.first().failure,
                    )
                }
                graph.allComponents.sortedBy { it.id.displayName }.forEach {
                    appendLine("${project.path}\t${configuration.name}\t${it.id.displayName}")
                }
            }
        })
    }
}

val verifyConnectionRuntimeTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Require all 154 frozen runtime declaration/parameter families and full unskipped JUnit evidence."
    dependsOn("test", resolveRuntimeDependencies)
    workingDir(repository)
    commandLine("python", repository.resolve("docs").resolve("android").resolve("evidence")
        .resolve("WP-207").resolve("collect_evidence.py").absolutePath)
}

val verifyRuntimeNativeIntegrationTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Require actual new runtime consumer cases and full unchanged native repository/preference suites."
    dependsOn(":core:data:testDebugUnitTest", ":core:datastore:testDebugUnitTest")
    workingDir(repository)
    commandLine("python", repository.resolve("docs").resolve("android").resolve("evidence")
        .resolve("WP-207").resolve("collect_native_evidence.py").absolutePath)
}

val verifyRuntimeEvidenceReaders by tasks.registering(Exec::class) {
    group = "verification"
    description = "Run positive and adversarial runtime raw-evidence reader cases."
    workingDir(repository)
    commandLine("python", "-B", "-m", "unittest", "discover", "-s",
        repository.resolve("docs").resolve("android").resolve("evidence").resolve("WP-207").absolutePath,
        "-p", "test_collect_evidence.py", "-v")
}

val printRuntimeFailureDiagnostics by tasks.registering(Exec::class) {
    group = "verification"
    description = "Print bounded actual raw module failures after verification; never change its result."
    workingDir(repository)
    commandLine("python", "-B", layout.projectDirectory.file("verification/print_failures.py").asFile.absolutePath)
    providers.gradleProperty("wp207EvidenceDirectory").orNull?.let { args("--output", it) }
}

gradle.projectsEvaluated {
    for (path in listOf(":core:data", ":core:datastore")) {
        rootProject.project(path).tasks.named("testDebugUnitTest") { finalizedBy(printRuntimeFailureDiagnostics) }
    }
    tasks.named("test") { finalizedBy(printRuntimeFailureDiagnostics) }
}

rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyConnectionRuntimeTests) }
rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyRuntimeNativeIntegrationTests, verifyRuntimeEvidenceReaders) }
rootProject.tasks.named("verifyScaffoldTests") { finalizedBy(printRuntimeFailureDiagnostics) }
tasks.named("check") { dependsOn(verifyConnectionRuntimeTests, verifyRuntimeNativeIntegrationTests, verifyRuntimeEvidenceReaders) }
