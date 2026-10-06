// PortedFrom: MC1Services/Sources/MC1Services/Services/LoginResult.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.model.RoomPermissionLevel
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.time.Instant

data class LoginResult(
    val success: Boolean,
    val isAdmin: Boolean,
    val aclPermissions: UByte?,
    val publicKeyPrefix: Bytes,
    /** The remote node's RTC reading from the login response, if carried. */
    val serverTime: Instant? = null,
) {
    val permissionLevel: RoomPermissionLevel
        get() = if (isAdmin) RoomPermissionLevel.ADMIN
        else RoomPermissionLevel.fromRawValue(aclPermissions ?: 0u) ?: RoomPermissionLevel.GUEST
}
