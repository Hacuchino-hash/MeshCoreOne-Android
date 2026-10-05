// AndroidOnly: WP-204 Immutable bounded encrypted DataStore state retaining formerly-used-key metadata.
package com.meshcoreone.android.core.datastore

import androidx.datastore.core.Serializer
import com.meshcoreone.android.core.model.SnapshotMap
import com.meshcoreone.android.core.model.snapshotMap
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class SecretArchive(
    val alias: String?,
    val generation: Long,
    entries: Map<String, Bytes>,
    val authentication: AuthenticatedCiphertext? = null,
) {
    val entries: SnapshotMap<String, Bytes> = entries.snapshotMap()
    override fun toString(): String = "SecretArchive([REDACTED])"

    fun authenticated(cryptography: SecretCryptography): SecretArchive {
        val alias = alias ?: throw StorageFailure(StorageProblem.CorruptSecretState, StorageOperation.WRITE)
        val tag = cryptography.encrypt(alias, byteArrayOf(), authenticatedData())
        if (tag.nonce.size != AndroidKeystoreCryptography.NONCE_BYTES ||
            tag.ciphertext.size != AndroidKeystoreCryptography.TAG_BYTES
        ) throw StorageFailure(StorageProblem.ProviderFailure, StorageOperation.WRITE)
        return SecretArchive(alias, generation, entries, tag)
    }

    fun verifyAuthentication(cryptography: SecretCryptography) {
        val alias = alias ?: throw StorageFailure(StorageProblem.CorruptSecretState, StorageOperation.READ)
        val tag = authentication ?: throw StorageFailure(StorageProblem.CorruptSecretState, StorageOperation.READ)
        val decrypted = cryptography.decrypt(alias, tag, authenticatedData())
        try {
            if (decrypted.isNotEmpty()) {
                throw StorageFailure(StorageProblem.CorruptSecretState, StorageOperation.READ)
            }
        } finally {
            decrypted.fill(0)
        }
    }

    private fun authenticatedData(): ByteArray {
        val output = BoundedOutput(SecretArchiveSerializer.MAXIMUM_BYTES)
        val data = DataOutputStream(output)
        data.write("MeshCoreOne.Android.Secret.Archive.v1".toByteArray(Charsets.US_ASCII))
        data.writeLong(generation)
        data.writeCheckedString(alias ?: "", 256)
        data.writeInt(entries.size)
        for ((key, value) in entries.toSortedMap()) {
            data.writeCheckedString(key, 512)
            data.writeInt(value.size)
            data.write(value.toByteArray())
        }
        return output.toByteArray()
    }
}

internal class SecretArchiveSerializer : Serializer<SecretArchive> {
    override val defaultValue: SecretArchive get() = SecretArchive(null, 0, emptyMap())

    override suspend fun readFrom(input: InputStream): SecretArchive {
        val bytes = readBounded(input, MAXIMUM_BYTES, StorageProblem.CorruptSecretState)
        try {
            val data = DataInputStream(ByteArrayInputStream(bytes))
            if (data.readInt() != MAGIC) corrupt()
            val version = data.readUnsignedByte()
            if (version != VERSION) {
                throw StorageFailure(StorageProblem.UnsupportedVersion(version), StorageOperation.READ)
            }
            val generation = data.readLong()
            val alias = data.readCheckedString(256).takeIf(String::isNotEmpty)
            val count = data.readInt()
            if (generation < 0 || count !in 0..MAXIMUM_ENTRIES || alias == null) corrupt()
            val entries = linkedMapOf<String, Bytes>()
            repeat(count) {
                currentCoroutineContext().ensureActive()
                val key = data.readCheckedString(512)
                val length = data.readInt()
                if (key.isEmpty() || length !in 1..SecretEnvelope.MAXIMUM_BYTES ||
                    length > data.available() || entries.containsKey(key)
                ) corrupt()
                identityFromStorageKey(key)
                entries[key] = Bytes(ByteArray(length).also(data::readFully))
            }
            if (data.available() != AndroidKeystoreCryptography.NONCE_BYTES + AndroidKeystoreCryptography.TAG_BYTES) corrupt()
            val nonce = ByteArray(AndroidKeystoreCryptography.NONCE_BYTES).also(data::readFully)
            val tag = ByteArray(AndroidKeystoreCryptography.TAG_BYTES).also(data::readFully)
            return SecretArchive(alias, generation, entries, AuthenticatedCiphertext(Bytes(nonce), Bytes(tag)))
        } catch (failure: EOFException) {
            throw StorageFailure(StorageProblem.CorruptSecretState, StorageOperation.READ, failure)
        }
    }

    override suspend fun writeTo(t: SecretArchive, output: OutputStream) {
        val authentication = t.authentication
        if (t.generation < 0 || t.entries.size > MAXIMUM_ENTRIES || t.alias == null || authentication == null ||
            authentication.nonce.size != AndroidKeystoreCryptography.NONCE_BYTES ||
            authentication.ciphertext.size != AndroidKeystoreCryptography.TAG_BYTES
        ) throw StorageFailure(StorageProblem.CorruptSecretState, StorageOperation.WRITE)
        val buffer = BoundedOutput(MAXIMUM_BYTES)
        val data = DataOutputStream(buffer)
        data.writeInt(MAGIC)
        data.writeByte(VERSION)
        data.writeLong(t.generation)
        data.writeCheckedString(t.alias ?: "", 256)
        data.writeInt(t.entries.size)
        for ((key, value) in t.entries.toSortedMap()) {
            currentCoroutineContext().ensureActive()
            if (value.size !in 1..SecretEnvelope.MAXIMUM_BYTES) {
                throw StorageFailure(StorageProblem.InvalidSecretValue, StorageOperation.WRITE)
            }
            data.writeCheckedString(key, 512)
            data.writeInt(value.size)
            data.write(value.toByteArray())
        }
        data.write(authentication.nonce.toByteArray())
        data.write(authentication.ciphertext.toByteArray())
        currentCoroutineContext().ensureActive()
        buffer.writeTo(output)
    }

    private fun corrupt(): Nothing =
        throw StorageFailure(StorageProblem.CorruptSecretState, StorageOperation.READ)

    companion object {
        const val MAXIMUM_BYTES = 4 * 1_048_576
        const val MAXIMUM_ENTRIES = 4096
        private const val MAGIC = 0x4D434453
        private const val VERSION = 1
    }
}
