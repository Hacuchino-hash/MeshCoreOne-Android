// AndroidOnly: WP-102 Independent encryption/decryption bytes for every original and boundary message input.
package com.meshcoreone.android.core.protocol.crypto

import com.meshcoreone.android.core.protocol.bytes.Bytes
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndependentMessageVectorTest {
    @TestFactory
    fun `Every independent message vector has its declared decryption outcome`(): List<DynamicTest> =
        CryptoFixtures.messages.map { vector ->
            DynamicTest.dynamicTest("decrypt ${vector.id}") {
                if (vector.kind == "channel") CryptoFixtures.assertChannel(vector) else CryptoFixtures.assertDirect(vector)
            }
        }

    @TestFactory
    fun `AES framing and full-key HMAC match independent bytes rather than candidate round trips`(): List<DynamicTest> =
        CryptoFixtures.messages.filter { it.plaintext != null }.map { vector ->
            DynamicTest.dynamicTest("wire bytes ${vector.id}") {
                val offset = if (vector.kind == "channel") ChannelCrypto.macSize else DirectMessageCrypto.headerSize + DirectMessageCrypto.macSize
                val ciphertext = vector.packet.slice(offset, vector.packet.size)
                assertEquals(ciphertext, WireCrypto.encryptAes128EcbZeroPadded(requireNotNull(vector.plaintext), vector.secret))
                assertEquals(vector.packet.slice(offset - 2, offset), WireCrypto.truncatedHmacSha256(ciphertext, vector.secret))
                assertTrue(WireCrypto.authenticate(ciphertext, vector.secret, vector.packet.slice(offset - 2, offset)))
                val decrypted = WireCrypto.decryptAes128Ecb(ciphertext, vector.secret)
                assertEquals(vector.plaintext, decrypted.prefix(requireNotNull(vector.plaintext).size))
                assertTrue(decrypted.slice(vector.plaintext.size, decrypted.size).all { it == 0.toUByte() })
                assertEquals(((vector.plaintext.size + 15) / 16) * 16, ciphertext.size)
            }
        }

    @Test
    fun `Authenticated malformed direct UTF8 keeps the timestamp and does not invent replacement text`() {
        val vector = CryptoFixtures.message("direct-utf8-surrogate")
        val result = assertIs<DirectMessageCrypto.DecryptResult.Success>(DirectMessageCrypto.decrypt(
            vector.packet, requireNotNull(vector.privateKey), requireNotNull(vector.publicKey),
        ))
        assertNull(result.text)
        assertEquals(UInt.MAX_VALUE, result.timestamp)
        assertEquals(vector.timestamp, DirectMessageCrypto.extractTimestamp(
            vector.packet, vector.privateKey, vector.publicKey,
        ))
    }

    @Test
    fun `DM header hashes are deliberately outside the source authentication scope`() {
        val vector = CryptoFixtures.message("direct-normal")
        val altered = vector.packet.toByteArray()
        altered[0] = 0
        altered[1] = 0xff.toByte()
        assertEquals(DirectMessageCrypto.decrypt(vector.packet, requireNotNull(vector.privateKey), requireNotNull(vector.publicKey)),
            DirectMessageCrypto.decrypt(Bytes(altered), vector.privateKey, vector.publicKey))
    }

    @Test
    fun `Success diagnostics never print decrypted text`() {
        val channel = assertIs<ChannelCrypto.DecryptResult.Success>(
            ChannelCrypto.decrypt(CryptoFixtures.message("channel-normal").packet, CryptoFixtures.message("channel-normal").secret),
        )
        val directVector = CryptoFixtures.message("direct-normal")
        val direct = assertIs<DirectMessageCrypto.DecryptResult.Success>(DirectMessageCrypto.decrypt(
            directVector.packet, requireNotNull(directVector.privateKey), requireNotNull(directVector.publicKey),
        ))
        assertFalse(channel.toString().contains(channel.text))
        assertFalse(direct.toString().contains(requireNotNull(direct.text)))
    }
}
