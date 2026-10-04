// AndroidOnly: WP-102 Vetted seed and firmware-expanded Ed25519 primitives; radio signing remains WP-107.
package com.meshcoreone.android.core.protocol.crypto

import com.meshcoreone.android.core.protocol.bytes.Bytes
import org.bouncycastle.math.ec.rfc8032.Ed25519

object Ed25519Crypto {
    const val seedSize = 32
    const val expandedPrivateKeySize = 64
    const val publicKeySize = 32
    const val signatureSize = 64

    fun expandSeed(seed: Bytes): Bytes {
        seed.requireKeyFormat(KeyFormat.ED25519_SEED)
        val rawSeed = seed.toByteArray()
        val expanded = ByteArray(expandedPrivateKeySize)
        try {
            Ed25519.ExpandedKey.expandPrivateKey(rawSeed, 0, expanded, 0)
            Ed25519.ExpandedKey.prune(expanded, 0)
            return Bytes(expanded)
        } finally {
            rawSeed.fill(0)
            expanded.fill(0)
        }
    }

    fun publicKeyFromSeed(seed: Bytes): Bytes {
        seed.requireKeyFormat(KeyFormat.ED25519_SEED)
        val rawSeed = seed.toByteArray()
        val publicKey = ByteArray(publicKeySize)
        try {
            Ed25519.generatePublicKey(rawSeed, 0, publicKey, 0)
            return Bytes(publicKey)
        } finally {
            rawSeed.fill(0)
        }
    }

    fun publicKeyFromExpanded(expandedPrivateKey: Bytes): Bytes {
        validateExpandedPrivateKey(expandedPrivateKey)
        val expanded = expandedPrivateKey.toByteArray()
        val publicKey = ByteArray(publicKeySize)
        try {
            Ed25519.ExpandedKey.generatePublicKey(expanded, 0, publicKey, 0)
            return Bytes(publicKey)
        } finally {
            expanded.fill(0)
        }
    }

    fun x25519PrivateKeyFromSeed(seed: Bytes): Bytes = x25519PrivateKeyFromExpanded(expandSeed(seed))

    fun x25519PrivateKeyFromExpanded(expandedPrivateKey: Bytes): Bytes {
        validateExpandedPrivateKey(expandedPrivateKey)
        return expandedPrivateKey.prefix(KeyFormat.X25519_PRIVATE.size)
    }

    fun signWithSeed(message: Bytes, seed: Bytes): Bytes {
        seed.requireKeyFormat(KeyFormat.ED25519_SEED)
        val rawSeed = seed.toByteArray()
        val signature = ByteArray(signatureSize)
        try {
            Ed25519.sign(rawSeed, 0, message.toByteArray(), 0, message.size, signature, 0)
            return Bytes(signature)
        } finally {
            rawSeed.fill(0)
        }
    }

    fun signWithExpanded(message: Bytes, expandedPrivateKey: Bytes): Bytes {
        validateExpandedPrivateKey(expandedPrivateKey)
        val expanded = expandedPrivateKey.toByteArray()
        val signature = ByteArray(signatureSize)
        try {
            Ed25519.ExpandedKey.sign(expanded, 0, message.toByteArray(), 0, message.size, signature, 0)
            return Bytes(signature)
        } finally {
            expanded.fill(0)
        }
    }

    fun verify(message: Bytes, signature: Bytes, publicKey: Bytes): Boolean {
        publicKey.requireKeyFormat(KeyFormat.ED25519_PUBLIC)
        if (signature.size != signatureSize) throw CryptoException.InvalidSignatureLength(signature.size)
        if (!Ed25519.validatePublicKeyFull(publicKey.toByteArray(), 0)) {
            throw CryptoException.InvalidEd25519PublicKey()
        }
        return Ed25519.verify(signature.toByteArray(), 0, publicKey.toByteArray(), 0, message.toByteArray(), 0, message.size)
    }

    fun validateExpandedPrivateKey(expandedPrivateKey: Bytes) {
        expandedPrivateKey.requireKeyFormat(KeyFormat.ED25519_EXPANDED_PRIVATE)
        val first = expandedPrivateKey[0].toInt()
        val last = expandedPrivateKey[31].toInt()
        if ((first and 0x07) != 0 || (last and 0x80) != 0 || (last and 0x40) == 0) {
            throw CryptoException.InvalidExpandedPrivateKey()
        }
    }
}
