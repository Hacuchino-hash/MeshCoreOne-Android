// AndroidOnly: WP-204 Typed, non-secret-bearing storage failures and native reporting.
package com.meshcoreone.android.core.datastore

import android.util.Log

enum class StorageOperation { READ, OBSERVE, WRITE, DELETE, ROTATE, EXPORT, OPEN }

sealed interface StorageProblem {
    data object LockedBeforeFirstUnlock : StorageProblem
    data object DeviceLocked : StorageProblem
    data object UserStorageInaccessible : StorageProblem
    data object PermissionDenied : StorageProblem
    data object IoFailure : StorageProblem
    data object CorruptPreferences : StorageProblem
    data object CorruptSecretState : StorageProblem
    data object CorruptEnvelope : StorageProblem
    data object AuthenticationFailed : StorageProblem
    data object FormerKeyMissing : StorageProblem
    data object KeyPermanentlyInvalidated : StorageProblem
    data object CorruptKeystoreKey : StorageProblem
    data object ProviderFailure : StorageProblem
    data object DuplicateOwner : StorageProblem
    data object OwnerClosed : StorageProblem
    data object InvalidSecretIdentity : StorageProblem
    data object InvalidSecretValue : StorageProblem
    data object SecretAlreadyExists : StorageProblem
    data object SecretNotFound : StorageProblem
    data class UnsupportedVersion(val version: Int) : StorageProblem
    data class StateTooLarge(val maximumBytes: Int) : StorageProblem
    data class PreferenceTypeMismatch(val key: String) : StorageProblem
    data class PreferenceHasNoDefault(val key: String) : StorageProblem
    data class InvalidPreference(val key: String) : StorageProblem
}

class StorageFailure(
    val problem: StorageProblem,
    val operation: StorageOperation,
    cause: Throwable? = null,
) : Exception("${operation.name}:${problem.javaClass.simpleName}", cause)

fun interface StorageIssueReporter {
    fun report(failure: StorageFailure)
}

object AndroidStorageIssueReporter : StorageIssueReporter {
    override fun report(failure: StorageFailure) {
        // Provider causes can contain paths, aliases or credential material.
        Log.e("MeshCoreOne.Storage", "${failure.operation.name}:${failure.problem.javaClass.simpleName}")
    }
}

sealed interface StoreState<out T> {
    data object Loading : StoreState<Nothing>
    data class Ready<T>(val value: T) : StoreState<T>
    data class Failed(val failure: StorageFailure) : StoreState<Nothing>
}
