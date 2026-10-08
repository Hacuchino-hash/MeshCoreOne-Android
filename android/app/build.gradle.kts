// AndroidOnly: WP-002 Honest launcher/module assembly without a product AppContainer.
// AndroidOnly: WP-218 Narrow app/content native adapters for core:services content ports.
// AndroidOnly: WP-218 Exclusive producer support for WP-302's reviewed native navigation graph.
// Navigation coordinates are the WP-302 owner's reviewed proposal; incumbent catalog pins remain unchanged.
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

plugins {
    id("mesh.android.application")
    id("mesh.android.robolectric")
}

dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:model"))
    implementation(project(":core:contracts"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:data"))
    implementation(project(":core:ble"))
    implementation(project(":core:connectivity"))
    implementation(project(":core:runtime"))
    implementation(project(":core:services"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:maps"))
    implementation(project(":core:l10n"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:chats"))
    implementation(project(":feature:nodes"))
    implementation(project(":feature:remotenodes"))
    implementation(project(":feature:map"))
    implementation(project(":feature:tools"))
    implementation(project(":feature:settings"))
    implementation(project(":platform:notifications"))
    implementation(project(":platform:widgets"))
    implementation(project(":platform:shortcuts"))
    implementation(project(":platform:translation"))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.room.runtime)
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("androidx.navigation3:navigation3-runtime:1.1.0")
    implementation("androidx.navigation3:navigation3-ui:1.1.0")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.4.0")
    implementation("androidx.compose.material3.adaptive:adaptive:1.3.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.3.0")
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}

android {
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    sourceSets.getByName("test").java.srcDir(
        "src/androidTest/kotlin/com/meshcoreone/android/app/navigation/cases",
    )
}

class ContentPlatformSdkArguments(
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    val directory: Provider<Directory>,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-Drobolectric.dependency.dir=${directory.get().asFile.absolutePath}")
}
tasks.withType<Test>().configureEach {
    dependsOn(":core:designsystem:prepareThemePlatformSdks")
    jvmArgumentProviders.add(ContentPlatformSdkArguments(
        rootProject.project(":core:designsystem").layout.buildDirectory.dir("theme-platform-sdks"),
    ))
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}

tasks.register("resolveContentHttpDependencies") {
    group = "verification"
    description = "Resolve only the eleven admitted App content HTTP classpaths and retain their actual graphs."
    val names = listOf(
        "debugCompileClasspath", "debugRuntimeClasspath",
        "debugUnitTestCompileClasspath", "debugUnitTestRuntimeClasspath",
        "debugAndroidTestCompileClasspath", "releaseCompileClasspath", "releaseRuntimeClasspath",
        "debugLintChecksClasspath", "debugUnitTestLintChecksClasspath",
        "debugAndroidTestLintChecksClasspath", "releaseLintChecksClasspath",
    )
    doLast {
        val directory = layout.buildDirectory.dir("reports/wp218/http-dependencies").get().asFile
        directory.mkdirs()
        names.forEach { name ->
            val configuration = configurations.findByName(name)
                ?: throw GradleException("Missing admitted HTTP configuration: $name")
            check(configuration.isCanBeResolved) { "Unresolvable admitted HTTP configuration: $name" }
            val graph = configuration.incoming.resolutionResult
            val rows = graph.allComponents.map {
                "${it.id.displayName}\t${it.moduleVersion?.version.orEmpty()}"
            }.sorted()
            check(rows.isNotEmpty()) { "Empty admitted HTTP dependency graph: $name" }
            directory.resolve("$name.tsv").writeText("component\tversion\n" + rows.joinToString("\n") + "\n")
            configuration.incoming.artifactView {
                componentFilter { it is org.gradle.api.artifacts.component.ModuleComponentIdentifier }
            }.files.files
        }
    }
}

tasks.register("resolveWp302NavigationDependencies") {
    group = "verification"
    description = "Resolve only App navigation assembly/lint/unit/instrumentation classpaths and retain actual graphs."
    dependsOn("resolveContentHttpDependencies")
    val names = listOf(
        "debugAndroidTestRuntimeClasspath",
        "kotlinCompilerPluginClasspathDebug",
        "kotlinCompilerPluginClasspathDebugUnitTest",
        "kotlinCompilerPluginClasspathDebugAndroidTest",
        "kotlinCompilerPluginClasspathRelease",
    )
    doLast {
        val directory = layout.buildDirectory.dir("reports/wp302/navigation-dependencies").get().asFile
        directory.mkdirs()
        names.forEach { name ->
            val configuration = configurations.findByName(name)
                ?: throw GradleException("Missing admitted navigation configuration: $name")
            check(configuration.isCanBeResolved) { "Unresolvable admitted navigation configuration: $name" }
            val rows = configuration.incoming.resolutionResult.allComponents.map {
                "${it.id.displayName}\t${it.moduleVersion?.version.orEmpty()}"
            }.sorted()
            check(rows.isNotEmpty()) { "Empty admitted navigation dependency graph: $name" }
            directory.resolve("$name.tsv").writeText("component\tversion\n" + rows.joinToString("\n") + "\n")
            configuration.incoming.artifactView {
                componentFilter { it is org.gradle.api.artifacts.component.ModuleComponentIdentifier }
            }.files.files
        }
    }
}

val contentRepository = rootProject.projectDir.parentFile
val contentCollector = contentRepository.resolve("docs").resolve("android").resolve("evidence")
    .resolve("WP-218").resolve("collect_evidence.py")
val contentInvocation = providers.gradleProperty("meshCliInvocationFile")
val retainContentAppReports by tasks.registering(Exec::class) {
    group = "verification"
    description = "Retain complete raw App JUnit and immutable input bytes before verification, including failures."
    workingDir(contentRepository)
    val destination = contentInvocation.map {
        file(it).parentFile.resolve("wp218-native").resolve("app-raw").absolutePath
    }.getOrElse(layout.buildDirectory.dir("reports/wp218/app-raw").get().asFile.absolutePath)
    commandLine("python", "-B", contentCollector.absolutePath, "--retain-only", "app", "--output", destination)
    contentInvocation.orNull?.let { args("--invocation", it) }
    contentInvocation.orNull?.let {
        args("--pretest", file(it).parentFile.resolve("wp218-native").resolve("pretest-binding.json").absolutePath)
    }
}
tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest") {
        dependsOn(":core:services:prepareContentInvocation")
        finalizedBy(retainContentAppReports)
    }
}

val navigationCollector = contentRepository.resolve("docs/android/evidence/WP-302/collect_evidence.py")
val navigationBinding = layout.buildDirectory.file("reports/wp302/input-binding.json")
val prepareWp302NavigationInputs by tasks.registering(Exec::class) {
    group = "verification"
    description = "Bind the actual current navigation source, original families and immutable inputs before App tests."
    workingDir(contentRepository)
    commandLine("python", "-B", navigationCollector.absolutePath, "--bind-inputs",
        "--binding", navigationBinding.get().asFile.absolutePath)
}
val verifyWp302NavigationTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Require actual App navigation assertions, source50 families and native API31/37 rendered states."
    dependsOn("testDebugUnitTest")
    workingDir(contentRepository)
    commandLine("python", "-B", navigationCollector.absolutePath, "--check", "--self-test",
        "--junit", layout.buildDirectory.dir("test-results/testDebugUnitTest").get().asFile.absolutePath,
        "--binding", navigationBinding.get().asFile.absolutePath)
}

// The producer also runs before the separately owned navigation source is carried.
// Once that real source exists, binding/tests/verdict are mandatory, including missing-reader failures.
if (layout.projectDirectory.dir("src/main/kotlin/com/meshcoreone/android/app/navigation").asFile.isDirectory) {
    tasks.named<Test>("testDebugUnitTest") {
        dependsOn(prepareWp302NavigationInputs)
        systemProperty("navigationInputBinding",
            navigationBinding.get().asFile.resolveSibling("input-binding.properties").absolutePath)
        systemProperty("navigationArtifactDirectory",
            layout.buildDirectory.dir("reports/wp302/screens").get().asFile.absolutePath)
        outputs.upToDateWhen { false }
        outputs.doNotCacheIf("Navigation evidence requires fresh current-head execution markers") { true }
    }
    rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyWp302NavigationTests) }
    tasks.named("check") { dependsOn(verifyWp302NavigationTests) }
}

// WP-218 diagnostic-only addition: hosted CI invokes the root verify stage with Gradle's
// `--quiet` flag, which otherwise suppresses the failing test's identity and stack trace even on
// a failed Test task (confirmed by direct inspection of tools/android-port/controller/ci.py and
// the uploaded CI artifact, which contains no JUnit XML/report on a failed run). This mirrors the
// exact same local pattern already used by core/designsystem/build.gradle.kts for the same
// reason: no behavior change, no new dependency/config; `logger.error` always prints regardless
// of the configured Gradle log level, so this is the one reliable way to see which test failed.
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
                failureLogger.error("WP218_NATIVE_FAILURE|${testDescriptor.className}|${testDescriptor.name}")
                result.exceptions.forEach { failure ->
                    failureLogger.error("WP218 native failure stack", failure)
                }
            }
        }
    })
}
