// AndroidOnly: WP-217 native checks for demo-mode observation, the shared store binding, image seeding and I/O boundaries.
package com.meshcoreone.android.core.services.simulator

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.services.simulator.SimulatorTestSupport.native
import java.io.File
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class DemoModeNativeTest {
    @TestFactory
    fun demoModeTests(): List<DynamicTest> = listOf(
        native("demo mode state publishes every assignment") {
            val manager = DemoModeManager(InMemoryDemoModeDefaults())
            assertEquals(DemoModeState(isUnlocked = false, isEnabled = false), manager.state.value)
            manager.isEnabled = true
            assertEquals(DemoModeState(isUnlocked = false, isEnabled = true), manager.state.value)
            manager.unlock()
            assertEquals(DemoModeState(isUnlocked = true, isEnabled = true), manager.state.value)
            manager.isEnabled = false
            assertEquals(DemoModeState(isUnlocked = true, isEnabled = false), manager.state.value)
        },
        native("unlock publishes both flags in one state change") {
            val observed = mutableListOf<DemoModeState>()
            lateinit var manager: DemoModeManager
            val defaults = object : DemoModeDefaults {
                private val backing = InMemoryDemoModeDefaults()
                override fun bool(forKey: String): Boolean = backing.bool(forKey)
                override fun set(value: Boolean, forKey: String) {
                    observed += manager.state.value
                    backing.set(value, forKey)
                }
            }
            manager = DemoModeManager(defaults)
            manager.unlock()
            observed += manager.state.value
            val midpoint = DemoModeState(isUnlocked = true, isEnabled = false)
            assertTrue(midpoint !in observed, "observers must never see unlocked-but-disabled: $observed")
            assertEquals(DemoModeState(isUnlocked = true, isEnabled = true), observed.last())
            assertEquals(true, defaults.bool(DemoModeManager.IS_DEMO_MODE_UNLOCKED_KEY))
            assertEquals(true, defaults.bool(DemoModeManager.IS_DEMO_MODE_ENABLED_KEY))
        },
        native("demo mode toggles persist across manager instances") {
            val defaults = InMemoryDemoModeDefaults()
            DemoModeManager(defaults).unlock()
            val reopened = DemoModeManager(defaults)
            assertEquals(DemoModeState(isUnlocked = true, isEnabled = true), reopened.state.value)
            reopened.isEnabled = false
            assertEquals(DemoModeState(isUnlocked = true, isEnabled = false), DemoModeManager(defaults).state.value)
        },
        native("an unchanged assignment still writes through like didSet") {
            val defaults = InMemoryDemoModeDefaults()
            val manager = DemoModeManager(defaults)
            manager.isUnlocked = false
            assertTrue(defaults.contains(DemoModeManager.IS_DEMO_MODE_UNLOCKED_KEY))
            assertTrue(!defaults.contains(DemoModeManager.IS_DEMO_MODE_ENABLED_KEY))
        },
        native("shared standard defaults can only be bound once") {
            SimulatorTestSupport.installStandardDefaults()
            SimulatorTestSupport.installStandardDefaults()
            assertFailsWith<IllegalStateException> { DemoModeManager.installStandardDefaults(InMemoryDemoModeDefaults()) }
            try {
                DemoModeManager.shared.isEnabled = true
                assertEquals(true, SimulatorTestSupport.standardDefaults.bool(forKey = "isDemoModeEnabled"))
            } finally {
                DemoModeManager.shared.isEnabled = false
            }
        },
    )

    @TestFactory
    fun demoImageTests(): List<DynamicTest> = listOf(
        native("demo image decodes to the 1292-byte 120x80 PNG") {
            val image = MockDataProvider.demoImageData
            assertEquals(1292, image.size)
            assertEquals(Bytes.of(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), image.prefix(8))
            assertEquals(120, image.slice(16, 20).toByteArray().fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) })
            assertEquals(80, image.slice(20, 24).toByteArray().fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) })
        },
        native("inline image seeder stores the demo bytes under the direct image URL") {
            val cache = RecordingImageCache(decodes = true)
            DemoInlineImageSeeder.seed(cache)
            DemoInlineImageSeeder.seed(cache)
            val expected = StoredImage("decoded:1292", isGIF = false, data = MockDataProvider.demoImageData, url = URI("direct:" + MockDataProvider.inlineImageURL))
            assertEquals(listOf(expected, expected), cache.stored)
            assertEquals(listOf(URI(MockDataProvider.inlineImageURL), URI(MockDataProvider.inlineImageURL)), cache.classified)
        },
        native("inline image seeder skips an undecodable image") {
            val cache = RecordingImageCache(decodes = false)
            DemoInlineImageSeeder.seed(cache)
            assertEquals(emptyList(), cache.stored)
        },
    )

    @TestFactory
    fun boundaryTests(): List<DynamicTest> = listOf(
        native("simulator sources perform no socket, HTTP, file or Bluetooth I/O") {
            val directory = File("src/main/kotlin/com/meshcoreone/android/core/services/simulator")
            val sources = directory.listFiles { file -> file.extension == "kt" }.orEmpty()
            assertTrue(sources.size >= 18, "expected the simulator sources under ${directory.absolutePath}")
            val allowedNet = setOf("import java.net.URI", "import java.net.URISyntaxException")
            for (source in sources) {
                for (line in source.readLines()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import ")) {
                        val io = forbiddenImportPrefixes.any { trimmed.startsWith(it) } && trimmed !in allowedNet
                        assertTrue(!io, "${source.name} imports an I/O API: $trimmed")
                    }
                    assertTrue(forbiddenTokens.none { line.contains(it, ignoreCase = true) }, "${source.name}: $line")
                }
            }
        },
    )

    private data class StoredImage(val image: String, val isGIF: Boolean, val data: Bytes, val url: URI)

    private class RecordingImageCache(private val decodes: Boolean) : DemoInlineImageCache<String> {
        val stored = mutableListOf<StoredImage>()
        val classified = mutableListOf<URI>()

        override fun decodeImage(data: Bytes): String? = if (decodes) "decoded:${data.size}" else null

        override fun directImageURL(url: URI): URI {
            classified += url
            return URI("direct:$url")
        }

        override fun storeDecoded(image: String, isGIF: Boolean, data: Bytes, url: URI) {
            stored += StoredImage(image, isGIF, data, url)
        }
    }

    private companion object {
        val forbiddenImportPrefixes = listOf(
            "import java.net.", "import javax.net", "import java.nio.channels", "import java.io.", "import java.nio.file",
            "import okhttp", "import io.ktor", "import kotlinx.coroutines.Dispatchers",
        )
        val forbiddenTokens = listOf("Socket", "HttpURLConnection", "openConnection", "Bluetooth", "ProcessBuilder", "Runtime.getRuntime")
    }
}
