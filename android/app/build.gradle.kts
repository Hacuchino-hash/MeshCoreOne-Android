// AndroidOnly: WP-002 Honest launcher/module assembly without a product AppContainer.
// AndroidOnly: WP-218 Narrow app/content native adapters for core:services content ports.
// kotlinx-coroutines-core/test are already transitively resolved here (core-jvm via
// core:protocol's `api(libs.kotlinx.coroutines.core)`; coroutines-test already locked on
// debugUnitTest* configs via androidx.compose.ui.test.junit4) - no new explicit declaration
// or app.lockfile delta is needed for the app/content adapters added in this increment.
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

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
