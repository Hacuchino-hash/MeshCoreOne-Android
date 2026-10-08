// AndroidOnly: WP-304 Real storage operation/problem/cause presentation, native family equivalents and preference-only committed recovery.
package com.meshcoreone.android.core.ui

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.meshcoreone.android.core.datastore.StorageFailure
import com.meshcoreone.android.core.datastore.StorageOperation
import com.meshcoreone.android.core.datastore.StorageProblem
import com.meshcoreone.android.core.model.BackupContract
import com.meshcoreone.android.core.model.CommittedBackupCounts
import com.meshcoreone.android.core.model.CommittedBackupPreferenceFailure
import com.meshcoreone.android.core.model.CommittedBackupReceipt
import com.meshcoreone.android.core.model.RadioId
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "en-rUS")
class StoragePresentationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val resources get() = context.resources
    private val mapper = UiErrorMapper(reporter = UiErrorReporter {})

    private val problems = listOf(
        StorageProblem.LockedBeforeFirstUnlock, StorageProblem.DeviceLocked,
        StorageProblem.UserStorageInaccessible, StorageProblem.PermissionDenied, StorageProblem.IoFailure,
        StorageProblem.CorruptPreferences, StorageProblem.CorruptSecretState, StorageProblem.CorruptEnvelope,
        StorageProblem.AuthenticationFailed, StorageProblem.FormerKeyMissing, StorageProblem.KeyPermanentlyInvalidated,
        StorageProblem.CorruptKeystoreKey, StorageProblem.ProviderFailure, StorageProblem.DuplicateOwner,
        StorageProblem.OwnerClosed, StorageProblem.InvalidSecretIdentity, StorageProblem.InvalidSecretValue,
        StorageProblem.SecretAlreadyExists, StorageProblem.SecretNotFound, StorageProblem.UnsupportedVersion(3),
        StorageProblem.StateTooLarge(1048576), StorageProblem.PreferenceTypeMismatch("private-alias"),
        StorageProblem.PreferenceHasNoDefault("private-alias"), StorageProblem.InvalidPreference("private-alias"),
    )

    @Test fun everyActualProblemAndOperationRetainsTypedMetadataAndOriginalCauseWithoutSecretProviderCopy() {
        for (operation in StorageOperation.entries) for (problem in problems) {
            val cause = IOException("secret-provider-marker/private-alias")
            val failure = StorageFailure(problem, operation, cause)
            val presented = mapper.present(failure)
            assertSame(failure, presented.originalFailure)
            assertSame(cause, presented.originalFailure.cause)
            assertEquals(UiStorageIssue(operation, problem), presented.content.storageIssue)
            assertTrue(presented.content.code.contains(operation.name))
            assertTrue(presented.content.code.contains(problem.javaClass.simpleName))
            val copy = presented.content.message.resolve(resources)
            assertTrue(copy.isNotEmpty())
            assertFalse(copy.contains("secret-provider-marker"))
            assertFalse(copy.contains("private-alias"))
        }
    }

    @Test fun lockedWriteAndMissingKeyDeletionHaveDifferentCopyAndSafeRecoveryWithoutAppleStatuses() {
        val locked = mapper.present(StorageFailure(StorageProblem.DeviceLocked, StorageOperation.WRITE))
        val missing = mapper.present(StorageFailure(StorageProblem.FormerKeyMissing, StorageOperation.DELETE))
        assertNotEquals(locked.content.message.resolve(resources), missing.content.message.resolve(resources))
        assertEquals(UiRecovery.UNLOCK_DEVICE, locked.content.recovery)
        assertEquals(UiRecovery.RESTORE_SECURE_DATA, missing.content.recovery)
        assertTrue(locked.content.message.resolve(resources).startsWith(resources.getString(R.string.ui_storage_save_failed)))
        assertTrue(missing.content.message.resolve(resources).startsWith(resources.getString(R.string.ui_storage_delete_failed)))
    }

    @Test fun nativeEncodingSaveReadAndDeleteFamiliesAreDrivenByActualTypedFaults() {
        val failures = listOf(
            StorageFailure(StorageProblem.InvalidSecretValue, StorageOperation.WRITE),
            StorageFailure(StorageProblem.IoFailure, StorageOperation.WRITE),
            StorageFailure(StorageProblem.IoFailure, StorageOperation.READ),
            StorageFailure(StorageProblem.IoFailure, StorageOperation.DELETE),
        )
        assertEquals(NativeStorageErrorFamily.entries.toList(), failures.map { it.nativeFamily })
        val prefixes = listOf(R.string.ui_storage_encoding_failed, R.string.ui_storage_save_failed,
            R.string.ui_storage_read_failed, R.string.ui_storage_delete_failed)
        for ((index, failure) in failures.withIndex()) {
            assertTrue(mapper.message(failure).resolve(resources).startsWith(resources.getString(prefixes[index])))
        }
    }

    @Test fun anExplicitTypedAdapterCanOverrideNativeStoragePresentationAndRecoveryWithoutLosingMetadata() {
        val actual = StorageFailure(StorageProblem.DeviceLocked, StorageOperation.WRITE)
        var called: StorageFailure? = null
        val adapter = TypedUiErrorAdapter(StorageFailure::class.java) { error, _ ->
            called = error
            UiErrorMapping(UiText.Verbatim("typed-adapter-fixture"), UiRecovery.UNLOCK_DEVICE)
        }
        val presented = UiErrorMapper(listOf(adapter), UiErrorReporter {}).present(actual)
        assertSame(actual, called)
        assertEquals("typed-adapter-fixture", presented.content.message.resolve(resources))
        assertEquals(UiStorageIssue(StorageOperation.WRITE, StorageProblem.DeviceLocked), presented.content.storageIssue)
        assertSame(actual, presented.originalFailure)
        assertFailsWith<CancellationException> {
            UiErrorMapper(listOf(adapter), UiErrorReporter {}).present(CancellationException("synthetic cancellation"))
        }
    }

    private class ConsumerMarkerFixture(
        override val committedReceipt: CommittedBackupReceipt,
        override val preferenceFailure: Throwable,
    ) : Exception("test-only-marker-not-data-producer", preferenceFailure), CommittedBackupPreferenceFailure

    @Test fun typedCommittedReceiptJoinsTheRealProducerContractAndDispatchesOnlyRemainingPreferences() {
        val radio = RadioId(UUID.fromString("11111111-2222-3333-4444-555555555555"))
        val counts = BackupContract.modelArrayKeys.mapIndexed { index, kind ->
            kind to CommittedBackupCounts(index.toLong(), 2, 3, 4)
        }.toMap()
        val receipt = CommittedBackupReceipt(counts, false, mapOf(radio to setOf(1u.toUByte(), 3u.toUByte())))
        val storage = StorageFailure(StorageProblem.FormerKeyMissing, StorageOperation.WRITE)
        val failure = ConsumerMarkerFixture(receipt, storage)
        val presented = mapper.present(failure)
        assertSame(failure, presented.originalFailure)
        assertSame(storage, presented.originalFailure.cause)
        assertEquals(receipt, presented.content.committedBackupReceipt)
        assertEquals(UiRecovery.COMPLETE_BACKUP_PREFERENCES, presented.content.recovery)
        assertEquals(UiStorageIssue(StorageOperation.WRITE, StorageProblem.FormerKeyMissing), presented.content.storageIssue)
        val copy = presented.content.message.resolve(resources)
        assertTrue(copy.startsWith(resources.getString(R.string.ui_backup_preferences_incomplete)))
        var importRetries = 0
        var completed: CommittedBackupPreferenceFailure? = null
        val action = assertNotNull(presented.recoveryAction(UiErrorActions(
            retry = { importRetries++ }, completeBackupPreferences = { completed = it },
        )))
        action()
        assertEquals(0, importRetries)
        assertSame(failure, completed)
        assertEquals(12, assertNotNull(completed).committedReceipt.counts.size)
        val affectedSlots = assertNotNull(assertNotNull(completed).committedReceipt.channelSlotsAffectedByImport[radio])
        assertEquals(setOf(1u.toUByte(), 3u.toUByte()), affectedSlots.toSet())
        assertNull(presented.recoveryAction(UiErrorActions(retry = { importRetries++ })))
        assertFailsWith<IllegalArgumentException> {
            presented.copy(content = presented.content.copy(recovery = UiRecovery.RETRY)).recoveryAction(UiErrorActions(retry = {}))
        }
    }

    @Test fun committedRecoveryCannotBeShadowedByABroadRetryAdapterOrLoseItsReceipt() {
        val receipt = CommittedBackupReceipt(BackupContract.modelArrayKeys.associateWith { CommittedBackupCounts(1, 0, 0, 0) },
            false, emptyMap())
        val fixture = ConsumerMarkerFixture(receipt, StorageFailure(StorageProblem.OwnerClosed, StorageOperation.WRITE))
        val broad = TypedUiErrorAdapter(Exception::class.java) { _, _ -> UiErrorMapping(UiText.Verbatim("generic"), UiRecovery.RETRY) }
        val presented = UiErrorMapper(listOf(broad), UiErrorReporter {}).present(fixture)
        assertEquals(UiRecovery.COMPLETE_BACKUP_PREFERENCES, presented.content.recovery)
        assertEquals(receipt, presented.content.committedBackupReceipt)
    }

    @Test fun nativeCopyResolvesInAllTwelveSourceLocalesWithVersionAndByteMetadata() {
        for (locale in listOf("en", "de", "es", "fr", "it", "ko", "nl", "pl", "pt", "ru", "uk", "zh-Hans")) {
            val configuration = Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(locale)) }
            val localized = context.createConfigurationContext(configuration).resources
            val version = mapper.message(StorageFailure(StorageProblem.UnsupportedVersion(3), StorageOperation.READ)).resolve(localized)
            val size = mapper.message(StorageFailure(StorageProblem.StateTooLarge(1048576), StorageOperation.WRITE)).resolve(localized)
            assertTrue(version.contains("3"), locale)
            assertTrue(size.contains("1048576"), locale)
            assertTrue(localized.getString(R.string.ui_backup_preferences_incomplete).isNotEmpty())
        }
    }
}
