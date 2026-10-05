// AndroidOnly: WP-204 Deterministic key-access failures around real JCA AES-GCM and real DataStore.
package com.meshcoreone.android.core.datastore

import android.security.keystore.KeyGenParameterSpec
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class OriginalCase(val id: String, val parameterCount: Int = 1)

internal class RecordingReporter : StorageIssueReporter {
    val failures = mutableListOf<StorageFailure>()
    @Synchronized
    override fun report(failure: StorageFailure) {
        failures += failure
    }
}

internal class TestKeystoreAccess : KeystoreKeyAccess {
    val keys = mutableMapOf<String, SecretKey>()
    val specifications = mutableListOf<KeyGenParameterSpec>()
    val deleted = mutableListOf<String>()
    var findFailure: Throwable? = null
    var generateFailure: Throwable? = null
    var deleteFailure: Throwable? = null

    @Synchronized
    override fun find(alias: String): SecretKey? {
        findFailure?.let { throw it }
        return keys[alias]
    }

    @Synchronized
    override fun generate(specification: KeyGenParameterSpec): SecretKey {
        generateFailure?.let { throw it }
        check(!keys.containsKey(specification.keystoreAlias))
        specifications += specification
        return KeyGenerator.getInstance("AES").run {
            init(256)
            generateKey()
        }.also { keys[specification.keystoreAlias] = it }
    }

    @Synchronized
    override fun delete(alias: String) {
        deleteFailure?.let { throw it }
        keys.remove(alias)
        deleted += alias
    }
}

internal class StorageHarness(
    val directory: File,
    val platform: TestKeystoreAccess = TestKeystoreAccess(),
    val reporter: RecordingReporter = RecordingReporter(),
    val preferenceAccess: StorageAccess = StorageAccess {},
    val secretAccess: StorageAccess = preferenceAccess,
) {
    val cryptography = AndroidKeystoreCryptography(platform, secretAccess)
    var owner = open()
        private set

    private fun open(): MeshCoreStorage {
        val hash = MessageDigest.getInstance("SHA-256").digest(directory.canonicalPath.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        return MeshCoreStorage.createOwned(
            directory, cryptography, reporter, preferenceAccess, secretAccess, Dispatchers.IO,
            "com.meshcoreone.android.fixture.$hash",
        )
    }

    suspend fun reopen() {
        owner.close()
        owner = open()
    }

    suspend fun close() = owner.close()
}

internal suspend fun <T> withStorage(
    directory: File,
    block: suspend (StorageHarness) -> T,
): T {
    val harness = StorageHarness(directory)
    return try {
        block(harness)
    } finally {
        harness.close()
    }
}

internal fun radioId(value: Long = 1): RadioId = RadioId(UUID(0, value))
internal fun nodePublicKey(value: Int = 1): Bytes = Bytes(ByteArray(32) { (it + value).toByte() })

internal fun bytesFromHex(hex: String): Bytes =
    Bytes(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())

internal val rfc8032Seed: Bytes =
    bytesFromHex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
internal val rfc8032PublicKey: Bytes =
    bytesFromHex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")

internal suspend fun expectStorageFailure(problem: StorageProblem, block: suspend () -> Unit): StorageFailure {
    val failure = assertFailsWith<StorageFailure> { block() }
    assertEquals(problem, failure.problem)
    return failure
}
