// AndroidOnly: WP-310 Native input-rule checks (the Swift counterparts are private view code).
package com.meshcoreone.android.feature.chats.channels

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class ChannelInputTest {
    @Test
    fun `name is capped to 31 UTF-8 bytes on a character boundary`() {
        assertEquals("a".repeat(31), ChannelInput.truncatedName("a".repeat(40)))
        // 15 two-byte characters = 30 bytes; a 16th would make 32, so it is dropped whole.
        assertEquals("é".repeat(15), ChannelInput.truncatedName("é".repeat(20)))
        assertEquals("short", ChannelInput.truncatedName("short"))
    }

    @Test
    fun `secret input is uppercased and stripped to hex digits`() {
        assertEquals("AB12CD", ChannelInput.sanitizedSecretHex("ab 12-cd!zz"))
    }

    @Test
    fun `secret validity requires exactly 32 hex digits`() {
        assertTrue(ChannelInput.isValidSecretHex("0123456789abcdef0123456789ABCDEF"))
        assertTrue(ChannelInput.isValidSecretHex("0123 4567 89ab cdef 0123 4567 89AB CDEF"))
        assertFalse(ChannelInput.isValidSecretHex("0123456789abcdef0123456789ABCDE"))
        assertFalse(ChannelInput.isValidSecretHex("0123456789abcdef0123456789ABCDEFF"))
        assertFalse(ChannelInput.isValidSecretHex("0123456789abcdef0123456789ABCDEG"))
        assertNull(ChannelInput.secretFromHex("zz"))
    }

    @Test
    fun `hashtag input is lowercased filtered and loses leading hyphens`() {
        assertEquals("my-channel1", ChannelInput.sanitizedHashtagName("--My-Channel1"))
        assertEquals("abc", ChannelInput.sanitizedHashtagName("-A!b@C"))
        assertEquals("", ChannelInput.sanitizedHashtagName("---"))
        assertTrue(ChannelInput.isValidHashtagName("a-b"))
        assertFalse(ChannelInput.isValidHashtagName("-ab"))
        assertFalse(ChannelInput.isValidHashtagName(""))
    }

    @Test
    fun `slots skip used ones and slot zero`() {
        assertEquals(listOf<UByte>(2u, 4u), ChannelInput.availableSlots(5, setOf(0u, 1u, 3u)))
        assertEquals(emptyList(), ChannelInput.availableSlots(1, emptySet()))
        assertEquals(emptyList(), ChannelInput.availableSlots(0, emptySet()))
        assertEquals(2u.toUByte(), ChannelInput.defaultSlot(listOf(2u, 4u)))
        assertEquals(1u.toUByte(), ChannelInput.defaultSlot(emptyList()))
    }
}
