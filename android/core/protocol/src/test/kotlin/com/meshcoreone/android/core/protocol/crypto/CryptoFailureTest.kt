// AndroidOnly: WP-102 Typed malformed inputs, curve validity, source error ordering and immutable key-format boundaries.
package com.meshcoreone.android.core.protocol.crypto

import com.meshcoreone.android.core.protocol.bytes.Bytes
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CryptoFailureTest {
    @TestFactory
    fun `Every truncated channel payload fails before authentication or key validation`(): List<DynamicTest> =
        (0 until 18).map { length ->
            DynamicTest.dynamicTest("channel truncated length $length") {
                assertEquals(ChannelCrypto.DecryptResult.PayloadTooShort,
                    ChannelCrypto.decrypt(Bytes.EMPTY.paddedOrTruncated(length), Bytes.EMPTY))
            }
        }

    @TestFactory
    fun `Every truncated direct payload fails before key derivation`(): List<DynamicTest> =
        (0 until 20).map { length ->
            DynamicTest.dynamicTest("direct truncated length $length") {
                val payload = Bytes.EMPTY.paddedOrTruncated(length)
                assertEquals(DirectMessageCrypto.DecryptResult.InvalidPayload,
                    DirectMessageCrypto.decrypt(payload, Bytes.EMPTY, Bytes.EMPTY))
                assertNull(DirectMessageCrypto.extractTimestamp(payload, Bytes.EMPTY, Bytes.EMPTY))
            }
        }

    @TestFactory
    fun `Every AES misaligned length has a typed failure instead of provider padding or silent truncation`(): List<DynamicTest> =
        (1..33).filter { it % 16 != 0 }.map { length ->
            DynamicTest.dynamicTest("AES unaligned length $length") {
                val error = assertFailsWith<CryptoException.InvalidCiphertextLength> {
                    WireCrypto.decryptAes128Ecb(Bytes.EMPTY.paddedOrTruncated(length), CryptoFixtures.message("channel-normal").secret)
                }
                assertEquals(length, error.actual)
            }
        }

    @TestFactory
    fun `Every undersized AES secret fails explicitly`(): List<DynamicTest> =
        (0 until 16).map { length ->
            DynamicTest.dynamicTest("AES short key length $length") {
                val key = Bytes.EMPTY.paddedOrTruncated(length)
                for (operation in listOf(
                    { WireCrypto.encryptAes128EcbZeroPadded(Bytes.EMPTY, key) },
                    { WireCrypto.decryptAes128Ecb(Bytes.EMPTY, key) },
                )) assertEquals(length, assertFailsWith<CryptoException.SecretTooShort> { operation() }.actual)
            }
        }

    @TestFactory
    fun `X25519 and seed APIs reject every non-32-byte key with format metadata`(): List<DynamicTest> =
        listOf(0, 1, 16, 31, 33, 63, 64, 65, 127).map { length ->
            DynamicTest.dynamicTest("32-byte format rejected length $length") {
                val key = Bytes.EMPTY.paddedOrTruncated(length)
                val valid = CryptoFixtures.keys[0]
                val errors = listOf(
                    KeyFormat.X25519_PRIVATE to assertFailsWith<CryptoException.InvalidKeyLength> { X25519Crypto.publicKey(key) },
                    KeyFormat.X25519_PRIVATE to assertFailsWith<CryptoException.InvalidKeyLength> { X25519Crypto.sharedSecret(key, valid.xPublic) },
                    KeyFormat.X25519_PUBLIC to assertFailsWith<CryptoException.InvalidKeyLength> { X25519Crypto.sharedSecret(valid.xPrivate, key) },
                    KeyFormat.ED25519_SEED to assertFailsWith<CryptoException.InvalidKeyLength> { Ed25519Crypto.expandSeed(key) },
                    KeyFormat.ED25519_SEED to assertFailsWith<CryptoException.InvalidKeyLength> { Ed25519Crypto.publicKeyFromSeed(key) },
                    KeyFormat.ED25519_SEED to assertFailsWith<CryptoException.InvalidKeyLength> { Ed25519Crypto.signWithSeed(Bytes.EMPTY, key) },
                    KeyFormat.ED25519_PUBLIC to assertFailsWith<CryptoException.InvalidKeyLength> { Ed25519ToX25519.convertPublicKey(key) },
                )
                for ((format, error) in errors) {
                    assertEquals(format, error.format)
                    assertEquals(length, error.actual)
                }
                assertEquals(DirectMessageCrypto.DecryptResult.KeyError,
                    DirectMessageCrypto.decrypt(CryptoFixtures.message("direct-normal").packet, key, valid.xPublic))
                assertEquals(DirectMessageCrypto.DecryptResult.KeyError,
                    DirectMessageCrypto.decrypt(CryptoFixtures.message("direct-normal").packet, valid.xPrivate, key))
            }
        }

    @TestFactory
    fun `Firmware expanded APIs never accept a seed or arbitrary length as a 64-byte key`(): List<DynamicTest> =
        listOf(0, 1, 16, 31, 32, 33, 63, 65, 127).map { length ->
            DynamicTest.dynamicTest("64-byte format rejected length $length") {
                val key = Bytes.EMPTY.paddedOrTruncated(length)
                for (operation in listOf(
                    { Ed25519Crypto.validateExpandedPrivateKey(key) },
                    { Ed25519Crypto.publicKeyFromExpanded(key) },
                    { Ed25519Crypto.x25519PrivateKeyFromExpanded(key) },
                    { Ed25519Crypto.signWithExpanded(Bytes.EMPTY, key) },
                )) {
                    val error = assertFailsWith<CryptoException.InvalidKeyLength> { operation() }
                    assertEquals(KeyFormat.ED25519_EXPANDED_PRIVATE, error.format)
                    assertEquals(length, error.actual)
                }
            }
        }

    @TestFactory
    fun `Every firmware scalar clamp constraint is enforced without silently rewriting imported keys`(): List<DynamicTest> =
        listOf(0 to 1, 0 to 2, 0 to 4, 31 to 0x80, 31 to 0x40).map { (offset, mask) ->
            DynamicTest.dynamicTest("invalid scalar clamp offset $offset mask $mask") {
                val invalid = CryptoFixtures.keys[0].expanded.toByteArray()
                invalid[offset] = (invalid[offset].toInt() xor mask).toByte()
                val key = Bytes(invalid)
                for (operation in listOf(
                    { Ed25519Crypto.validateExpandedPrivateKey(key) },
                    { Ed25519Crypto.publicKeyFromExpanded(key) },
                    { Ed25519Crypto.x25519PrivateKeyFromExpanded(key) },
                    { Ed25519Crypto.signWithExpanded(Bytes.EMPTY, key) },
                )) assertFailsWith<CryptoException.InvalidExpandedPrivateKey> { operation() }
                assertEquals(Bytes(invalid), key)
            }
        }

    @TestFactory
    fun `Invalid Edwards points are rejected rather than emitting invented Montgomery keys`(): List<DynamicTest> =
        listOf(
            "small-order-zero" to Bytes.EMPTY.paddedOrTruncated(32),
            "identity" to Bytes.of(1).paddedOrTruncated(32),
            "non-prime-order-or-off-curve" to Bytes.of(2).paddedOrTruncated(32),
            "minus-one" to Bytes.fromHex("ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"),
            "noncanonical-p" to Bytes.fromHex("edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"),
            "noncanonical-p-plus-one" to Bytes.fromHex("eeffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"),
            "noncanonical-max" to Bytes(ByteArray(32) { 0xff.toByte() }),
        ).map { (id, key) ->
            DynamicTest.dynamicTest("invalid Ed25519 point $id") {
                assertFailsWith<CryptoException.InvalidEd25519PublicKey> { Ed25519ToX25519.convertPublicKey(key) }
                assertFailsWith<CryptoException.InvalidEd25519PublicKey> {
                    Ed25519Crypto.verify(Bytes.EMPTY, Bytes.EMPTY.paddedOrTruncated(64), key)
                }
            }
        }

    @TestFactory
    fun `Low-order X25519 peers cannot forge successful agreement or direct decryption`(): List<DynamicTest> =
        listOf(
            "zero" to Bytes.EMPTY.paddedOrTruncated(32),
            "one" to Bytes.of(1).paddedOrTruncated(32),
            "minus-one" to Bytes.fromHex("ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"),
            "p" to Bytes.fromHex("edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"),
            "p-plus-one" to Bytes.fromHex("eeffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"),
            "zero-with-masked-high-bit" to Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000080"),
        ).map { (id, peer) ->
            DynamicTest.dynamicTest("noncontributory X25519 $id") {
                val vector = CryptoFixtures.message("direct-normal")
                assertFailsWith<CryptoException.NonContributoryPublicKey> {
                    X25519Crypto.sharedSecret(requireNotNull(vector.privateKey), peer)
                }
                assertEquals(DirectMessageCrypto.DecryptResult.KeyError,
                    DirectMessageCrypto.decrypt(vector.packet, requireNotNull(vector.privateKey), peer))
                assertNull(DirectMessageCrypto.extractTimestamp(vector.packet, vector.privateKey, peer))
            }
        }

    @Test
    fun `Wrong MAC precedes block alignment failures while a valid MAC exposes the source decrypt failure`() {
        val channel = CryptoFixtures.message("channel-nonblock-17")
        val direct = CryptoFixtures.message("direct-nonblock-17")
        CryptoFixtures.assertChannel(channel)
        CryptoFixtures.assertDirect(direct)
        val badChannel = channel.packet.toByteArray()
        badChannel[0] = (badChannel[0].toInt() xor 1).toByte()
        assertEquals(ChannelCrypto.DecryptResult.HmacFailed, ChannelCrypto.decrypt(Bytes(badChannel), channel.secret))
        val badDirect = direct.packet.toByteArray()
        badDirect[2] = (badDirect[2].toInt() xor 1).toByte()
        assertEquals(DirectMessageCrypto.DecryptResult.MacMismatch,
            DirectMessageCrypto.decrypt(Bytes(badDirect), requireNotNull(direct.privateKey), requireNotNull(direct.publicKey)))
    }

    @Test
    fun `Ciphertext tampering and failures never produce extracted timestamps`() {
        val channel = CryptoFixtures.message("channel-normal")
        val direct = CryptoFixtures.message("direct-normal")
        val tamperedChannel = channel.packet.toByteArray()
        tamperedChannel[2] = (tamperedChannel[2].toInt() xor 0x80).toByte()
        assertEquals(ChannelCrypto.DecryptResult.HmacFailed, ChannelCrypto.decrypt(Bytes(tamperedChannel), channel.secret))
        val tamperedDirect = direct.packet.toByteArray()
        tamperedDirect[4] = (tamperedDirect[4].toInt() xor 0x80).toByte()
        assertEquals(DirectMessageCrypto.DecryptResult.MacMismatch,
            DirectMessageCrypto.decrypt(Bytes(tamperedDirect), requireNotNull(direct.privateKey), requireNotNull(direct.publicKey)))
        assertNull(DirectMessageCrypto.extractTimestamp(Bytes(tamperedDirect), direct.privateKey, direct.publicKey))
    }

    @Test
    fun `Invalid signature widths retain exact typed size metadata`() {
        for (length in listOf(0, 1, 32, 63, 65, 128)) {
            val error = assertFailsWith<CryptoException.InvalidSignatureLength> {
                Ed25519Crypto.verify(Bytes.EMPTY, Bytes.EMPTY.paddedOrTruncated(length), CryptoFixtures.keys[0].edPublic)
            }
            assertEquals(length, error.actual)
        }
    }
}
