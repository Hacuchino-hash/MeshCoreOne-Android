// PortedFrom: MC1Services/Sources/MC1Services/Utilities/ContactResult.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.contacts

import com.meshcoreone.android.core.model.uppercaseHexString
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.ContactType

/** Parsed contact identity recovered from a deep-link URL or a contact share token. */
data class ContactResult(
    val name: String,
    val publicKey: Bytes,
    val contactType: ContactType,
) {
    /** Identifiable id: the public key as uppercase hex. */
    val id: String get() = publicKey.uppercaseHexString()
}
