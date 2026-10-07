// PortedFrom: MC1Services/Tests/MC1ServicesTests/Services/AccessorySetupKitDiscoveryCriteriaTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.connectivity

import com.meshcoreone.android.core.ble.NusUuid
import com.meshcoreone.android.core.connectivity.pairing.CompanionDiscoveryCriteria
import com.meshcoreone.android.core.connectivity.support.OriginalCase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class CompanionDiscoveryCriteriaTest {
    @Test @OriginalCase("AccessorySetupKitDiscoveryCriteriaTests::picker discovers by Nordic UART service UUID only()")
    fun `picker discovers by Nordic UART service UUID only`() {
        assertEquals(NusUuid.SERVICE, CompanionDiscoveryCriteria.bluetoothServiceUuid)
        assertEquals("6E400001-B5A3-F393-E0A9-E50E24DCCA9E", CompanionDiscoveryCriteria.bluetoothServiceUUID)
    }

    @Test @OriginalCase("AccessorySetupKitDiscoveryCriteriaTests::picker opts into filtered discovery so matches can be relabeled with advertised names()")
    fun `picker opts into filtered discovery so matches can be relabeled with advertised names`() {
        assertTrue(CompanionDiscoveryCriteria.usesFilteredDiscovery)
    }
}
