// AndroidOnly: WP-102 Typed, non-secret-bearing failures for wire cryptographic primitives.
package com.meshcoreone.android.core.protocol.crypto

import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.security.GeneralSecurityException

enum class KeyFormat(val size: Int) {
    X25519_PRIVATE(32),
    X25519_PUBLIC(32),
    ED25519_SEED(32),
    ED25519_PUBLIC(32),
    ED25519_EXPANDED_PRIVATE(64),
}

sealed class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidKeyLength(val format: KeyFormat, val actual: Int) :
        CryptoException("Invalid $format key length: expected=${format.size}, actual=$actual")

    class SecretTooShort(val actual: Int) :
        CryptoException("AES-128 secret is too short: minimum=16, actual=$actual")

    class InvalidCiphertextLength(val actual: Int) :
        CryptoException("AES ciphertext is not block-aligned: actual=$actual")

    class InvalidTagLength(val actual: Int) :
        CryptoException("Invalid wire authentication tag length: expected=2, actual=$actual")

    class InvalidSignatureLength(val actual: Int) :
        CryptoException("Invalid Ed25519 signature length: expected=64, actual=$actual")

    class InvalidExpandedPrivateKey :
        CryptoException("Ed25519 expanded private scalar is not firmware-clamped")

    class InvalidEd25519PublicKey :
        CryptoException("Ed25519 public key is not a canonical non-identity prime-order point")

    class NonContributoryPublicKey :
        CryptoException("X25519 peer public key does not produce a contributory shared secret")

    class DataTooLarge(val actual: Int) :
        CryptoException("Zero-padded AES input exceeds the supported byte length: actual=$actual")

    class ProviderFailure(val operation: String, cause: GeneralSecurityException) :
        CryptoException("Wire cryptographic operation failed: $operation", cause)
}

internal fun Bytes.requireKeyFormat(format: KeyFormat) {
    if (size != format.size) throw CryptoException.InvalidKeyLength(format, size)
}
