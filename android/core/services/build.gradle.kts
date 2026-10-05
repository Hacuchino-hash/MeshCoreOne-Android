// AndroidOnly: WP-002 Android-free service boundary; consumes contracts, never concrete runtime/data.
// AndroidOnly: WP-218 Pure-JVM content safety/location services; no java.net.http/java.awt/javax.imageio.
plugins { id("mesh.jvm.library") }
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    // WP-218 PROPOSED, not yet lock-admitted: coordinator confirmed kotlinx-serialization-json
    // 1.7.3 for InlineImageDimensionsStore's flat JSON persistence file (manual JsonElement DOM
    // usage only -- no kotlin("plugin.serialization") compiler plugin, no @Serializable). There
    // is no libs.kotlinx.serialization.json alias in the version catalog, so the exact coordinate
    // is used directly per instruction. This line is declared here for review but
    // android/gradle/dependency-locks/core-services.lockfile has NOT been regenerated for it --
    // see the scoped `resolveContentDependencies` task below and docs/android/evidence/WP-218/
    // run-summary.json's amendment_requested for the exact proposed write-lock command, pending
    // coordinator sign-off before execution.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

// WP-218 PROPOSED scoped lock-regeneration task: resolves ONLY the four classpaths the
// coordinator's narrow admission names -- compileClasspath/runtimeClasspath/
// testCompileClasspath/testRuntimeClasspath -- never every `isCanBeResolved` configuration this
// plugin exposes (that set also includes e.g. `*ScriptKotlinCompilerPluginClasspath`/lint/
// buildscript-adjacent configurations the coordinator's admission does not cover), and never a
// root-wide `--write-locks`/another module's lock. It fails loudly if any admitted configuration
// is absent or unresolvable (never a silent no-op on a missing name), and records the actual
// selected dependency graph -- resolved components plus any unresolved-dependency rows -- as a
// TSV per configuration under this module's own build/ output (gitignored), so the auxiliary
// generator has real resolved-component provenance to copy/hash instead of inferring it from the
// lockfile. The graph is written before the forced `.resolve()` call so it is retained even if
// that call then fails strict/verification resolution.
val contentDependencyGraphDirectory = layout.buildDirectory.dir("wp218/content-dependencies")

tasks.register("resolveContentDependencies") {
    group = "verification"
    description = "WP-218: resolves exactly core:services' compileClasspath/runtimeClasspath/" +
        "testCompileClasspath/testRuntimeClasspath, failing loudly on a missing/unresolvable " +
        "admitted configuration or an empty resolved graph, and writes the actual selected " +
        "dependency graph (plus unresolved-dependency rows) as a TSV per configuration so " +
        "--write-locks has real resolved-component provenance behind it."
    val admittedConfigurationNames = listOf(
        "compileClasspath",
        "runtimeClasspath",
        "testCompileClasspath",
        "testRuntimeClasspath",
    )
    val outputDirectory = contentDependencyGraphDirectory
    outputs.dir(outputDirectory)
    doLast {
        val directory = outputDirectory.get().asFile
        directory.mkdirs()
        admittedConfigurationNames.forEach { name ->
            val configuration = configurations.findByName(name)
                ?: throw GradleException(
                    "WP-218 resolveContentDependencies: admitted configuration '$name' does " +
                        "not exist in core:services; refusing to silently skip it.")
            check(configuration.isCanBeResolved) {
                "WP-218 resolveContentDependencies: admitted configuration '$name' is not " +
                    "resolvable in core:services."
            }
            val result = configuration.incoming.resolutionResult
            val rows = mutableListOf("component\tversion\tstatus")
            result.allComponents.forEach { component ->
                val version = component.moduleVersion?.version.orEmpty()
                rows += "${component.id.displayName}\t$version\tresolved"
            }
            result.allDependencies.forEach { dependency ->
                if (dependency is org.gradle.api.artifacts.result.UnresolvedDependencyResult) {
                    val message = (dependency.failure.message ?: "unresolved")
                        .replace("\t", " ").replace("\n", " ")
                    rows += "${dependency.attempted.displayName}\t\tunresolved: $message"
                }
            }
            directory.resolve("$name.tsv").writeText(rows.joinToString("\n") + "\n")
            if (rows.size == 1) {
                throw GradleException(
                    "WP-218 resolveContentDependencies: admitted configuration '$name' " +
                        "resolved an empty dependency graph; refusing to lock an empty result.")
            }
            // Forces eager artifact resolution (including strict dependency-verification) so a
            // genuinely broken/unresolvable component still fails this task, matching the
            // previous unconditional `.resolve()` behavior.
            configuration.resolve()
        }
    }
}
