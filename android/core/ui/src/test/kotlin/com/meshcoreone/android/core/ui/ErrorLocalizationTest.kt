// PortedFrom: MC1Services/Tests/MC1ServicesTests/Services/ErrorLocalizationTests.swift@db14559b39d32322b06477c6ae676112f583db50
// Pending producer annotations distinguish asserted copy policies from unavailable concrete service-error wiring.
package com.meshcoreone.android.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreError
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreException
import com.meshcoreone.android.core.l10n.generated.AppLocalizableStrings as L
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.model.ErrorCode
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "en-rUS")
class ErrorLocalizationTest : SourceCaseProof() {
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources
    private val mapper = UiErrorMapper()
    private fun copy(type: String, case: String) = ErrorCopy.static(type, case).resolve(resources)

    @OriginalCase("ErrorLocalizationTests::MeshCoreError.timeout produces human-readable description()")
    @Test fun timeout() = prove {
        assertEquals("The operation timed out. Please try again.", MeshCoreException.Timeout().sourceEnglishDescription())
    }
    @OriginalCase("ErrorLocalizationTests::MeshCoreError.deviceError maps known firmware codes(code : UInt8 , expected : String)", 6)
    @Test fun knownCodes() = prove(6) {
        val expected = listOf("Command not supported by device firmware.", "Item not found on device.",
            "Device storage is full.", "Device is in an invalid state for this operation.",
            "Device file system error.", "Invalid parameter sent to device.")
        for ((index, description) in expected.withIndex()) {
            assertEquals(description, MeshCoreException.DeviceError((index + 1).toUByte()).sourceEnglishDescription())
        }
    }
    @OriginalCase("ErrorLocalizationTests::MeshCoreError.deviceError falls back for unknown codes()")
    @Test fun unknownCode() = prove { assertEquals("Device error (code 10).", MeshCoreException.DeviceError(10u).sourceEnglishDescription()) }
    @OriginalCase("ErrorLocalizationTests::MeshCoreError.deviceError handles code zero()")
    @Test fun zeroCode() = prove { assertEquals("Device error (code 0).", MeshCoreException.DeviceError(0u).sourceEnglishDescription()) }
    @OriginalCase("ErrorLocalizationTests::MeshCoreError bluetooth errors produce readable descriptions()")
    @Test fun bluetooth() = prove {
        assertEquals("Bluetooth is not available on this device.", MeshCoreException.BluetoothUnavailable().sourceEnglishDescription())
        assertEquals("Bluetooth permission is required. Please enable it in Settings.", MeshCoreException.BluetoothUnauthorized().sourceEnglishDescription())
        assertEquals("Bluetooth is turned off. Please enable Bluetooth to connect.", MeshCoreException.BluetoothPoweredOff().sourceEnglishDescription())
    }
    @OriginalCase("ErrorLocalizationTests::MeshCoreError.connectionLost includes underlying error when present()")
    @Test fun underlyingConnection() = prove {
        val description = MeshCoreException.ConnectionLost(IllegalStateException("link dropped")).sourceEnglishDescription()
        assertTrue(description.contains("Connection to device was lost")); assertTrue(description.contains("link dropped"))
    }
    @OriginalCase("ErrorLocalizationTests::MeshCoreError.connectionLost without underlying error()")
    @Test fun connectionWithoutCause() = prove {
        assertEquals("Connection to device was lost.", MeshCoreException.ConnectionLost().sourceEnglishDescription())
    }
    @OriginalCase("ErrorLocalizationTests::MeshCoreError.featureDisabled produces readable description()")
    @Test fun disabled() = prove {
        assertEquals("This feature is disabled on the device.", MeshCoreException.FeatureDisabled().sourceEnglishDescription())
    }
    @OriginalCase("ErrorLocalizationTests::MeshCoreError.sessionNotStarted produces readable description()")
    @Test fun sessionNotStarted() = prove {
        assertEquals("Session has not been started.", MeshCoreException.SessionNotStarted().sourceEnglishDescription())
    }
    @OriginalCase("ErrorLocalizationTests::ProtocolError cases produce non-empty, readable descriptions(protocolError : ProtocolError)", 6)
    @Test fun protocolDescriptions() = prove(6) {
        for (error in ErrorCode.entries) {
            val description = mapper.protocolError(error).resolve(resources)
            assertTrue(description.isNotEmpty()); assertFalse(description.contains("ProtocolError"))
        }
    }
    @ProducerBindingPending("WP-208")
    @OriginalCase("ErrorLocalizationTests::MessageServiceError.sessionError passes through MeshCoreError description()")
    @Test fun messageDelegationPolicy() = prove {
        val error = MeshCoreException.DeviceError(3u)
        assertEquals("Device storage is full.", error.sourceEnglishDescription())
        assertEquals(mapper.deviceError(3u).resolve(resources), mapper.message(error).resolve(resources))
    }
    @ProducerBindingPending("WP-209")
    @OriginalCase("ErrorLocalizationTests::ChannelServiceError.sessionError passes through MeshCoreError description()")
    @Test fun channelDelegationPolicy() = prove {
        assertEquals("The operation timed out. Please try again.", MeshCoreException.Timeout().sourceEnglishDescription())
        assertEquals(resources.getString(L.errorMeshCoreTimeout), mapper.message(MeshCoreException.Timeout()).resolve(resources))
    }
    @ProducerBindingPending("WP-211")
    @OriginalCase("ErrorLocalizationTests::SettingsServiceError.sessionError passes through without prefix()")
    @Test fun settingsDelegationPolicy() = prove {
        val description = MeshCoreException.NotConnected().sourceEnglishDescription()
        assertEquals("Not connected to device.", description); assertFalse(description.contains("Session error:"))
    }
    @ProducerBindingPending("WP-211")
    @OriginalCase("ErrorLocalizationTests::SettingsServiceError.deviceGPSVerificationFailed is human-readable()")
    @Test fun gpsDescriptionPolicy() = prove {
        val copy = ErrorCopy.gpsVerificationFailed(false).resolve(resources)
        assertEquals(resources.getString(L.errorSettingsGpsNotSavedExpectedOff), copy)
        assertTrue(copy.isNotEmpty()); assertFalse(copy.contains("SettingsServiceError"))
    }
    @ProducerBindingPending("WP-210")
    @OriginalCase("ErrorLocalizationTests::RemoteNodeError.sessionError passes through without prefix()")
    @Test fun remoteDelegationPolicy() = prove {
        val description = MeshCoreException.BluetoothPoweredOff().sourceEnglishDescription()
        assertEquals("Bluetooth is turned off. Please enable Bluetooth to connect.", description)
        assertFalse(description.contains("Session error:"))
    }
    @ProducerBindingPending("WP-209")
    @OriginalCase("ErrorLocalizationTests::AdvertisementError.notConnected produces readable description()")
    @Test fun advertisementPolicy() = prove {
        assertEquals(resources.getString(L.errorAdvertisementNotConnected), copy("AdvertisementError", "notConnected"))
    }
    @ProducerBindingPending("WP-210")
    @OriginalCase("ErrorLocalizationTests::RoomServerError.permissionDenied produces readable description()")
    @Test fun roomPolicy() = prove {
        assertEquals(resources.getString(L.errorRoomServerPermissionDenied), copy("RoomServerError", "permissionDenied"))
    }
    @ProducerBindingPending("WP-210")
    @OriginalCase("ErrorLocalizationTests::BinaryProtocolError.timeout produces readable description()")
    @Test fun binaryPolicy() = prove {
        assertEquals(resources.getString(L.errorBinaryProtocolTimeout), copy("BinaryProtocolError", "timeout"))
    }
    @ProducerBindingPending("WP-214")
    @OriginalCase("ErrorLocalizationTests::SyncCoordinatorError.alreadySyncing produces readable description()")
    @Test fun syncPolicy() = prove {
        assertEquals(resources.getString(L.errorSyncCoordinatorAlreadySyncing), copy("SyncCoordinatorError", "alreadySyncing"))
    }
    @OriginalCase("ErrorLocalizationTests::PersistenceStoreError.contactNotFound produces readable description()")
    @Test fun persistenceDescription() = prove {
        val failure = PersistenceStoreException(PersistenceStoreError.ContactNotFound)
        assertEquals(resources.getString(L.errorPersistenceContactNotFound), mapper.message(failure).resolve(resources))
    }
}
