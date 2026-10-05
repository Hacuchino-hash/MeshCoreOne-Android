// AndroidOnly: WP-204 Real SDK/Keyguard shadow policy regressions; no hardware Keystore assertion.
package com.meshcoreone.android.core.datastore

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import java.security.InvalidAlgorithmParameterException
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 32, 33, 37])
class KeystorePlatformPolicyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun noPinAndSecureLockBothUseActualSdkNewKeyPolicy() {
        val manager = context.getSystemService(KeyguardManager::class.java)
        val keyguard = Shadows.shadowOf(manager)
        Shadows.shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        for (secure in listOf(false, true)) {
            keyguard.setIsDeviceSecure(secure)
            keyguard.setIsDeviceLocked(false)
            assertEquals(secure, manager.isDeviceSecure)
            assertFalse(manager.isDeviceLocked)
            val keys = TestKeystoreAccess()
            val delegate = object : KeystoreKeyAccess by keys {
                override fun generate(specification: android.security.keystore.KeyGenParameterSpec): javax.crypto.SecretKey {
                    if (Build.VERSION.SDK_INT <= 34 && !manager.isDeviceSecure && specification.isUnlockedDeviceRequired) {
                        throw InvalidAlgorithmParameterException("Actual legacy platform policy requires a secure lock")
                    }
                    return keys.generate(specification)
                }
            }
            val crypto = AndroidKeystoreCryptography(delegate, AndroidStorageAccess(context, secrets = true))
            crypto.createKeyIfAbsent("platform-$secure")
            val specification = keys.specifications.single()
            assertEquals(Build.VERSION.SDK_INT >= 35, specification.isUnlockedDeviceRequired)
            assertFalse(specification.isUserAuthenticationRequired)
            assertEquals(256, specification.keySize)
            assertTrue(specification.isRandomizedEncryptionRequired)
            assertEquals(
                if (Build.VERSION.SDK_INT >= 35) KeystoreUnlockedAccessPolicy.PLATFORM_ENFORCED_WITH_EXPLICIT_LOCK_CHECKS
                else KeystoreUnlockedAccessPolicy.LEGACY_EXPLICIT_LOCK_CHECKS,
                crypto.unlockedAccessPolicy,
            )
            val clear = "fixture-$secure".toByteArray()
            val encrypted = crypto.encrypt("platform-$secure", clear, byteArrayOf(1, 2, 3))
            assertContentEquals(clear, crypto.decrypt("platform-$secure", encrypted, byteArrayOf(1, 2, 3)))
        }
    }

    @Test
    fun removingLegacySecureLockNeverRecreatesAnExistingKeyOrLosesCiphertext() {
        val manager = context.getSystemService(KeyguardManager::class.java)
        val keyguard = Shadows.shadowOf(manager)
        Shadows.shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        keyguard.setIsDeviceSecure(true)
        keyguard.setIsDeviceLocked(false)
        val keys = TestKeystoreAccess()
        val crypto = AndroidKeystoreCryptography(keys, AndroidStorageAccess(context, secrets = true))
        crypto.createKeyIfAbsent("existing")
        val original = keys.keys.getValue("existing")
        val plaintext = "kept".toByteArray()
        val encrypted = crypto.encrypt("existing", plaintext, byteArrayOf(1))
        keyguard.setIsDeviceSecure(false)
        crypto.createKeyIfAbsent("existing")
        assertSame(original, keys.keys.getValue("existing"))
        assertEquals(1, keys.specifications.size)
        assertContentEquals(plaintext, crypto.decrypt("existing", encrypted, byteArrayOf(1)))
    }

    @Test
    fun currentLockAndFirstUnlockChecksRemainMandatoryForBothLockConfigurations() = runBlocking<Unit> {
        val manager = context.getSystemService(KeyguardManager::class.java)
        val keyguard = Shadows.shadowOf(manager)
        val user = Shadows.shadowOf(context.getSystemService(UserManager::class.java))
        for (secure in listOf(false, true)) {
            keyguard.setIsDeviceSecure(secure)
            val keys = TestKeystoreAccess()
            val crypto = AndroidKeystoreCryptography(keys, AndroidStorageAccess(context, secrets = true))
            user.setUserUnlocked(false)
            keyguard.setIsDeviceLocked(false)
            expectStorageFailure(StorageProblem.LockedBeforeFirstUnlock) { crypto.createKeyIfAbsent("blocked") }
            user.setUserUnlocked(true)
            keyguard.setIsDeviceLocked(true)
            expectStorageFailure(StorageProblem.DeviceLocked) { crypto.createKeyIfAbsent("blocked") }
            assertTrue(keys.specifications.isEmpty())
            keyguard.setIsDeviceLocked(false)
            crypto.createKeyIfAbsent("unlocked-$secure")
            keyguard.setIsDeviceLocked(true)
            expectStorageFailure(StorageProblem.DeviceLocked) { crypto.containsKey("unlocked-$secure") }
            assertEquals(1, keys.specifications.size)
        }
    }

    @Test
    fun missingAndInvalidatedKeysNeverTriggerAnApiPolicyRecoveryKey() = runBlocking<Unit> {
        val manager = context.getSystemService(KeyguardManager::class.java)
        Shadows.shadowOf(manager).setIsDeviceSecure(false)
        Shadows.shadowOf(manager).setIsDeviceLocked(false)
        Shadows.shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        val keys = TestKeystoreAccess()
        val crypto = AndroidKeystoreCryptography(keys, AndroidStorageAccess(context, secrets = true))
        crypto.createKeyIfAbsent("used")
        val encrypted = crypto.encrypt("used", byteArrayOf(1), byteArrayOf())
        keys.keys.clear()
        expectStorageFailure(StorageProblem.FormerKeyMissing) { crypto.decrypt("used", encrypted, byteArrayOf()) }
        keys.findFailure = android.security.keystore.KeyPermanentlyInvalidatedException()
        expectStorageFailure(StorageProblem.KeyPermanentlyInvalidated) { crypto.decrypt("used", encrypted, byteArrayOf()) }
        assertEquals(1, keys.specifications.size)
    }
}
