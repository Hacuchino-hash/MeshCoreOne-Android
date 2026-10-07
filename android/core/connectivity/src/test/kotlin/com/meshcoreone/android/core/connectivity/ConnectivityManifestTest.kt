// AndroidOnly: WP-206 Declared-manifest boundaries: one connectedDevice service, presence binding, permissions and no blanket CDM exemptions.
package com.meshcoreone.android.core.connectivity

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ConnectivityManifestTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))

    private fun elements(tag: String): List<Element> =
        document.getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }

    private val permissions get() = elements("uses-permission").map { it.getAttributeNS(android, "name") }

    @Test fun `declares exactly one foreground service and it is connectedDevice typed and not exported`() {
        val foreground = elements("service").filter { it.hasAttributeNS(android, "foregroundServiceType") }
        assertEquals(1, foreground.size)
        assertEquals("connectedDevice", foreground.single().getAttributeNS(android, "foregroundServiceType"))
        assertEquals("false", foreground.single().getAttributeNS(android, "exported"))
        assertTrue("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" in permissions)
        assertTrue("android.permission.FOREGROUND_SERVICE" in permissions)
    }

    @Test fun `companion presence service is bindable only by the system`() {
        val presence = elements("service").single { it.getAttributeNS(android, "name").endsWith("MeshCompanionDeviceService") }
        assertEquals("android.permission.BIND_COMPANION_DEVICE_SERVICE", presence.getAttributeNS(android, "permission"))
        val action = (presence.getElementsByTagName("action").item(0) as Element).getAttributeNS(android, "name")
        assertEquals("android.companion.CompanionDeviceService", action)
        assertTrue("android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE" in permissions)
    }

    @Test fun `scan permission is neverForLocation and no location permission is requested`() {
        val scan = elements("uses-permission").single { it.getAttributeNS(android, "name") == "android.permission.BLUETOOTH_SCAN" }
        assertEquals("neverForLocation", scan.getAttributeNS(android, "usesPermissionFlags"))
        assertFalse(permissions.any { it.contains("LOCATION") })
        assertTrue("android.permission.BLUETOOTH_CONNECT" in permissions)
    }

    @Test fun `association is not used as a blanket background or run-in-background exemption`() {
        assertFalse("android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND" in permissions)
        assertFalse("android.permission.REQUEST_COMPANION_USE_DATA_IN_BACKGROUND" in permissions)
        assertFalse("android.permission.REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND" in permissions)
        assertFalse(permissions.any { it.startsWith("android.permission.REQUEST_COMPANION_PROFILE_") })
    }

    @Test fun `LAN transport declares network binding and the API 37 local network permission`() {
        for (permission in listOf("INTERNET", "ACCESS_NETWORK_STATE", "CHANGE_NETWORK_STATE", "ACCESS_LOCAL_NETWORK")) {
            assertTrue("android.permission.$permission" in permissions, permission)
        }
        assertFalse("android.permission.NEARBY_WIFI_DEVICES" in permissions, "TCP to a LAN host needs no WiFi scanning")
    }

    @Test fun `hardware features are optional so the LAN path installs without BLE or CDM`() {
        val features = elements("uses-feature").associate { it.getAttributeNS(android, "name") to it.getAttributeNS(android, "required") }
        assertEquals("false", features["android.hardware.bluetooth_le"])
        assertEquals("false", features["android.software.companion_device_setup"])
    }
}
