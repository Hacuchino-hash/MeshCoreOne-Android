// AndroidOnly: WP-304 Shared native UI, real preference claims and owned fail-closed verification.
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.process.CommandLineArgumentProvider

plugins {
    id("mesh.android.library")
    id("mesh.android.compose")
    id("mesh.android.robolectric")
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:l10n"))
    implementation(project(":core:datastore"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(project(":core:runtime"))
    testImplementation(project(":core:ble"))
    testImplementation(project(":core:services"))
    testImplementation(project(":core:connectivity"))
    // The existing UI seed is 1.8.2; do not silently replace it with the catalog's 1.13.0.
    testImplementation("androidx.activity:activity-compose:1.8.2")
}

dependencyLocking {
    lockFile.set(layout.projectDirectory.file("gradle.lockfile"))
}

val repository = rootProject.projectDir.parentFile
val sharedUiEvidence = repository.resolve("docs").resolve("android").resolve("evidence").resolve("WP-304")
val sourceReader = sharedUiEvidence.resolve("collect_evidence.py")
val frozenRootUiLock = rootProject.layout.projectDirectory.file("gradle/dependency-locks/core-ui.lockfile")
val seedText = frozenRootUiLock.asFile.readText()
val seedByConfiguration = linkedMapOf<String, MutableList<String>>()
seedText.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
    val parts = line.split("=", limit = 2)
    check(parts.size == 2) { "Malformed frozen UI lock input" }
    if (parts[0] != "empty") for (name in parts[1].split(",")) {
        seedByConfiguration.getOrPut(name) { mutableListOf() }.add(parts[0])
    }
}
configurations.configureEach {
    seedByConfiguration[name]?.let { incumbent -> resolutionStrategy.force(*incumbent.toTypedArray()) }
}
val admittedUnitCompileAlignment = linkedMapOf(
    "androidx.core:core" to "1.16.0",
    "androidx.core:core-ktx" to "1.16.0",
    "androidx.lifecycle:lifecycle-livedata-core" to "2.9.4",
    "androidx.lifecycle:lifecycle-viewmodel" to "2.9.4",
    "androidx.lifecycle:lifecycle-viewmodel-android" to "2.9.4",
    "androidx.lifecycle:lifecycle-viewmodel-ktx" to "2.9.4",
    "androidx.lifecycle:lifecycle-viewmodel-savedstate" to "2.9.4",
).map { (artifact, version) ->
    val source = seedByConfiguration["debugUnitTestRuntimeClasspath"].orEmpty()
        .filter { it.substringBeforeLast(":") == artifact }
    check(source.size == 1 && source.single() == "$artifact:$version") {
        "Admitted unit-compile alignment lacks its exact unique incumbent runtime seed: $artifact"
    }
    source.single()
}
configurations.configureEach {
    if (name == "debugUnitTestCompileClasspath") {
        resolutionStrategy.force(*admittedUnitCompileAlignment.toTypedArray())
    }
}

val sharedUiPlatformSdk37 = configurations.create("sharedUiPlatformSdk37") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
dependencies.add(sharedUiPlatformSdk37.name, "org.robolectric:android-all-instrumented:17-robolectric-15733970-i7")
val prepareSharedUiPlatformSdks by tasks.registering(Sync::class) {
    from(configurations.named("testRobolectricSdk"))
    from(sharedUiPlatformSdk37)
    into(layout.buildDirectory.dir("shared-ui-platform-sdks"))
}
class SharedUiPlatformArguments(
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    val directory: Provider<Directory>,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-Drobolectric.dependency.dir=${directory.get().asFile.absolutePath}")
}

tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest") dependsOn(":core:runtime:test", ":core:ble:testDebugUnitTest",
        ":core:services:test", ":core:connectivity:testDebugUnitTest")
    dependsOn(prepareSharedUiPlatformSdks)
    jvmArgumentProviders.add(SharedUiPlatformArguments(layout.buildDirectory.dir("shared-ui-platform-sdks")))
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
    systemProperty("repositoryDirectory", repository.absolutePath)
    systemProperty("sharedUiArtifactDirectory", layout.buildDirectory.dir("reports/wp304/ui").get().asFile.absolutePath)
    forkEvery = 1
    testLogging.quiet {
        events(TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

val verifySharedUiInputs by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verify all 88 frozen inputs, complete 130/158 source accounting and owned reader regressions."
    workingDir(repository)
    commandLine("python", sourceReader.absolutePath, "--static", "--self-test")
}
tasks.named("preBuild") { dependsOn(verifySharedUiInputs) }

val verifySharedUiTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Require actual nonzero raw JUnit, exact source-family/parameter assertions and native PNG evidence."
    dependsOn("testDebugUnitTest")
    workingDir(repository)
    commandLine("python", sourceReader.absolutePath, "--check", "--self-test",
        "--junit", layout.buildDirectory.dir("test-results/testDebugUnitTest").get().asFile.absolutePath,
        "--output", layout.buildDirectory.dir("reports/wp304/verified").get().asFile.absolutePath)
}
rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifySharedUiTests) }
tasks.named("check") { dependsOn(verifySharedUiTests) }

