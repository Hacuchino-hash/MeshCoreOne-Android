// AndroidOnly: WP-102 RFC7748/RFC8032 plus independent OpenSSL-backed conversion and expanded-key signing evidence.
package com.meshcoreone.android.core.protocol.crypto

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CurveCryptoTest {
    @Test
    fun `RFC7748 Alice and Bob public keys and shared secrets match in both directions`() {
        val alice = Bytes.fromHex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val bob = Bytes.fromHex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        val alicePublic = Bytes.fromHex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
        val bobPublic = Bytes.fromHex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")
        val shared = Bytes.fromHex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")
        assertEquals(alicePublic, X25519Crypto.publicKey(alice))
        assertEquals(bobPublic, X25519Crypto.publicKey(bob))
        assertEquals(shared, X25519Crypto.sharedSecret(alice, bobPublic))
        assertEquals(shared, X25519Crypto.sharedSecret(bob, alicePublic))
    }

    @TestFactory
    fun `RFC7748 scalar multiplication examples preserve clamping and high bits`(): List<DynamicTest> =
        listOf(
            Triple("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4",
                "e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c",
                "c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552"),
            Triple("4b66e9d4d1b4673c5ad22691957d6af5c11b6421e0ea01d42ca4169e7918ba0d",
                "e5210f12786811d3f4b7959d0538ae2c31dbe7106fc03c3efc4cd549c715a493",
                "95cbde9476e8907d7aade45cb4b873f88b595a68799fa152e6f8f7647aac7957"),
        ).mapIndexed { index, (scalar, coordinate, result) ->
            DynamicTest.dynamicTest("RFC7748 scalar example ${index + 1}") {
                assertEquals(Bytes.fromHex(result), X25519Crypto.sharedSecret(Bytes.fromHex(scalar), Bytes.fromHex(coordinate)))
            }
        }

    @Test
    fun `RFC7748 one and one thousand iterations match the independent standard`() {
        var scalar = Bytes.of(9).paddedOrTruncated(32)
        var coordinate = scalar
        repeat(1000) { index ->
            val next = X25519Crypto.sharedSecret(scalar, coordinate)
            coordinate = scalar
            scalar = next
            if (index == 0) assertEquals(
                Bytes.fromHex("422c8e7a6227d7bca1350b3e2bb7279f7897b87bb6854b783c60e80311ae3079"), scalar,
            )
        }
        assertEquals(Bytes.fromHex("684cf59ba83309552800ef566f2f4d3c1c3887c49360e3875f2eb94d99532c51"), scalar)
    }

    @Test
    fun `RFC7748 masks the public high bit and accepts noncanonical Montgomery coordinates`() {
        val vector = CryptoFixtures.message("direct-normal")
        val public = requireNotNull(vector.publicKey).toByteArray()
        public[31] = (public[31].toInt() or 0x80).toByte()
        assertEquals(vector.secret, X25519Crypto.sharedSecret(requireNotNull(vector.privateKey), Bytes(public)))
        val nine = Bytes.of(9).paddedOrTruncated(32)
        val noncanonicalNine = Bytes.fromHex("f6ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f")
        assertEquals(Bytes.fromHex("422c8e7a6227d7bca1350b3e2bb7279f7897b87bb6854b783c60e80311ae3079"),
            X25519Crypto.sharedSecret(nine, noncanonicalNine))
    }

    @TestFactory
    fun `Every independent seed expansion public key and Edwards conversion matches bytes`(): List<DynamicTest> =
        CryptoFixtures.keys.map { vector ->
            DynamicTest.dynamicTest("identity and conversion ${vector.id}") {
                assertEquals(vector.expanded, Ed25519Crypto.expandSeed(vector.seed))
                assertEquals(vector.edPublic, Ed25519Crypto.publicKeyFromSeed(vector.seed))
                assertEquals(vector.edPublic, Ed25519Crypto.publicKeyFromExpanded(vector.expanded))
                assertEquals(vector.xPrivate, Ed25519Crypto.x25519PrivateKeyFromSeed(vector.seed))
                assertEquals(vector.xPrivate, Ed25519Crypto.x25519PrivateKeyFromExpanded(vector.expanded))
                assertEquals(vector.xPublic, X25519Crypto.publicKey(vector.xPrivate))
                assertEquals(vector.xPublic, Ed25519ToX25519.convertPublicKey(vector.edPublic))
                val negative = vector.edPublic.toByteArray()
                negative[31] = (negative[31].toInt() xor 0x80).toByte()
                assertEquals(vector.xPublic, Ed25519ToX25519.convertPublicKey(Bytes(negative)))
            }
        }

    @TestFactory
    fun `Seed and firmware-expanded signatures match RFC and independent variable-length data`(): List<DynamicTest> =
        CryptoFixtures.signatures.map { vector ->
            DynamicTest.dynamicTest("sign and verify ${vector.id}") {
                val seedBefore = Bytes(vector.seed.toByteArray())
                val messageBefore = Bytes(vector.message.toByteArray())
                val expanded = Ed25519Crypto.expandSeed(vector.seed)
                assertEquals(vector.signature, Ed25519Crypto.signWithSeed(vector.message, vector.seed))
                assertEquals(vector.signature, Ed25519Crypto.signWithExpanded(vector.message, expanded))
                assertTrue(Ed25519Crypto.verify(vector.message, vector.signature, vector.publicKey))
                assertTrue(jdkVerify(vector.message, vector.signature, vector.publicKey))
                assertEquals(seedBefore, vector.seed)
                assertEquals(messageBefore, vector.message)
            }
        }

    @Test
    fun `Expanded nonce half is not ignored or mistaken for a seed-plus-public-key format`() {
        val vector = CryptoFixtures.signatures[0]
        val expanded = Ed25519Crypto.expandSeed(vector.seed)
        val altered = expanded.toByteArray()
        altered[32] = (altered[32].toInt() xor 0x80).toByte()
        val alteredExpanded = Bytes(altered)
        assertEquals(vector.publicKey, Ed25519Crypto.publicKeyFromExpanded(alteredExpanded))
        assertEquals(expanded.prefix(32), Ed25519Crypto.x25519PrivateKeyFromExpanded(alteredExpanded))
        val signature = Ed25519Crypto.signWithExpanded(vector.message, alteredExpanded)
        assertNotEquals(vector.signature, signature)
        assertTrue(jdkVerify(vector.message, signature, vector.publicKey))
        assertTrue(Ed25519Crypto.verify(vector.message, signature, vector.publicKey))
        assertEquals(expanded, Ed25519Crypto.expandSeed(vector.seed))
    }

    @Test
    fun `Modified message signature and noncanonical R or S never verify`() {
        val vector = CryptoFixtures.signatures[1]
        assertFalse(Ed25519Crypto.verify(vector.message + Bytes.of(0), vector.signature, vector.publicKey))
        val changed = vector.signature.toByteArray()
        changed[0] = (changed[0].toInt() xor 0x80).toByte()
        assertFalse(Ed25519Crypto.verify(vector.message, Bytes(changed), vector.publicKey))
        val noncanonicalS = vector.signature.toByteArray()
        noncanonicalS.fill(0xff.toByte(), 32, 64)
        assertFalse(Ed25519Crypto.verify(vector.message, Bytes(noncanonicalS), vector.publicKey))
        val noncanonicalR = vector.signature.toByteArray()
        Bytes.fromHex("edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f")
            .toByteArray().copyInto(noncanonicalR)
        assertFalse(Ed25519Crypto.verify(vector.message, Bytes(noncanonicalR), vector.publicKey))
        assertFalse(Ed25519Crypto.verify(vector.message, Bytes.EMPTY.paddedOrTruncated(64), vector.publicKey))
    }

    private fun jdkVerify(message: Bytes, signature: Bytes, publicKey: Bytes): Boolean {
        // Independent JDK21 test oracle only; production curves do not rely on Android's EdDSA provider.
        val encoded = Bytes.fromHex("302a300506032b6570032100") + publicKey
        val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(encoded.toByteArray()))
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(key)
        verifier.update(message.toByteArray())
        return verifier.verify(signature.toByteArray())
    }
}
