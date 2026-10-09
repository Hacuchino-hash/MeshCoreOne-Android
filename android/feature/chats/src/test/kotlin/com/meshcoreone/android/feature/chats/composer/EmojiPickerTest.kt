// AndroidOnly: WP-308 native tests for the emoji picker logic (search, frequent-first, MRU, stale search guard).
package com.meshcoreone.android.feature.chats.composer

import com.meshcoreone.android.feature.chats.list.support.scenario
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test

class EmojiPickerTest {
    private class MemoryFrequent(var value: List<String> = emptyList()) : FrequentEmojiStore {
        override fun read() = value
        override fun write(emojis: List<String>) { value = emojis }
    }

    private val smile = EmojiEntry("1F600", "😀", "grinning face", listOf("grinning"), listOf("happy"))
    private val heart = EmojiEntry("2764", "❤️", "red heart", listOf("heart"), emptyList())
    private val flag = EmojiEntry("1F1EF", "🇯🇵", "flag: Japan", emptyList(), listOf("nation"))
    private val catalog = mapOf("people" to listOf(smile, heart), "flags" to listOf(flag), "unknown" to listOf(smile))

    @Test
    fun `categories follow the fixed order with frequent first and unknown groups skipped`() = scenario {
        val frequent = MemoryFrequent(listOf("❤️"))
        val holder = EmojiPickerStateHolder({ catalog }, frequent, scope)
        holder.load()
        assertEquals(listOf("frequent", "people", "flags"), holder.state.value.categories.map { it.id })
        assertEquals("frequent-0", holder.state.value.categories.first().emojis.single().id)
        assertEquals("people-1F600", holder.state.value.categories[1].emojis.first().id)
        assertEquals(EmojiPickerLoad.Loaded, holder.state.value.load)
    }

    @Test
    fun `search matches label shortcodes and tags and hides frequent`() = scenario {
        val holder = EmojiPickerStateHolder({ catalog }, MemoryFrequent(listOf("❤️")), scope)
        holder.load()
        holder.setQuery("HAPPY")
        assertEquals(listOf("people"), holder.state.value.categories.map { it.id })
        assertEquals(listOf("😀"), holder.state.value.categories.single().emojis.map { it.unicode })
        holder.setQuery("japan")
        assertEquals(listOf("flags"), holder.state.value.categories.map { it.id })
        holder.setQuery("zzz")
        assertTrue(holder.state.value.categories.isEmpty())
        holder.setQuery("")
        assertEquals(listOf("frequent", "people", "flags"), holder.state.value.categories.map { it.id })
    }

    @Test
    fun `a slow older search cannot overwrite a newer one`() = scenario {
        val gate = CompletableDeferred<Unit>()
        var first = true
        val holder = EmojiPickerStateHolder({ if (first) { first = false; gate.await() }; catalog }, MemoryFrequent(), scope)
        holder.setQuery("heart")
        holder.setQuery("japan")
        gate.complete(Unit)
        assertEquals(listOf("flags"), holder.state.value.categories.map { it.id })
    }

    @Test
    fun `catalog failure is reported and cancellation is not`() = scenario {
        val holder = EmojiPickerStateHolder({ error("no dataset") }, MemoryFrequent(), scope)
        holder.load()
        assertIs<EmojiPickerLoad.Failed>(holder.state.value.load)
        assertTrue(holder.state.value.categories.isEmpty())
    }

    @Test
    fun `frequent list is MRU de-duplicated and capped at 20 and recents at 6`() = scenario {
        val frequent = MemoryFrequent()
        val holder = EmojiPickerStateHolder({ catalog }, frequent, scope)
        (1..25).forEach { holder.markAsFrequentlyUsed("e$it") }
        assertEquals(20, frequent.value.size)
        assertEquals("e25", frequent.value.first())
        holder.markAsFrequentlyUsed("e10")
        assertEquals("e10", frequent.value.first())
        assertEquals(20, frequent.value.size)
        assertEquals(1, frequent.value.count { it == "e10" })
        var recent = RecentEmojis.DEFAULTS
        recent = RecentEmojis.record(recent, "🎉")
        assertEquals(listOf("🎉", "👍", "👎", "❤️", "😂", "😮"), recent)
        assertEquals("👎", RecentEmojis.record(recent, "👎").first())
        assertEquals(6, RecentEmojis.record(recent, "👎").size)
    }
}
