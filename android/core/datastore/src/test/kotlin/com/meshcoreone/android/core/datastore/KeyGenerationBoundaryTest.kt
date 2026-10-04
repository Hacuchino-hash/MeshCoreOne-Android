// AndroidOnly: WP-204 Deterministic generation bounds, entropy failure, cancellation and key-format guards.
package com.meshcoreone.android.core.datastore

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.security.ProviderException
import java.security.SecureRandom
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test

class KeyGenerationBoundaryTest {
    @Test
    fun sourceAttemptBudgetsAreExactAndBounded() {
        assertEquals(listOf(10_000, 10_000, 10_000, 81_920, 1_310_720), (0..4).map(KeyGenerationService::maxAttempts))
    }

    @Test
    fun maximumAttemptsIsARealTypedFailureAfterExactly10000Attempts() = runBlocking<Unit> {
        var calls = 0
        val generator = KeyGenerationService(SeedSource { calls++; rfc8032Seed })
        assertFailsWith<KeyGenerationFailure.MaxAttemptsExceeded> { generator.generateIdentity("AA") }
        assertEquals(10_000, calls)
    }

    @Test
    fun malformedPrefixNeverReachesEntropyOrOverflowsAttemptScaling() = runBlocking<Unit> {
        var calls = 0
        val generator = KeyGenerationService(SeedSource { calls++; rfc8032Seed })
        for (prefix in listOf("aa", "ABCDE", "G1", "\uD83D\uDE00")) {
            assertFailsWith<KeyGenerationFailure.InvalidPrefix> { generator.generateIdentity(prefix) }
        }
        assertEquals(0, calls)
        assertEquals(rfc8032PublicKey, generator.generateIdentity("").publicKey)
        assertEquals(1, calls)
    }

    @Test
    fun reservedPrefixesFailBeforeEntropy() = runBlocking<Unit> {
        var calls = 0
        val generator = KeyGenerationService(SeedSource { calls++; rfc8032Seed })
        for (prefix in listOf("00", "FF", "00A1", "FFB2")) {
            assertFailsWith<KeyGenerationFailure.ReservedPrefix> { generator.generateIdentity(prefix) }
        }
        assertEquals(0, calls)
    }

    @Test
    fun randomProviderFailureAndWrongSeedWidthAreNotExpandedKeys() = runBlocking<Unit> {
        val failing = SecureSeedSource(object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) {
                throw ProviderException("Test-only entropy failure")
            }
        })
        assertFailsWith<KeyGenerationFailure.RandomGenerationFailed> { KeyGenerationService(failing).generateIdentity(null) }
        for (length in listOf(0, 31, 33, 64)) {
            assertFailsWith<KeyGenerationFailure.RandomGenerationFailed> {
                KeyGenerationService(SeedSource { Bytes(ByteArray(length)) }).generateIdentity(null)
            }
        }
    }

    @Test
    fun cancellationFromEntropyIsNotReclassifiedAsRandomFailure() = runBlocking<Unit> {
        val generator = KeyGenerationService(SeedSource { throw CancellationException("Test-only entropy boundary cancellation") })
        assertFailsWith<CancellationException> { generator.generateIdentity(null) }
    }

    @Test
    fun importedExpandedKeyUsesMergedCurvePrimitiveAndNotDoubleHashing() = runBlocking<Unit> {
        val generated = KeyGenerationService(SeedSource { rfc8032Seed }).generateIdentity(null)
        val imported = KeyGenerationService.importExpandedKey(generated.expandedPrivateKey)
        assertEquals(rfc8032PublicKey, imported.publicKey)
        assertEquals(generated.expandedPrivateKey, imported.expandedPrivateKey)
        assertEquals("GeneratedIdentity([REDACTED])", imported.toString())
        assertFalse(imported.toString().contains(imported.expandedPrivateKey.hexUppercase()))
    }
}
