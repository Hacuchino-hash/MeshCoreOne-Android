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
// root-wide `--write-locks`/another module's lock.
tasks.register("resolveContentDependencies") {
    group = "verification"
    description = "WP-218: resolves exactly core:services' compileClasspath/runtimeClasspath/" +
        "testCompileClasspath/testRuntimeClasspath so the kotlinx-serialization-json 1.7.3 " +
        "addition can be locked via --write-locks scoped to this module only."
    val admittedConfigurationNames = setOf(
        "compileClasspath",
        "runtimeClasspath",
        "testCompileClasspath",
        "testRuntimeClasspath",
    )
    doLast {
        configurations
            .matching { it.isCanBeResolved && it.name in admittedConfigurationNames }
            .forEach { it.resolve() }
    }
}
