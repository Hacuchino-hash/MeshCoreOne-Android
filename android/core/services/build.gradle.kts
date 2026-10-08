// AndroidOnly: WP-002 Android-free service boundary; consumes contracts, never concrete runtime/data.
// AndroidOnly: WP-218 Pure-JVM content safety/location services; no java.net.http/java.awt/javax.imageio.
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import java.io.ByteArrayOutputStream

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

// AndroidOnly: WP-218 diagnostic-only raw-failure printer, mirroring the precedented
// android/core/runtime/verification/print_failures.py (WP-207) pattern, scoped only to
// core:services. Hosted CI's --quiet Gradle invocation renders test progress on a single
// rich-console line via carriage returns; stripped of terminal escape sequences afterwards, only
// the last redraw of each overwritten line survives, so the TestListener above (whose
// logger.error(...) calls are routed through that same live console) can silently lose all but
// the final one or two of several genuine failures -- confirmed in CI run 37403142503, where 8
// core:services test failures were reported in aggregate but only 2 ever reached the log.
//
// A first fix attempt ran this script as a plain Exec task (finalizedBy'd after the Test task
// completes) relying on its subprocess stdout being a fresh, separate stream -- but CI run
// 37404888269 showed the SAME loss pattern even there: the script reported 9 raw failed/skipped
// nodes, yet only 3 full blocks survived in both `gh run view --log` and the official untruncated
// per-step log file. Root cause: Gradle's `Exec` task redirects child-process stdout through
// Gradle's OWN logging/console pipeline by default (not a raw, independent terminal stream), so
// it remains subject to the same rich-console redraw loss as the live TestListener output.
//
// The reliable channel, confirmed intact byte-for-byte in every run so far (including this one),
// is Gradle's own end-of-build "* What went wrong" / "Execution failed for task ..." exception
// summary -- printed once, after all live rich-console redrawing has stopped, and never observed
// truncated or clobbered. This task therefore captures the script's stdout/stderr into an
// in-memory buffer (bypassing Gradle's console during execution) and, if the script discovered
// any raw failed/skipped nodes, throws a GradleException carrying the FULL captured text as its
// message, so the complete list reaches that reliable channel. It never changes `:core:services:
// test`'s own result (finalizedBy does not affect the finalized task's outcome); it only makes
// this task itself fail loudly, which is purely diagnostic plumbing, not a feature/behavior gate.
val repository = rootProject.projectDir.parentFile
val contentCollector = repository.resolve("docs").resolve("android").resolve("evidence")
    .resolve("WP-218").resolve("collect_evidence.py")
val contentInvocation = providers.gradleProperty("meshCliInvocationFile")
val contentEvidenceDirectory = contentInvocation.map {
    file(it).parentFile.resolve("wp218-native").absolutePath
}
val contentPretestBinding = contentEvidenceDirectory.map { file(it).resolve("pretest-binding.json").absolutePath }
val prepareContentInvocation by tasks.registering(Exec::class) {
    group = "verification"
    description = "Bind the actual Linux local/hosted candidate and compiled inputs BEFORE native tests."
    workingDir(repository)
    commandLine("python", "-B", contentCollector.absolutePath, "--prepare-binding",
        "--invocation", contentInvocation.getOrElse(""), "--output", contentPretestBinding.getOrElse(""))
}
tasks.named<Test>("test") {
    dependsOn(prepareContentInvocation)
    outputs.upToDateWhen { false }
    outputs.doNotCacheIf("Content evidence requires fresh current-head Services execution") { true }
}

val retainContentServiceReports by tasks.registering(Exec::class) {
    group = "verification"
    description = "Retain all raw Services reports and input bytes before parsing, including failed runs."
    workingDir(repository)
    commandLine("python", "-B", contentCollector.absolutePath, "--retain-only", "services",
        "--output", contentEvidenceDirectory.map { file(it).resolve("services-raw").absolutePath }
            .getOrElse(layout.buildDirectory.dir("reports/wp218/services-raw").get().asFile.absolutePath))
    contentInvocation.orNull?.let { args("--invocation", it) }
    contentPretestBinding.orNull?.let { args("--pretest", it) }
}
tasks.named("test") { finalizedBy(retainContentServiceReports) }

