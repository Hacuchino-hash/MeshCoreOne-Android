// AndroidOnly: WP-204 Actual credential-protected/no-backup Context and process-factory shadow evidence.
package com.meshcoreone.android.core.datastore

import android.content.Context
import android.content.res.Configuration
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class PlatformStorageTest {
    @Test
    fun actualApplicationContextFactoryIsSingletonAndPersistsUnderNoBackupDirectory() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val reporter = RecordingReporter()
        val owner = MeshCoreStorage.get(app, reporter)
        try {
            val view = app.createConfigurationContext(Configuration(app.resources.configuration))
            assertSame(owner, MeshCoreStorage.get(view, reporter))
            owner.preferences.set(AppStorageKey.hasCompletedOnboarding, true)
            val credential = app
            val file = File(File(credential.noBackupFilesDir, "meshcoreone-datastore"), MeshCoreStorage.PREFERENCE_FILENAME)
            assertTrue(file.isFile)
            assertTrue(file.canonicalPath.startsWith(credential.noBackupFilesDir.canonicalPath + File.separator))
            assertFalse(credential.isDeviceProtectedStorage)
            assertTrue(reporter.failures.isEmpty())
        } finally {
            owner.close()
        }
        val reopened = MeshCoreStorage.get(app, reporter)
        try {
            assertTrue(reopened.preferences.get(AppStorageKey.hasCompletedOnboarding))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun unavailableBeforeFirstUnlockIsReportedByActualFactoryNotReplacedWithEmptyPersistence() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val users = Shadows.shadowOf(app.getSystemService(UserManager::class.java))
        users.setUserUnlocked(false)
        val reporter = RecordingReporter()
        try {
            expectStorageFailure(StorageProblem.LockedBeforeFirstUnlock) { MeshCoreStorage.get(app, reporter) }
            assertEquals(StorageProblem.LockedBeforeFirstUnlock, reporter.failures.single().problem)
        } finally {
            users.setUserUnlocked(true)
        }
    }

    @Test
    fun storageErrorLoggingContainsCodesButNotProviderCauses() {
        val marker = "WP204_TEST_ONLY_DO_NOT_LOG_PROVIDER_CAUSE"
        org.robolectric.shadows.ShadowLog.clear()
        AndroidStorageIssueReporter.report(
            StorageFailure(StorageProblem.ProviderFailure, StorageOperation.WRITE, java.security.ProviderException(marker)),
        )
        val logs = org.robolectric.shadows.ShadowLog.getLogsForTag("MeshCoreOne.Storage")
        assertEquals(1, logs.size)
        assertEquals("WRITE:ProviderFailure", logs.single().msg)
        assertNull(logs.single().throwable)
        assertFalse(logs.single().msg.contains(marker))
    }

    @Test
    fun manualExportPolicyIsSeparateFromAutomaticBackupAndDeviceOnlyPasswords() {
        assertFalse(SensitiveStoragePolicy.AUTOMATIC_BACKUP_ALLOWED)
        assertFalse(SensitiveStoragePolicy.NODE_PASSWORDS_INCLUDED_IN_MANUAL_APP_BACKUP)
        assertTrue(SensitiveStoragePolicy.IDENTITY_EXPORT_REQUIRES_EXPLICIT_USER_ACTION)
        assertTrue(SensitiveStoragePolicy.MANUAL_BACKUPS_MAY_CONTAIN_CHANNEL_SECRETS)
    }
}
