// PortedFrom: MC1Services/Tests/MC1ServicesTests/Services/NotificationStringProviderTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.notifications

import com.meshcoreone.android.core.protocol.model.ContactType
import kotlin.test.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class NotificationStringProviderTests {
    @TestFactory
    fun defaults(): List<DynamicTest> = listOf(
        originalCase("NotificationStringProviderTests", "Default fallback titles are English") {
            val service = NotificationHarness(this).service
            assertEquals("New Contact Discovered", service.defaultDiscoveryTitle(ContactType.CHAT))
            assertEquals("New Repeater Discovered", service.defaultDiscoveryTitle(ContactType.REPEATER))
            assertEquals("New Room Discovered", service.defaultDiscoveryTitle(ContactType.ROOM))
        },
    )
}
