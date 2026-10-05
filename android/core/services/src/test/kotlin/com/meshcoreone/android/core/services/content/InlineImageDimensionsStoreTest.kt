// NOT a port of MC1Services/Tests/MC1ServicesTests/InlineImageDimensionsStoreTests.swift:
// port-manifest.json assigns that test file's ownership to WP-213 (InlineImageDimensionsStore
// production is WP-218's; its original Swift test file is not). This suite is independently
// authored against InlineImageDimensionsStore.kt's own contract and does not claim the source
// file's 9 original assertions as WP-218's 154-declaration credit. It exercises the same
// user-observable behaviors (save/aspect round-trip, non-positive-size rejection, corrupt/
// missing-file recovery, multi-key independence, multicast resolution events, cross-instance
// persistence) because that behavior is what InlineImageDimensionsStore.kt actually implements,
// not because the original assertions are being replayed.
package com.meshcoreone.android.core.services.content

import java.io.File
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InlineImageDimensionsStoreTest {

    private val createdDirs = mutableListOf<File>()

    private fun tempFile(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "InlineImageDimensionsStoreTest-${UUID.randomUUID()}")
        createdDirs += dir
        return File(dir, "InlineImageDimensions.json")
    }

    @AfterTest
    fun cleanup() {
        createdDirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun `save then aspect returns the expected ratio`() = runTest {
        val file = tempFile()
        val store = InlineImageDimensionsStore(file)

        store.save("https://example.com/a.png", width = 200.0, height = 100.0)

        assertEquals(2.0, store.aspect("https://example.com/a.png"))
    }

    @Test
    fun `save with zero width is rejected silently`() = runTest {
        val file = tempFile()
        val store = InlineImageDimensionsStore(file)

        store.save("https://example.com/zero-width.png", width = 0.0, height = 100.0)

        assertNull(store.aspect("https://example.com/zero-width.png"))
    }

    @Test
    fun `save with zero height is rejected silently`() = runTest {
        val file = tempFile()
        val store = InlineImageDimensionsStore(file)

        store.save("https://example.com/zero-height.png", width = 100.0, height = 0.0)

        assertNull(store.aspect("https://example.com/zero-height.png"))
    }

    @Test
    fun `save with negative width is rejected silently`() = runTest {
        val file = tempFile()
        val store = InlineImageDimensionsStore(file)

        store.save("https://example.com/negative-width.png", width = -10.0, height = 100.0)

        assertNull(store.aspect("https://example.com/negative-width.png"))
    }

    @Test
    fun `save with negative height is rejected silently`() = runTest {
        val file = tempFile()
        val store = InlineImageDimensionsStore(file)

        store.save("https://example.com/negative-height.png", width = 100.0, height = -10.0)

        assertNull(store.aspect("https://example.com/negative-height.png"))
    }

    @Test
    fun `init recovers from a corrupt file by starting empty`() {
        val file = tempFile()
        file.parentFile.mkdirs()
        file.writeText("not json", Charsets.UTF_8)

        val store = InlineImageDimensionsStore(file)

        assertNull(store.aspect("https://example.com/anything.png"))
    }

    @Test
    fun `init recovers from a single malformed entry by starting entirely empty`() {
        val file = tempFile()
        file.parentFile.mkdirs()
        // "fetchedAt" is a string, not a number: this single malformed entry must reject the
        // WHOLE parse (all-or-nothing), not just this one key.
        file.writeText(
            """{"https://example.com/a.png":{"aspect":2.0,"fetchedAt":"oops"}}""",
            Charsets.UTF_8,
        )

        val store = InlineImageDimensionsStore(file)

        assertNull(store.aspect("https://example.com/a.png"))
    }

    @Test
    fun `init on non-existent file yields empty store`() {
        val file = tempFile()

        val store = InlineImageDimensionsStore(file)

        assertNull(store.aspect("https://example.com/unknown.png"))
    }

    @Test
    fun `two saves are both readable via aspect`() = runTest {
        val file = tempFile()
        val store = InlineImageDimensionsStore(file)

        store.save("https://example.com/a.png", width = 400.0, height = 200.0)
        store.save("https://example.com/b.png", width = 100.0, height = 400.0)

        assertEquals(2.0, store.aspect("https://example.com/a.png"))
        assertEquals(0.25, store.aspect("https://example.com/b.png"))
    }

    @Test
    fun `resolutionUpdates emits the URL on save`() = runTest {
        val file = tempFile()
        val store = InlineImageDimensionsStore(file)

        val received = async { store.resolutionUpdates.first() }
        // Allow the collector above to register before saving, matching the source contract
        // that only events yielded after subscription are delivered.
        testScheduler.runCurrent()

        store.save("https://example.com/stream.png", width = 300.0, height = 150.0)

        assertEquals("https://example.com/stream.png", received.await())
    }

    @Test
    fun `resolutionUpdates delivers every event to every concurrent subscriber`() = runTest {
        val file = tempFile()
        val store = InlineImageDimensionsStore(file)

        val subscriberOne = async { store.resolutionUpdates.take(2).toList() }
        val subscriberTwo = async { store.resolutionUpdates.take(2).toList() }
        testScheduler.runCurrent()

        store.save("https://example.com/multicast-a.png", width = 200.0, height = 100.0)
        store.save("https://example.com/multicast-b.png", width = 300.0, height = 100.0)

        val expected = listOf(
            "https://example.com/multicast-a.png",
            "https://example.com/multicast-b.png",
        )
        assertEquals(expected, subscriberOne.await())
        assertEquals(expected, subscriberTwo.await())
    }

    @Test
    fun `round-trip recreated store reads previously persisted aspect`() = runTest {
        val file = tempFile()
        val writer = InlineImageDimensionsStore(file)
        writer.save("https://example.com/persisted.png", width = 600.0, height = 300.0)

        val reader = InlineImageDimensionsStore(file)

        assertEquals(2.0, reader.aspect("https://example.com/persisted.png"))
    }
}
