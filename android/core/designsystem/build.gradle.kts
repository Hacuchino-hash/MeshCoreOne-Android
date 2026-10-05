// AndroidOnly: WP-301 Native themes, actual preference consumer, pinned tests and module-owned verification.
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

plugins {
    id("mesh.android.library")
    id("mesh.android.compose")
    id("mesh.android.robolectric")
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:l10n"))
    implementation(project(":core:datastore"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.activity.compose)
}

dependencyLocking {
    lockFile.set(layout.projectDirectory.file("gradle.lockfile"))
}

val repository = rootProject.projectDir.parentFile
val converter = repository.resolve("tools").resolve("android-port").resolve("theme_convert.py")
val evidenceCollector = repository.resolve("docs").resolve("android").resolve("evidence")
    .resolve("WP-301").resolve("collect_evidence.py")

val themePlatformSdk37 = configurations.create("themePlatformSdk37") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
dependencies.add(themePlatformSdk37.name, "org.robolectric:android-all-instrumented:17-robolectric-15733970-i7")
val prepareThemePlatformSdks by tasks.registering(Sync::class) {
    from(configurations.named("testRobolectricSdk"))
    from(themePlatformSdk37)
    into(layout.buildDirectory.dir("theme-platform-sdks"))
}
class ThemePlatformSdkArguments(
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    val directory: Provider<Directory>,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("-Drobolectric.dependency.dir=${directory.get().asFile.absolutePath}")
}
tasks.withType<Test>().configureEach {
    dependsOn(prepareThemePlatformSdks)
    jvmArgumentProviders.add(ThemePlatformSdkArguments(layout.buildDirectory.dir("theme-platform-sdks")))
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
    systemProperty("repositoryDirectory", repository.absolutePath)
    systemProperty("themeArtifactDirectory", layout.buildDirectory.dir("reports/wp301/ui").get().asFile.absolutePath)
    forkEvery = 1
}

val verifyThemeConversion by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verify all frozen theme/color/icon/recovery inputs and nonzero fail-closed converter cases."
    workingDir(repository)
    commandLine("python", converter.absolutePath, "--check", "--self-test")
}
tasks.named("preBuild") { dependsOn(verifyThemeConversion) }

val verifyThemeTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Require all original theme families, parameter counts, current input blobs and complete nonzero raw JUnit."
    dependsOn("testDebugUnitTest")
    workingDir(repository)
    commandLine("python", evidenceCollector.absolutePath, "--check", "--self-test",
        "--junit", layout.buildDirectory.dir("test-results/testDebugUnitTest").get().asFile.absolutePath)
}
rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyThemeTests) }
tasks.named("check") { dependsOn(verifyThemeTests) }

val resolveThemeDependencies by tasks.registering {
    group = "verification"
    description = "Resolve only owned module configurations; no consumer lock writes."
    notCompatibleWithConfigurationCache("Reads the current owned Gradle configuration model")
    doLast {
        val output = layout.buildDirectory.file("reports/wp301/owned-dependency-graphs.tsv").get().asFile
        output.parentFile.mkdirs()
        output.writeText(buildString {
            appendLine("module\tconfiguration\tcomponent")
            configurations.filter { it.isCanBeResolved }.sortedBy { it.name }.forEach { configuration ->
                val resolution = configuration.incoming.resolutionResult
                val unresolved = resolution.allDependencies.filterIsInstance<UnresolvedDependencyResult>()
                if (unresolved.isNotEmpty()) throw GradleException(
                    "Owned theme graph is unresolved: ${configuration.name}", unresolved.first().failure,
                )
                resolution.allComponents.sortedBy { it.id.displayName }.forEach {
                    appendLine("${project.path}\t${configuration.name}\t${it.id.displayName}")
                }
            }
        })
    }
}

val inspectThemeConsumerGraphs by tasks.registering {
    group = "verification"
    description = "Read-only strict graph/missing-lock evidence for exactly ten affected consumers, never write-locks."
    notCompatibleWithConfigurationCache("Inspects exactly the proposed consumer configurations")
    doLast {
        check(!gradle.startParameter.isWriteDependencyLocks) { "Consumer inspection must never run with --write-locks" }
        val modules = listOf(":core:ui", ":core:maps", ":feature:onboarding", ":feature:chats", ":feature:nodes",
            ":feature:remotenodes", ":feature:map", ":feature:tools", ":feature:settings", ":platform:widgets")
        val names = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath", "debugUnitTestRuntimeClasspath",
            "debugAndroidTestRuntimeClasspath", "debugLintChecksClasspath", "releaseLintChecksClasspath",
            "debugUnitTestLintChecksClasspath", "debugAndroidTestLintChecksClasspath")
        val output = layout.buildDirectory.file("reports/wp301/consumer-dependency-graphs.tsv").get().asFile
        output.parentFile.mkdirs()
        var failed = false
        output.printWriter().use { writer ->
            writer.println("module\tconfiguration\tkind\tcomponent")
            for (module in modules) for (name in names) {
                val configuration = rootProject.project(module).configurations.getByName(name)
                val resolution = configuration.incoming.resolutionResult
                for (component in resolution.allComponents.sortedBy { it.id.displayName }) {
                    writer.println("$module\t$name\tselected\t${component.id.displayName}")
                }
                for (problem in resolution.allDependencies.filterIsInstance<UnresolvedDependencyResult>()) {
                    failed = true
                    writer.println("$module\t$name\tunresolved\t${problem.attempted.displayName}")
                    writer.println("$module\t$name\tfailure\t${problem.failure.message?.replace('\n', ' ')?.replace('\t', ' ')}")
                }
            }
        }
        if (failed) throw GradleException("Strict consumer graphs are BLOCKED; actual missing-lock evidence retained in ${output.name}")
    }
}
