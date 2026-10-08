// AndroidOnly: WP-002 Honest launcher/module assembly without a product AppContainer.
// AndroidOnly: WP-218 Sole producer support for WP-302's reviewed navigation configuration.
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.api.tasks.testing.Test

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
    implementation("androidx.navigation3:navigation3-runtime:1.1.0")
    implementation("androidx.navigation3:navigation3-ui:1.1.0")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.4.0")
    implementation("androidx.compose.material3.adaptive:adaptive:1.3.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.3.0")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.runtime)
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

class NavigationPlatformSdkArguments(
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    val directory: Provider<Directory>,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-Drobolectric.dependency.dir=${directory.get().asFile.absolutePath}")
}
tasks.withType<Test>().configureEach {
    dependsOn(":core:designsystem:prepareThemePlatformSdks")
    jvmArgumentProviders.add(NavigationPlatformSdkArguments(
        rootProject.project(":core:designsystem").layout.buildDirectory.dir("theme-platform-sdks"),
    ))
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}

tasks.register("resolveWp302NavigationDependencies") {
    group = "verification"
    description = "Resolve only sixteen reviewed App navigation assembly/lint/unit/instrumentation/compiler graphs."
    val names = listOf(
        "debugCompileClasspath", "debugRuntimeClasspath",
        "debugUnitTestCompileClasspath", "debugUnitTestRuntimeClasspath",
        "debugAndroidTestCompileClasspath", "debugAndroidTestRuntimeClasspath",
        "releaseCompileClasspath", "releaseRuntimeClasspath",
        "debugLintChecksClasspath", "debugUnitTestLintChecksClasspath",
        "debugAndroidTestLintChecksClasspath", "releaseLintChecksClasspath",
        "kotlinCompilerPluginClasspathDebug", "kotlinCompilerPluginClasspathDebugUnitTest",
        "kotlinCompilerPluginClasspathDebugAndroidTest", "kotlinCompilerPluginClasspathRelease",
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

val navigationRepository = rootProject.projectDir.parentFile
val navigationCollector = navigationRepository.resolve("docs/android/evidence/WP-302/collect_evidence.py")
val navigationBinding = layout.buildDirectory.file("reports/wp302/input-binding.json")
val prepareWp302NavigationInputs by tasks.registering(Exec::class) {
    group = "verification"
    description = "Bind real current navigation source and immutable inputs before App tests."
    workingDir(navigationRepository)
    commandLine("python", "-B", navigationCollector.absolutePath, "--bind-inputs",
        "--binding", navigationBinding.get().asFile.absolutePath)
}
val verifyWp302NavigationTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Require actual source50 navigation assertions and API31/37 rendered states."
    dependsOn("testDebugUnitTest")
    workingDir(navigationRepository)
    commandLine("python", "-B", navigationCollector.absolutePath, "--check", "--self-test",
        "--junit", layout.buildDirectory.dir("test-results/testDebugUnitTest").get().asFile.absolutePath,
        "--binding", navigationBinding.get().asFile.absolutePath)
}
val navigationSource = layout.projectDirectory.dir("src/main/kotlin/com/meshcoreone/android/app/navigation").asFile
if (navigationSource.exists()) {
    check(navigationSource.isDirectory && navigationCollector.isFile) {
        "Present Navigation source requires its real package and evidence collector."
    }
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
