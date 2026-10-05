// AndroidOnly: WP-204 Credential-protected user storage and source WhenUnlocked secret access.
package com.meshcoreone.android.core.datastore

import android.app.KeyguardManager
import android.content.Context
import android.os.UserManager

fun interface StorageAccess {
    fun requireAccessible(operation: StorageOperation)
}

internal class AndroidStorageAccess(private val context: Context, private val secrets: Boolean) : StorageAccess {
    override fun requireAccessible(operation: StorageOperation) {
        if (context.isDeviceProtectedStorage) {
            throw StorageFailure(StorageProblem.UserStorageInaccessible, operation)
        }
        val users = context.getSystemService(UserManager::class.java)
            ?: throw StorageFailure(StorageProblem.UserStorageInaccessible, operation)
        if (!users.isUserUnlocked) throw StorageFailure(StorageProblem.LockedBeforeFirstUnlock, operation)
        if (secrets) {
            val keyguard = context.getSystemService(KeyguardManager::class.java)
                ?: throw StorageFailure(StorageProblem.UserStorageInaccessible, operation)
            if (keyguard.isDeviceLocked) throw StorageFailure(StorageProblem.DeviceLocked, operation)
        }
    }
}