val resolveSharedUiDependencies by tasks.registering {
    group = "verification"
    description = "Resolve only owned UI configurations, preserving every frozen incumbent version; no ROOT or consumer lock writes."
    notCompatibleWithConfigurationCache("Inspects exactly the current owned module configurations")
    doLast {
        val output = layout.buildDirectory.file("reports/wp304/dependency-graphs.tsv").get().asFile
        output.parentFile.mkdirs()
        val ownedConfigurations = configurations.filter { it.isCanBeResolved }.sortedBy { it.name }
        layout.buildDirectory.file("reports/wp304/resolution-configurations.txt").get().asFile
            .writeText(ownedConfigurations.joinToString("\n", postfix = "\n") { it.name })
        var failed = false
        output.printWriter().use { writer ->
            writer.println("module\tconfiguration\tkind\tcomponent")
            ownedConfigurations.forEach { configuration ->
                val graph = configuration.incoming.resolutionResult
                graph.allComponents.sortedBy { it.id.displayName }.forEach {
                    writer.println("${project.path}\t${configuration.name}\tselected\t${it.id.displayName}")
                }
                graph.allDependencies.filterIsInstance<UnresolvedDependencyResult>().forEach {
                    failed = true
                    writer.println("${project.path}\t${configuration.name}\tunresolved\t${it.attempted.displayName}")
                    writer.println("${project.path}\t${configuration.name}\tfailure\t${it.failure.message?.replace('\n', ' ')?.replace('\t', ' ')}")
                }
            }
        }
        val alignment = layout.buildDirectory.file("reports/wp304/unit-compile-alignment.tsv").get().asFile
        val compileGraph = configurations.getByName("debugUnitTestCompileClasspath").incoming.resolutionResult
        alignment.printWriter().use { writer ->
            writer.println("artifact\tsourceConfiguration\tsourceCoordinate\tconfiguration\trequested\tselected")
            for (dependency in compileGraph.allDependencies.filterIsInstance<ResolvedDependencyResult>()) {
                val requested = dependency.requested as? ModuleComponentSelector ?: continue
                val artifact = "${requested.group}:${requested.module}"
                val source = admittedUnitCompileAlignment.singleOrNull { it.substringBeforeLast(":") == artifact } ?: continue
                val selected = dependency.selected.id as? ModuleComponentIdentifier
                    ?: throw GradleException("Admitted compile alignment selected a non-module component")
                writer.println("$artifact\tdebugUnitTestRuntimeClasspath\t$source\tdebugUnitTestCompileClasspath\t${requested.displayName}\t${selected.displayName}")
            }
        }
        if (failed) throw GradleException("Owned shared UI resolution failed; complete graph retained at ${output.name}")
    }
}
