// AndroidOnly: WP-204 Checked binary AES-GCM envelope authenticating identity, alias, version and lengths.
package com.meshcoreone.android.core.datastore

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

internal object SecretEnvelope {
    private const val MAGIC = 0x4D435331
    private const val VERSION = 1
    private const val MAXIMUM_ALIAS_BYTES = 256
    private const val MAXIMUM_IDENTITY_BYTES = 512
    private val DOMAIN = "MeshCoreOne.Android.Keystore.AES-GCM".toByteArray(Charsets.US_ASCII)
    const val MAXIMUM_BYTES = SecretIdentity.MAXIMUM_SECRET_BYTES + 1024

    fun encrypt(
        identity: SecretIdentity, alias: String, plaintext: ByteArray, cryptography: SecretCryptography,
    ): Bytes {
        identity.validatePlaintext(plaintext)
        val header = header(identity, alias, plaintext.size)
        val encrypted = cryptography.encrypt(alias, plaintext, DOMAIN + header)
        if (encrypted.nonce.size != AndroidKeystoreCryptography.NONCE_BYTES ||
            encrypted.ciphertext.size != plaintext.size + AndroidKeystoreCryptography.TAG_BYTES
        ) {
            throw StorageFailure(StorageProblem.ProviderFailure, StorageOperation.WRITE)
        }
        return Bytes(header + encrypted.nonce.toByteArray() + encrypted.ciphertext.toByteArray())
    }

    fun decrypt(
        identity: SecretIdentity, alias: String, envelope: Bytes, cryptography: SecretCryptography,
    ): ByteArray {
        if (envelope.size > MAXIMUM_BYTES) {
            throw StorageFailure(StorageProblem.StateTooLarge(MAXIMUM_BYTES), StorageOperation.READ)
        }
        val raw = envelope.toByteArray()
        val input = DataInputStream(ByteArrayInputStream(raw))
        try {
            if (input.readInt() != MAGIC) corrupt()
            val version = input.readUnsignedByte()
            if (version != VERSION) {
                throw StorageFailure(StorageProblem.UnsupportedVersion(version), StorageOperation.READ)
            }
            val type = input.readUnsignedByte()
            val actualAlias = input.readCheckedString(MAXIMUM_ALIAS_BYTES)
            val actualIdentity = input.readCheckedString(MAXIMUM_IDENTITY_BYTES)
            val plaintextLength = input.readInt()
            if (type != identity.type || actualAlias != alias || actualIdentity != identity.storageKey ||
                plaintextLength !in 0..SecretIdentity.MAXIMUM_SECRET_BYTES ||
                input.available() != AndroidKeystoreCryptography.NONCE_BYTES +
                AndroidKeystoreCryptography.TAG_BYTES + plaintextLength
            ) corrupt()
            val headerLength = raw.size - input.available()
            val nonce = ByteArray(AndroidKeystoreCryptography.NONCE_BYTES).also(input::readFully)
            val ciphertext = ByteArray(input.available()).also(input::readFully)
            val plaintext = cryptography.decrypt(
                alias, AuthenticatedCiphertext(Bytes(nonce), Bytes(ciphertext)), DOMAIN + raw.copyOfRange(0, headerLength),
            )
            try {
                if (plaintext.size != plaintextLength) corrupt()
                identity.validatePlaintext(plaintext)
                return plaintext
            } catch (failure: StorageFailure) {
                plaintext.fill(0)
                throw failure
            } catch (failure: KeyGenerationFailure) {
                plaintext.fill(0)
                throw StorageFailure(StorageProblem.InvalidSecretValue, StorageOperation.READ, failure)
            }
        } catch (failure: EOFException) {
            throw StorageFailure(StorageProblem.CorruptEnvelope, StorageOperation.READ, failure)
        }
    }

    private fun header(identity: SecretIdentity, alias: String, length: Int): ByteArray =
        ByteArrayOutputStream().also { output ->
            DataOutputStream(output).apply {
                writeInt(MAGIC)
                writeByte(VERSION)
                writeByte(identity.type)
                writeCheckedString(alias, MAXIMUM_ALIAS_BYTES)
                writeCheckedString(identity.storageKey, MAXIMUM_IDENTITY_BYTES)
                writeInt(length)
            }
        }.toByteArray()

    private fun corrupt(): Nothing =
        throw StorageFailure(StorageProblem.CorruptEnvelope, StorageOperation.READ)
}

internal fun DataInputStream.readCheckedString(maximum: Int): String {
    val length = readUnsignedShort()
    if (length > maximum || length > available()) {
        throw StorageFailure(StorageProblem.CorruptEnvelope, StorageOperation.READ)
    }
    return decodeUtf8(ByteArray(length).also(::readFully), StorageOperation.READ)
}

internal fun DataOutputStream.writeCheckedString(value: String, maximum: Int) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    if (bytes.size > maximum) {
        throw StorageFailure(StorageProblem.InvalidSecretIdentity, StorageOperation.WRITE)
    }
    writeShort(bytes.size)
    write(bytes)
}
