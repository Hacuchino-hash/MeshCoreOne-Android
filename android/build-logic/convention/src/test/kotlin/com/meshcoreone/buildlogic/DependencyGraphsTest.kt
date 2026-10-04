// AndroidOnly: WP-002 Real Gradle variant fixtures distinguish metadata locking from artifact selection.
package com.meshcoreone.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testfixtures.internal.ProjectBuilderImpl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir

class DependencyGraphsTest {
    @TempDir
    lateinit var directory: File
    private lateinit var fixtureRoot: Project

    @AfterEach
    fun releaseOwnedFixtureServices() {
        if (::fixtureRoot.isInitialized) ProjectBuilderImpl.stop(fixtureRoot)
    }

    @Test
    fun `secondary artifact variants do not make component locking ambiguous`() {
        val root = ProjectBuilder.builder().withName("fixture").withProjectDir(directory).build()
        fixtureRoot = root
        val producer = ProjectBuilder.builder().withName("producer").withParent(root).build()
        val consumer = ProjectBuilder.builder().withName("consumer").withParent(root).build()
        producer.pluginManager.apply("java-library")
        consumer.pluginManager.apply("java-library")
        producer.configurations.getByName("apiElements").outgoing.variants.apply {
            create("androidClasses") {
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "android-classes-jar")
                artifact(File(producer.projectDir, "classes.jar"))
            }
            create("androidManifest") {
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "android-manifest")
                artifact(File(producer.projectDir, "AndroidManifest.xml"))
            }
        }
        consumer.dependencies.add("implementation", consumer.dependencies.project(mapOf("path" to ":producer")))
        assertTrue(resolveDependencyGraph(consumer.configurations.getByName("compileClasspath")).any { it.contains("producer") })
    }

    @Test
    fun `unresolved graph components fail instead of producing partial locks`() {
        val root = ProjectBuilder.builder().withProjectDir(directory).build()
        fixtureRoot = root
        root.pluginManager.apply("java-library")
        root.dependencies.add("implementation", "example:missing:1.0")
        assertFailsWith<GradleException> { resolveDependencyGraph(root.configurations.getByName("compileClasspath")) }
    }
}
