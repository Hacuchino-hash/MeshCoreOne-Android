// AndroidOnly: WP-002 Executable graph, lock resolution, schema and nonzero-suite checks.
package com.meshcoreone.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class ScaffoldRootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        check(this == rootProject) { "Scaffold root convention belongs at the root only" }
        tasks.register("validateModuleGraph") {
            group = "verification"
            description = "Inspect actual production configurations, artifacts and JVM sources against modules.md."
            notCompatibleWithConfigurationCache("Inspects the fully configured Gradle project dependency model")
            doLast {
                verifyLocalCatalogBookkeeping(layout.projectDirectory.file("settings-gradle.lockfile").asFile)
                val modules = subprojects.filter { it.path in allowedEdges || it.buildFile.isFile }
                    .map { inspectModule(it, resolveJvmArtifacts = true) }
                val violations = graphViolations(modules, completeInventory = true)
                if (violations.isNotEmpty()) throw GradleException(violations.joinToString("\n"))
                writeGraphReport(layout.buildDirectory.file("reports/scaffold/module-graph.tsv").get().asFile, modules)
                logger.lifecycle("Module graph verified: ${modules.size} modules; no forbidden production edges or Android JVM leakage")
            }
        }
        tasks.register("resolveScaffoldDependencies") {
            group = "build setup"
            description = "Resolve configured component graphs for reviewed locks; consumed artifact views supply checksums."
            notCompatibleWithConfigurationCache("Resolves the configured project dependency model")
            doLast {
                allprojects.sortedBy { it.path }.forEach { module ->
                    module.configurations.filter { it.isCanBeResolved }.sortedBy { it.name }.forEach {
                        resolveDependencyGraph(it)
                    }
                }
            }
        }
        tasks.register("runtimeDependencyInventory") {
            group = "verification"
            description = "Record exact linked-runtime POM license inputs; missing declarations block admission."
            notCompatibleWithConfigurationCache("Resolves current linked artifacts and declared license POMs")
            doLast {
                generateRuntimeDependencyInventory(
                    rootProject,
                    layout.buildDirectory.file("reports/scaffold/runtime-dependencies.tsv").get().asFile,
                )
            }
        }
        tasks.register("verifyScaffoldTests") {
            group = "verification"
            description = "Require real nonzero, unskipped assertions in all four scaffold suites."
            notCompatibleWithConfigurationCache("Reads current reports from the four configured scaffold suites")
            dependsOn(
                ":core:contracts:test",
                ":core:services:test",
                ":app:testDebugUnitTest",
                ":scaffold:room-verification:testDebugUnitTest",
                gradle.includedBuild("build-logic").task(":convention:test"),
            )
            doLast {
                val reports = linkedMapOf(
                    "build-logic" to "build-logic/convention/build/test-results/test",
                    "contracts" to "core/contracts/build/test-results/test",
                    "services" to "core/services/build/test-results/test",
                    "app" to "app/build/test-results/testDebugUnitTest",
                    "room-verification" to "scaffold/room-verification/build/test-results/testDebugUnitTest",
                )
                val result = layout.buildDirectory.file("reports/scaffold/test-discovery.tsv").get().asFile
                result.parentFile.mkdirs()
                result.writeText(buildString {
                    appendLine("suite\tdiscovered\tpassed\tfailed\terrors\tskipped")
                    reports.forEach { (suite, path) ->
                        val evidence = readTestEvidence(layout.projectDirectory.dir(path).asFile)
                        appendLine("$suite\t${evidence.discovered}\t${evidence.passed}\t${evidence.failed}\t${evidence.errors}\t${evidence.skipped}")
                        logger.lifecycle("$suite: $evidence")
                    }
                })
            }
        }
        tasks.register("verifyRoomSchema") {
            group = "verification"
            description = "Require a real Room/KSP-exported verification-fixture schema, not a product schema."
            notCompatibleWithConfigurationCache("Inspects the current fixture schema export")
            dependsOn(":scaffold:room-verification:assembleDebug")
            doLast {
                val schema = layout.projectDirectory.file(
                    "scaffold/room-verification/schemas/com.meshcoreone.android.scaffold.roomverification.ScaffoldDatabase/1.json",
                ).asFile
                verifyScaffoldSchema(schema)
                logger.lifecycle("Room/KSP fixture schema exported; no production schema/migration/backup acceptance implied")
            }
        }
        tasks.register("lintScaffold") {
            group = "verification"
            description = "Lint every Android scaffold module."
        }
        gradle.projectsEvaluated {
            tasks.named("lintScaffold") {
                dependsOn(subprojects.filter { it.plugins.hasPlugin("com.android.library") || it.plugins.hasPlugin("com.android.application") }
                    .map { "${it.path}:lintDebug" })
            }
        }
        tasks.register("checkScaffold") {
            group = "verification"
            description = "Local scaffold assertions only; not a WP completion or protected-gate receipt."
            dependsOn("validateModuleGraph", "verifyScaffoldTests", "verifyRoomSchema", "runtimeDependencyInventory", "lintScaffold")
        }
    }
}
