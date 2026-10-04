// AndroidOnly: WP-102 RFC4231, FIPS197 and NIST SP800-38A vectors plus wire-specific padding boundaries.
package com.meshcoreone.android.core.protocol.crypto

import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.sha256
import java.security.Security
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WireCryptoTest {
    private data class HmacVector(val key: Bytes, val data: Bytes, val expected: Bytes)

    private val hmacVectors = listOf(
        HmacVector(Bytes(ByteArray(20) { 0x0b }), Bytes.utf8("Hi There"),
            Bytes.fromHex("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7")),
        HmacVector(Bytes.utf8("Jefe"), Bytes.utf8("what do ya want for nothing?"),
            Bytes.fromHex("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843")),
        HmacVector(Bytes(ByteArray(20) { 0xaa.toByte() }), Bytes(ByteArray(50) { 0xdd.toByte() }),
            Bytes.fromHex("773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe")),
        HmacVector(Bytes(ByteArray(25) { (it + 1).toByte() }), Bytes(ByteArray(50) { 0xcd.toByte() }),
            Bytes.fromHex("82558a389a443c0ea4cc819899f2083a85f0faa3e578f8077a2e3ff46729665b")),
        HmacVector(Bytes(ByteArray(20) { 0x0c }), Bytes.utf8("Test With Truncation"),
            Bytes.fromHex("a3b6167473100ee06e0c796c2955552b")),
        HmacVector(Bytes(ByteArray(131) { 0xaa.toByte() }), Bytes.utf8("Test Using Larger Than Block-Size Key - Hash Key First"),
            Bytes.fromHex("60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54")),
        HmacVector(Bytes(ByteArray(131) { 0xaa.toByte() }),
            Bytes.utf8("This is a test using a larger than block-size key and a larger than block-size data. The key needs to be hashed before being used by the HMAC algorithm."),
            Bytes.fromHex("9b09ffa71b942fcb27635fbcd5b0e944bfdc63644f0713938a7f51535c3a35e2")),
    )

    @TestFactory
    fun `RFC4231 HMAC-SHA256 vectors preserve the firmware leading two-byte truncation`(): List<DynamicTest> =
        hmacVectors.mapIndexed { index, vector ->
            DynamicTest.dynamicTest("RFC4231 case ${index + 1}") {
                assertEquals(vector.expected, WireCrypto.hmacSha256(vector.data, vector.key).prefix(vector.expected.size))
                assertEquals(vector.expected.prefix(2), WireCrypto.truncatedHmacSha256(vector.data, vector.key))
                assertTrue(WireCrypto.authenticate(vector.data, vector.key, vector.expected.prefix(2)))
                assertEquals(2, WireCrypto.macSize)
            }
        }

    @Test
    fun `Empty HMAC keys preserve RFC2104 normalization instead of failing JCA initialization`() {
        val expected = Bytes.fromHex("b613679a0814d9ec772f95d778c35fc5ff1697c493715653c6c712144292c5ad")
        assertEquals(expected, WireCrypto.hmacSha256(Bytes.EMPTY, Bytes.EMPTY))
        assertEquals(expected, WireCrypto.hmacSha256(Bytes.EMPTY, Bytes.EMPTY.paddedOrTruncated(64)))
    }

    @Test
    fun `Existing SHA256 helper matches empty abc and all high-bit byte values without a duplicate implementation`() {
        for ((input, expected) in listOf(
            Bytes.EMPTY to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Bytes.utf8("abc") to "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Bytes(ByteArray(256) { it.toByte() }) to "40aff2e9d2d8922e47afd4648e6967497158785fbd1da870e7110266bf944880",
        )) assertEquals(Bytes.fromHex(expected), sha256(input))
    }

    @Test
    fun `FIPS197 AES128 vector is ECB with no IV or PKCS7 padding`() {
        val key = Bytes.fromHex("000102030405060708090a0b0c0d0e0f")
        val plaintext = Bytes.fromHex("00112233445566778899aabbccddeeff")
        val ciphertext = Bytes.fromHex("69c4e0d86a7b0430d8cdb78070b4c55a")
        assertEquals(ciphertext, WireCrypto.encryptAes128EcbZeroPadded(plaintext, key))
        assertEquals(plaintext, WireCrypto.decryptAes128Ecb(ciphertext, key))
        assertEquals(16, ciphertext.size)
    }

    @Test
    fun `NIST SP800-38A all four ECB blocks match independently specified ciphertext`() {
        val key = Bytes.fromHex("2b7e151628aed2a6abf7158809cf4f3c")
        val plaintext = Bytes.fromHex(
            "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e51" +
                "30c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710",
        )
        val ciphertext = Bytes.fromHex(
            "3ad77bb40d7a3660a89ecaf32466ef97f5d3d58503b9699de785895a96fdbaaf" +
                "43b1cd7f598ece23881b00e3ed03068877b0c785e27e8ad3f8223207104725dd4",
        )
        assertEquals(ciphertext, WireCrypto.encryptAes128EcbZeroPadded(plaintext, key))
        assertEquals(plaintext, WireCrypto.decryptAes128Ecb(ciphertext, key))
    }

    @Test
    fun `Zero-length raw AES input stays empty and exact blocks get no extra padding block`() {
        val key = CryptoFixtures.message("channel-normal").secret
        assertEquals(Bytes.EMPTY, WireCrypto.encryptAes128EcbZeroPadded(Bytes.EMPTY, key))
        assertEquals(Bytes.EMPTY, WireCrypto.decryptAes128Ecb(Bytes.EMPTY, key))
        for (length in listOf(1, 15, 16, 17, 31, 32, 33)) {
            val input = Bytes(ByteArray(length) { 0x7f })
            val encrypted = WireCrypto.encryptAes128EcbZeroPadded(input, key)
            assertEquals(((length + 15) / 16) * 16, encrypted.size)
            assertEquals(input.paddedOrTruncated(encrypted.size), WireCrypto.decryptAes128Ecb(encrypted, key))
        }
    }

    @TestFactory
    fun `Every authentication tag bit is checked`(): List<DynamicTest> {
        val vector = CryptoFixtures.message("channel-normal")
        val ciphertext = vector.packet.slice(2, vector.packet.size)
        return (0 until 16).map { bit ->
            DynamicTest.dynamicTest("tag bit $bit") {
                val tag = vector.packet.prefix(2).toByteArray()
                tag[bit / 8] = (tag[bit / 8].toInt() xor (1 shl (bit % 8))).toByte()
                assertFalse(WireCrypto.authenticate(ciphertext, vector.secret, Bytes(tag)))
                assertEquals(ChannelCrypto.DecryptResult.HmacFailed, ChannelCrypto.decrypt(Bytes(tag) + ciphertext, vector.secret))
            }
        }
    }

    @Test
    fun `Authentication tag sizes are explicit typed failures`() {
        for (length in listOf(0, 1, 3, 16, 32)) {
            val error = assertFailsWith<CryptoException.InvalidTagLength> {
                WireCrypto.authenticate(Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY.paddedOrTruncated(length))
            }
            assertEquals(length, error.actual)
        }
    }

    @Test
    fun `Crypto operations do not install replace or reorder global security providers`() {
        val before = Security.getProviders().map { it.name to it.javaClass.name }
        val key = CryptoFixtures.keys[0]
        Ed25519Crypto.signWithSeed(Bytes.EMPTY, key.seed)
        Ed25519Crypto.signWithExpanded(Bytes.EMPTY, key.expanded)
        Ed25519ToX25519.convertPublicKey(key.edPublic)
        X25519Crypto.publicKey(key.xPrivate)
        val vector = CryptoFixtures.message("channel-normal")
        ChannelCrypto.decrypt(vector.packet, vector.secret)
        assertEquals(before, Security.getProviders().map { it.name to it.javaClass.name })
    }
}
