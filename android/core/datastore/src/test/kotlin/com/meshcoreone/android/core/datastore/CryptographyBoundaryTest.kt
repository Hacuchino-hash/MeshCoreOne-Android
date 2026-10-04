// AndroidOnly: WP-204 Real AES-GCM nonce/AAD proofs and Android Keystore adapter error/spec assertions.
package com.meshcoreone.android.core.datastore

import android.app.KeyguardManager
import android.content.Context
import android.os.UserManager
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import androidx.test.core.app.ApplicationProvider
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.ProviderException
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class CryptographyBoundaryTest {
    private fun cryptography(keys: TestKeystoreAccess = TestKeystoreAccess()) =
        AndroidKeystoreCryptography(keys, StorageAccess {})

    @Test
    fun keyGenerationUsesSourceEquivalentUnlockedDeviceOnlyAes256GcmParameters() {
        val keys = TestKeystoreAccess()
        val crypto = cryptography(keys)
        crypto.createKeyIfAbsent("test.alias")
        crypto.createKeyIfAbsent("test.alias")
        val specification = keys.specifications.single()
        assertEquals("test.alias", specification.keystoreAlias)
        assertEquals(256, specification.keySize)
        assertEquals(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT, specification.purposes)
        assertEquals(listOf(KeyProperties.BLOCK_MODE_GCM), specification.blockModes.toList())
        assertEquals(listOf(KeyProperties.ENCRYPTION_PADDING_NONE), specification.encryptionPaddings.toList())
        assertTrue(specification.isRandomizedEncryptionRequired)
        assertTrue(specification.isUnlockedDeviceRequired)
        assertFalse(specification.isUserAuthenticationRequired)
    }

    @Test
    fun all128EncryptionsHaveFresh96BitNoncesAndAuthenticatedRoundTrips() {
        val crypto = cryptography()
        crypto.createKeyIfAbsent("nonce.test")
        val nonces = mutableSetOf<Bytes>()
        val plaintext = "fixture-only".toByteArray()
        val aad = "namespace/type/alias/version".toByteArray()
        repeat(128) {
            val encrypted = crypto.encrypt("nonce.test", plaintext, aad)
            assertEquals(12, encrypted.nonce.size)
            assertEquals(plaintext.size + 16, encrypted.ciphertext.size)
            assertTrue(nonces.add(encrypted.nonce), "A nonce must not repeat")
            assertContentEquals(plaintext, crypto.decrypt("nonce.test", encrypted, aad))
        }
        assertEquals(128, nonces.size)
    }

    @Test
    fun aadNonceTagAndCiphertextChangesEachFailAuthentication() = runBlocking<Unit> {
        val crypto = cryptography()
        crypto.createKeyIfAbsent("aad.test")
        val aad = "namespace/version/alias".toByteArray()
        val encrypted = crypto.encrypt("aad.test", "fixture".toByteArray(), aad)
        expectStorageFailure(StorageProblem.AuthenticationFailed) {
            crypto.decrypt("aad.test", encrypted, "other/namespace/version/alias".toByteArray())
        }
        val nonce = encrypted.nonce.toByteArray().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        expectStorageFailure(StorageProblem.AuthenticationFailed) {
            crypto.decrypt("aad.test", AuthenticatedCiphertext(Bytes(nonce), encrypted.ciphertext), aad)
        }
        for (offset in listOf(0, encrypted.ciphertext.size - 1)) {
            val ciphertext = encrypted.ciphertext.toByteArray().apply { this[offset] = (this[offset].toInt() xor 1).toByte() }
            expectStorageFailure(StorageProblem.AuthenticationFailed) {
                crypto.decrypt("aad.test", AuthenticatedCiphertext(encrypted.nonce, Bytes(ciphertext)), aad)
            }
        }
    }

    @Test
    fun missingInvalidatedUnauthenticatedAndProviderKeysHaveDistinctOutcomes() = runBlocking<Unit> {
        val keys = TestKeystoreAccess()
        val crypto = cryptography(keys)
        expectStorageFailure(StorageProblem.FormerKeyMissing) { crypto.encrypt("missing", byteArrayOf(1), byteArrayOf(2)) }
        for ((failure, problem) in listOf(
            KeyPermanentlyInvalidatedException() to StorageProblem.KeyPermanentlyInvalidated,
            UserNotAuthenticatedException() to StorageProblem.DeviceLocked,
            ProviderException("Test-only crypto provider failure") to StorageProblem.ProviderFailure,
            SecurityException("Test-only denied key access") to StorageProblem.PermissionDenied,
        )) {
            keys.findFailure = failure
            expectStorageFailure(problem) { crypto.containsKey("test") }
        }
        assertTrue(keys.specifications.isEmpty())
    }

    @Test
    fun preunlockAndDeviceLockUseActualShadowedAndroidServices() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val users = context.getSystemService(UserManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val user = Shadows.shadowOf(users)
        val lock = Shadows.shadowOf(keyguard)
        user.setUserUnlocked(false)
        expectStorageFailure(StorageProblem.LockedBeforeFirstUnlock) {
            AndroidStorageAccess(context, secrets = false).requireAccessible(StorageOperation.READ)
        }
        expectStorageFailure(StorageProblem.LockedBeforeFirstUnlock) {
            AndroidStorageAccess(context, secrets = true).requireAccessible(StorageOperation.READ)
        }
        user.setUserUnlocked(true)
        lock.setIsDeviceLocked(true)
        AndroidStorageAccess(context, secrets = false).requireAccessible(StorageOperation.READ)
        expectStorageFailure(StorageProblem.DeviceLocked) {
            AndroidStorageAccess(context, secrets = true).requireAccessible(StorageOperation.READ)
        }
        lock.setIsDeviceLocked(false)
        AndroidStorageAccess(context, secrets = true).requireAccessible(StorageOperation.READ)
    }

    @Test
    fun deviceProtectedContextIsNeverUsedAsASecretFallback() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>().createDeviceProtectedStorageContext()
        expectStorageFailure(StorageProblem.UserStorageInaccessible) {
            AndroidStorageAccess(context, secrets = true).requireAccessible(StorageOperation.READ)
        }
    }

    @Test
    fun envelopeRejectsWrongRadioPublicKeyAliasAndSecretType() = runBlocking<Unit> {
        val crypto = cryptography()
        crypto.createKeyIfAbsent("envelope.alias")
        val original = SecretIdentity.NodePassword(radioId(1), nodePublicKey(1))
        val encoded = SecretEnvelope.encrypt(original, "envelope.alias", "fixture".toByteArray(), crypto)
        for (identity in listOf(
            SecretIdentity.NodePassword(radioId(2), nodePublicKey(1)),
            SecretIdentity.NodePassword(radioId(1), nodePublicKey(2)),
            SecretIdentity.RadioIdentity(radioId(1)),
        )) {
            expectStorageFailure(StorageProblem.CorruptEnvelope) {
                SecretEnvelope.decrypt(identity, "envelope.alias", encoded, crypto)
            }
        }
        expectStorageFailure(StorageProblem.CorruptEnvelope) {
            SecretEnvelope.decrypt(original, "other.alias", encoded, crypto)
        }
    }

    @Test
    fun everyTruncationAndTrailingByteFailsCheckedEnvelopeDecode() = runBlocking<Unit> {
        val crypto = cryptography()
        crypto.createKeyIfAbsent("envelope.truncation")
        val identity = SecretIdentity.NodePassword(radioId(), nodePublicKey())
        val envelope = SecretEnvelope.encrypt(identity, "envelope.truncation", "fixture".toByteArray(), crypto)
        for (length in 0 until envelope.size) {
            assertFailsWith<StorageFailure> {
                SecretEnvelope.decrypt(identity, "envelope.truncation", envelope.prefix(length), crypto)
            }
        }
        expectStorageFailure(StorageProblem.CorruptEnvelope) {
            SecretEnvelope.decrypt(identity, "envelope.truncation", envelope + Bytes.of(0), crypto)
        }
    }

    @Test
    fun hostileDeclaredPlaintextLengthsNeverControlAllocation() = runBlocking<Unit> {
        val crypto = cryptography()
        crypto.createKeyIfAbsent("length.test")
        val identity = SecretIdentity.NodePassword(radioId(), nodePublicKey())
        val bytes = SecretEnvelope.encrypt(identity, "length.test", "fixture".toByteArray(), crypto).toByteArray()
        val input = DataInputStream(ByteArrayInputStream(bytes))
        input.readInt()
        input.readUnsignedByte()
        input.readUnsignedByte()
        input.readCheckedString(256)
        input.readCheckedString(512)
        val offset = bytes.size - input.available()
        for (length in listOf(-1, Int.MAX_VALUE, 0, 65_537)) {
            val mutated = bytes.copyOf()
            val encoded = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(length) }.toByteArray()
            encoded.copyInto(mutated, offset)
            expectStorageFailure(StorageProblem.CorruptEnvelope) {
                SecretEnvelope.decrypt(identity, "length.test", Bytes(mutated), crypto)
            }
        }
    }

    @Test
    fun unsupportedEnvelopeVersionAndOversizedBinaryHaveNoSuccessFallback() = runBlocking<Unit> {
        val crypto = cryptography()
        crypto.createKeyIfAbsent("version.test")
        val identity = SecretIdentity.NodePassword(radioId(), nodePublicKey())
        val raw = SecretEnvelope.encrypt(identity, "version.test", "fixture".toByteArray(), crypto).toByteArray()
        raw[4] = 2
        expectStorageFailure(StorageProblem.UnsupportedVersion(2)) {
            SecretEnvelope.decrypt(identity, "version.test", Bytes(raw), crypto)
        }
        expectStorageFailure(StorageProblem.StateTooLarge(SecretEnvelope.MAXIMUM_BYTES)) {
            SecretEnvelope.decrypt(identity, "version.test", Bytes(ByteArray(SecretEnvelope.MAXIMUM_BYTES + 1)), crypto)
        }
    }

    @Test
    fun utf8CorruptionPasswordLimitAndPublicKeyWidthAreChecked() = runBlocking<Unit> {
        assertFailsWith<StorageFailure> { SecretIdentity.NodePassword(radioId(), Bytes(ByteArray(31))) }
        assertFailsWith<StorageFailure> { SecretIdentity.NodePassword(radioId(), Bytes(ByteArray(33))) }
        val crypto = cryptography()
        crypto.createKeyIfAbsent("utf8.test")
        val identity = SecretIdentity.NodePassword(radioId(), nodePublicKey())
        expectStorageFailure(StorageProblem.InvalidSecretValue) {
            SecretEnvelope.encrypt(identity, "utf8.test", byteArrayOf(-61, 40), crypto)
        }
        expectStorageFailure(StorageProblem.InvalidSecretValue) {
            SecretEnvelope.encrypt(identity, "utf8.test", ByteArray(65_537), crypto)
        }
        val boundary = ByteArray(65_536) { 65 }
        val encrypted = SecretEnvelope.encrypt(identity, "utf8.test", boundary, crypto)
        assertContentEquals(boundary, SecretEnvelope.decrypt(identity, "utf8.test", encrypted, crypto))
    }

    @Test
    fun encryptedAndSensitiveValuesNeverPrintPrivateMaterial() {
        val crypto = cryptography()
        crypto.createKeyIfAbsent("redaction.test")
        val secret = "WP204_NON_SECRET_TEST_MARKER"
        val encrypted = crypto.encrypt("redaction.test", secret.toByteArray(), byteArrayOf())
        assertFalse(encrypted.toString().contains(secret))
        assertTrue(encrypted.toString().contains("REDACTED"))
        assertTrue(SecretIdentity.NodePassword(radioId(), nodePublicKey()).toString().contains("REDACTED"))
        val failure = StorageFailure(StorageProblem.ProviderFailure, StorageOperation.READ, ProviderException(secret))
        assertFalse(failure.message.orEmpty().contains(secret))
    }

    @Test
    fun nestedPlatformInvalidationIsNotFlattenedIntoUnspecifiedProviderFailure() = runBlocking<Unit> {
        val keys = TestKeystoreAccess()
        val crypto = cryptography(keys)
        keys.findFailure = java.security.UnrecoverableKeyException("Test-only wrapper").apply {
            initCause(KeyPermanentlyInvalidatedException())
        }
        expectStorageFailure(StorageProblem.KeyPermanentlyInvalidated) { crypto.containsKey("test") }
        keys.findFailure = ProviderException("Test-only wrapper", UserNotAuthenticatedException())
        expectStorageFailure(StorageProblem.DeviceLocked) { crypto.containsKey("test") }
    }
}
