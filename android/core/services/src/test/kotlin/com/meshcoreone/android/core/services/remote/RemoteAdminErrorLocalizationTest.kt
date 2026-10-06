// PortedFrom: MC1Services/Tests/MC1ServicesTests/Services/ErrorLocalizationTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.remote

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals

/** The ErrorLocalizationTests cases whose error types WP-210 owns (`RoomServerError`, `BinaryProtocolError`). */
class RemoteAdminErrorLocalizationTest {
    @TestFactory
    fun serviceErrorSpotChecks(): List<DynamicTest> = listOf(
        remoteCoreOriginal("ErrorLocalizationTests", "RoomServerError.permissionDenied produces readable description") {
            assertEquals("Permission denied.", RoomServerError.PermissionDenied().errorDescription)
        },
        remoteCoreOriginal("ErrorLocalizationTests", "BinaryProtocolError.timeout produces readable description") {
            assertEquals("Request timed out.", BinaryProtocolError.Timeout().errorDescription)
        },
    )
}