val verifyContentEvidenceReader by tasks.registering(Exec::class) {
    group = "verification"
    description = "Run nonzero content-reader raw-retention, source154 and immutable-binding regressions."
    workingDir(repository)
    commandLine("python", "-B", contentCollector.absolutePath, "--self-test")
}

val verifyContentTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Require real Services/App tests and every frozen original content family; missing behavior fails."
    dependsOn("test", ":app:testDebugUnitTest", verifyContentEvidenceReader)
    workingDir(repository)
    commandLine("python", "-B", contentCollector.absolutePath,
        "--output", contentEvidenceDirectory.map { file(it).resolve("source154").absolutePath }.getOrElse(""))
    contentInvocation.orNull?.let { args("--invocation", it) }
    contentPretestBinding.orNull?.let { args("--pretest", it) }
}
rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyContentTests) }
tasks.named("check") { dependsOn(verifyContentTests) }

val printServicesFailureDiagnostics by tasks.registering(Exec::class) {
    group = "verification"
    description = "Print bounded actual raw core:services failures after test; never changes test's result."
    dependsOn(retainContentServiceReports)
    workingDir(repository)
    commandLine("python", "-B", repository.resolve("docs").resolve("android").resolve("evidence")
        .resolve("WP-218").resolve("print_failures.py").absolutePath)
    providers.gradleProperty("wp218EvidenceDirectory").orNull?.let { args("--output", it) }
    val captured = ByteArrayOutputStream()
    standardOutput = captured
    errorOutput = captured
    isIgnoreExitValue = true
    doLast {
        val text = captured.toString(Charsets.UTF_8)
        val reportedNodes = Regex("""Actual failed/skipped core:services JUnit nodes printed: (\d+)""")
            .find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (reportedNodes == null || executionResult.get().exitValue != 0) {
            throw GradleException(
                "WP218 printServicesFailureDiagnostics: diagnostic printer itself failed or " +
                    "produced an unrecognized summary; raw captured output follows:\n$text")
        }
        if (reportedNodes > 0) {
            throw GradleException(
                "WP218_SERVICES_RAW_FAILURES ($reportedNodes actual raw failed/skipped " +
                    "core:services JUnit nodes; diagnostic-only, does not change " +
                    "core:services:test's own already-reported result):\n$text")
        }
    }
}
tasks.named("test") { finalizedBy(printServicesFailureDiagnostics) }

// WP-218 diagnostic-only test-failure logging: mirrors the precedented pattern already used by
// core/designsystem (WP-301) and android/app (WP-218). Hosted CI invokes Gradle with --quiet, so
// without this, a failing `:core:services:test` run prints zero per-test failure identity or
// stack trace to the CI log -- only an aggregate "N failed" line and a report path that is never
// uploaded as an artifact. `testLogging.quiet { ... }` targets exactly the --quiet log level, and
// `logger.error(...)` in the TestListener is unconditionally visible regardless of configured log
// level. No test behavior/assertions change; this only makes already-failing tests legible.
tasks.withType<Test>().configureEach {
    testLogging.quiet {
        events(TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    val failureLogger = logger
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) = Unit
        override fun afterSuite(suite: TestDescriptor, result: TestResult) = Unit
        override fun beforeTest(testDescriptor: TestDescriptor) = Unit
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            if (result.resultType == TestResult.ResultType.FAILURE) {
                failureLogger.error("WP218_SERVICES_FAILURE|${testDescriptor.className}|${testDescriptor.name}")
                result.exceptions.forEach { failure ->
                    failureLogger.error("WP218 services native failure stack", failure)
                }
            }
        }
    })
}
