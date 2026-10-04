// AndroidOnly: WP-204 Real DataStore/Keystore persistence and source-case verification.
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

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
val admittedConsumerConfigurations = listOf(
    "debugRuntimeClasspath", "releaseRuntimeClasspath",
    "debugUnitTestRuntimeClasspath", "debugAndroidTestRuntimeClasspath",
    "debugLintChecksClasspath", "releaseLintChecksClasspath",
    "debugUnitTestLintChecksClasspath", "debugAndroidTestLintChecksClasspath",
)

val resolvePreferenceDependencies by tasks.registering {
    group = "verification"
    description = "Resolve only owned DataStore graphs and the eight admitted app/data runtime/lint lock configurations."
    notCompatibleWithConfigurationCache("Inspects exactly the coordinator-admitted consumer configuration model")
    doLast {
        val output = layout.buildDirectory.file("reports/wp204/dependency-graphs.tsv").get().asFile
        output.parentFile.mkdirs()
        output.writeText(buildString {
            appendLine("module\tconfiguration\tcomponent")
            for (module in listOf(project, rootProject.project(":app"), rootProject.project(":core:data"))) {
                val configurations = if (module == project) {
                    module.configurations.filter { it.isCanBeResolved }.sortedBy { it.name }
                } else admittedConsumerConfigurations.map { name ->
                    module.configurations.getByName(name).also {
                        check(it.isCanBeResolved) { "Admitted configuration is not resolvable: ${module.path}:$name" }
                    }
                }
                for (configuration in configurations) {
                    val result = configuration.incoming.resolutionResult
                    val unresolved = result.allDependencies.filterIsInstance<UnresolvedDependencyResult>()
                    if (unresolved.isNotEmpty()) {
                        throw GradleException(
                            "Unresolved admitted graph ${module.path}:${configuration.name}: " +
                                unresolved.joinToString { it.attempted.displayName },
                            unresolved.first().failure,
                        )
                    }
                    for (component in result.allComponents.sortedBy { it.id.displayName }) {
                        appendLine("${module.path}\t${configuration.name}\t${component.id.displayName}")
                    }
                }
            }
        })
    }
}

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

val verifyPreferencePackaging by tasks.registering(Exec::class) {
    group = "verification"
    description = "Inspect actual APK backup exclusions, exact dependency notices and test/secret boundaries."
    dependsOn(":app:assembleDebug")
    workingDir(repository)
    commandLine(
        "python", repository.resolve("docs").resolve("android").resolve("evidence")
            .resolve("WP-204").resolve("inspect_packaging.py").absolutePath,
    )
}

rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyPreferenceTests) }
rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyPreferencePackaging) }
tasks.named("check") { dependsOn(verifyPreferenceTests, verifyPreferencePackaging) }
