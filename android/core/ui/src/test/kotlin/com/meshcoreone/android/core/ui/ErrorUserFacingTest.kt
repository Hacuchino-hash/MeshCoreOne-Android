// PortedFrom: MC1Tests/Extensions/ErrorUserFacingMessageTests.swift@db14559b39d32322b06477c6ae676112f583db50
// Concrete available faults execute through UiErrorMapper. Copy-only producer bindings remain explicitly pending, not accepted parity.
package com.meshcoreone.android.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreError
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreException
import com.meshcoreone.android.core.datastore.KeyGenerationFailure
import com.meshcoreone.android.core.l10n.generated.AppLocalizableStrings as L
import com.meshcoreone.android.core.l10n.generated.AppSettingsStrings as S
import com.meshcoreone.android.core.model.AppBackupError
import com.meshcoreone.android.core.model.AppBackupException
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.model.ErrorCode
import com.meshcoreone.android.core.protocol.transport.tcp.WiFiTransportError
import com.meshcoreone.android.core.protocol.transport.tcp.WiFiTransportException
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "en-rUS")
class ErrorUserFacingTest : SourceCaseProof() {
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources
    private val mapper = UiErrorMapper()
    private fun message(error: Throwable) = mapper.message(error).resolve(resources)
    private fun copy(type: String, case: String) = ErrorCopy.static(type, case).resolve(resources)

    @OriginalCase("ErrorUserFacingMessageTests::mesh core error dispatches to concrete mapping()")
    @Test fun mesh() = prove { assertEquals(resources.getString(L.errorMeshCoreTimeout), message(MeshCoreException.Timeout())) }
    @OriginalCase("ErrorUserFacingMessageTests::protocol error dispatches to concrete mapping()")
    @Test fun protocol() = prove {
        assertEquals(resources.getString(L.errorDeviceStorageFull), mapper.protocolError(ErrorCode.TABLE_FULL).resolve(resources))
    }
    @ProducerBindingPending("WP-207")
    @OriginalCase("ErrorUserFacingMessageTests::timeout error dispatches to concrete mapping()")
    @Test fun timeoutPolicy() = prove { assertEquals(resources.getString(L.errorTimeoutOperationTimedOut), copy("TimeoutError", "timeout")) }
    @OriginalCase("ErrorUserFacingMessageTests::app backup error dispatches to concrete mapping()")
    @Test fun backup() = prove {
        assertEquals(resources.getString(S.settingsBackupErrorInvalidFile), message(AppBackupException(AppBackupError.InvalidFile)))
    }
    @ProducerBindingPending("WP-205")
    @OriginalCase("ErrorUserFacingMessageTests::ble error dispatches to concrete mapping()")
    @Test fun bleCopyPolicy() = prove {
        assertEquals(L.errorBleConnectionFailed(resources, "peripheral unreachable"),
            ErrorCopy.bleConnectionFailed("peripheral unreachable").resolve(resources))
    }
    @ProducerBindingPending("WP-207")
    @OriginalCase("ErrorUserFacingMessageTests::connection error dispatches to concrete mapping()")
    @Test fun connectionCopyPolicy() = prove {
        assertEquals(L.errorConnectionInitializationFailed(resources, "handshake rejected"),
            ErrorCopy.initializationFailed("handshake rejected").resolve(resources))
    }
    @OriginalCase("ErrorUserFacingMessageTests::wifi transport error dispatches to concrete mapping()")
    @Test fun wifi() = prove {
        assertEquals(resources.getString(L.errorWifiInvalidHost), message(WiFiTransportException(WiFiTransportError.InvalidHost)))
    }
    @ProducerBindingPending("WP-206")
    @OriginalCase("ErrorUserFacingMessageTests::accessory setup kit error dispatches to concrete mapping()")
    @Test fun pickerCopyPolicy() = prove {
        assertEquals(L.errorAccessorySetupPairingFailed(resources, "user declined"),
            ErrorCopy.accessoryPairingFailed("user declined").resolve(resources))
    }
    @ProducerBindingPending("WP-209")
    @OriginalCase("ErrorUserFacingMessageTests::contact service error dispatches to concrete mapping()")
    @Test fun contactCopyPolicy() = prove {
        assertEquals(resources.getString(L.errorContactServiceContactTableFull), copy("ContactServiceError", "contactTableFull"))
    }
    @ProducerBindingPending("WP-208")
    @OriginalCase("ErrorUserFacingMessageTests::message service error dispatches to concrete mapping()")
    @Test fun sendCopyPolicy() = prove {
        val text = copy("MessageServiceError", "sendFailed")
        assertEquals(resources.getString(L.errorMessageServiceSendFailed), text); assertFalse(text.contains("queue rejected"))
    }
    @ProducerBindingPending("WP-209")
    @OriginalCase("ErrorUserFacingMessageTests::channel service error dispatches to concrete mapping()")
    @Test fun channelCopyPolicy() = prove {
        assertEquals(L.errorChannelServiceCircuitBreakerOpen(resources, 3), ErrorCopy.circuitBreakerOpen(3).resolve(resources))
    }
    @ProducerBindingPending("WP-208")
    @OriginalCase("ErrorUserFacingMessageTests::chat send queue service error dispatches to concrete mapping()")
    @Test fun queueCopyPolicy() = prove {
        assertEquals(resources.getString(L.errorChatSendQueueNotConnected), copy("ChatSendQueueServiceError", "notConnected"))
    }
    @ProducerBindingPending("WP-208")
    @OriginalCase("ErrorUserFacingMessageTests::message polling error dispatches to concrete mapping()")
    @Test fun pollingCopyPolicy() = prove {
        assertEquals(resources.getString(L.errorMessagePollingPollingFailed), copy("MessagePollingError", "pollingFailed"))
    }
    @ProducerBindingPending("WP-209")
    @OriginalCase("ErrorUserFacingMessageTests::advertisement error dispatches to concrete mapping()")
    @Test fun advertCopyPolicy() = prove {
        assertEquals(resources.getString(L.errorAdvertisementSendFailed), copy("AdvertisementError", "sendFailed"))
    }
    @ProducerBindingPending("WP-210")
    @OriginalCase("ErrorUserFacingMessageTests::remote node error dispatches to concrete mapping()")
    @Test fun remoteCopyPolicy() = prove {
        val text = copy("RemoteNodeError", "loginFailed")
        assertEquals(resources.getString(L.errorRemoteNodeLoginFailed), text); assertFalse(text.contains("authentication failed"))
    }
    @ProducerBindingPending("WP-210")
    @OriginalCase("ErrorUserFacingMessageTests::room server error dispatches to concrete mapping()")
    @Test fun roomCopyPolicy() = prove {
        assertEquals(resources.getString(L.errorRoomServerSessionNotFound), copy("RoomServerError", "sessionNotFound"))
    }
    @ProducerBindingPending("WP-210")
    @OriginalCase("ErrorUserFacingMessageTests::room server send failed drops raw reason()")
    @Test fun roomFailureReasonPolicy() = prove {
        val text = copy("RoomServerError", "sendFailed")
        assertEquals(resources.getString(L.errorRoomServerSendFailed), text); assertFalse(text.contains("Retry already in progress"))
    }
    @ProducerBindingPending("WP-210")
    @OriginalCase("ErrorUserFacingMessageTests::binary protocol error dispatches to concrete mapping()")
    @Test fun binaryCopyPolicy() = prove {
        assertEquals(resources.getString(L.errorBinaryProtocolTimeout), copy("BinaryProtocolError", "timeout"))
    }
    @OriginalCase("ErrorUserFacingMessageTests::persistence store error dispatches to concrete mapping()")
    @Test fun persistence() = prove {
        assertEquals(L.errorPersistenceSaveFailed(resources, "store unavailable"),
            message(PersistenceStoreException(PersistenceStoreError.SaveFailed("store unavailable"))))
    }
    @ProducerBindingPending("WP-214")
    @OriginalCase("ErrorUserFacingMessageTests::sync coordinator error dispatches to concrete mapping()")
    @Test fun syncCopyPolicy() = prove {
        assertEquals(resources.getString(L.errorSyncCoordinatorAlreadySyncing), copy("SyncCoordinatorError", "alreadySyncing"))
    }
    @ProducerBindingPending("WP-211")
    @OriginalCase("ErrorUserFacingMessageTests::device service error dispatches to concrete mapping()")
    @Test fun deviceCopyPolicy() = prove {
        assertEquals(L.errorDeviceServicePersistenceFailed(resources, "write rejected"),
            ErrorCopy.devicePersistenceFailed("write rejected").resolve(resources))
    }
    @ProducerBindingPending("WP-211")
    @OriginalCase("ErrorUserFacingMessageTests::settings service error dispatches to concrete mapping()")
    @Test fun settingsCopyPolicy() = prove {
        assertEquals(L.errorSettingsVerificationFailed(resources, "915.5", "868.0"),
            ErrorCopy.settingsVerificationFailed("915.5", "868.0").resolve(resources))
    }
    @ProducerBindingPending("WP-204-native-equivalence")
    @OriginalCase("ErrorUserFacingMessageTests::keychain error dispatches to concrete mapping()")
    @Test fun legacyKeychainCopyPolicyIsNotManufacturedAndroidStatus() = prove {
        assertEquals(L.errorKeychainStorageFailed(resources, -25299), ErrorCopy.keychainStorageFailed(-25299).resolve(resources))
    }
    @OriginalCase("ErrorUserFacingMessageTests::key generation error dispatches to concrete mapping()")
    @Test fun keyGeneration() = prove {
        assertEquals(resources.getString(L.errorKeyGenerationReservedPrefix), message(KeyGenerationFailure.ReservedPrefix()))
    }
    @ProducerBindingPending("WP-210")
    @OriginalCase("ErrorUserFacingMessageTests::node config service error dispatches to concrete mapping()")
    @Test fun configCopyPolicy() = prove {
        assertEquals(S.configImportErrorInvalidChannelSecret(resources, 2, 30),
            ErrorCopy.invalidChannelSecret(2, 30).resolve(resources))
    }
    @NativeAdaptation("removed-billing")
    @OriginalCase("ErrorUserFacingMessageTests::store service error dispatches to concrete mapping()")
    @Test fun billingHasNoNativeFaultEntitlementOrUiBranch() = prove {
        assertFalse("StoreServiceError" in ErrorCopy.sourceFamilies)
        assertFalse(ErrorCopy.staticCopies.keys.any { it.sourceType == "StoreServiceError" })
    }
    @ProducerBindingPending("WP-211")
    @OriginalCase("ErrorUserFacingMessageTests::device GPS verification failed picks boolean variant key()")
    @Test fun gpsBooleanPolicy() = prove {
        assertEquals(resources.getString(L.errorSettingsGpsNotSavedExpectedOn), ErrorCopy.gpsVerificationFailed(true).resolve(resources))
        assertEquals(resources.getString(L.errorSettingsGpsNotSavedExpectedOff), ErrorCopy.gpsVerificationFailed(false).resolve(resources))
    }
    @OriginalCase("ErrorUserFacingMessageTests::unmapped error falls back to localized description()")
    @Test fun unmapped() = prove { assertEquals("Something went wrong", message(IllegalStateException("Something went wrong"))) }
    @ProducerBindingPending("WP-208-209-210-211")
    @OriginalCase("ErrorUserFacingMessageTests::session error delegates to central mesh core mapping()")
    @Test fun allNineDelegatedSessionPolicyCases() = prove {
        val errors = listOf(MeshCoreException.Timeout(), MeshCoreException.NotConnected(), MeshCoreException.SessionNotStarted(),
            MeshCoreException.BluetoothPoweredOff(), MeshCoreException.FeatureDisabled(), MeshCoreException.Timeout(),
            MeshCoreException.NotConnected(), MeshCoreException.SessionNotStarted(), MeshCoreException.Timeout())
        val expected = listOf(L.errorMeshCoreTimeout, L.errorMeshCoreNotConnected, L.errorMeshCoreSessionNotStarted,
            L.errorMeshCoreBluetoothPoweredOff, L.errorMeshCoreFeatureDisabled, L.errorMeshCoreTimeout,
            L.errorMeshCoreNotConnected, L.errorMeshCoreSessionNotStarted, L.errorMeshCoreTimeout)
        for ((index, error) in errors.withIndex()) assertEquals(resources.getString(expected[index]), message(error))
    }
    @ProducerBindingPending("WP-208")
    @OriginalCase("ErrorUserFacingMessageTests::persist failed recurses into underlying error()")
    @Test fun nestedQueuePolicy() = prove {
        val underlying = ErrorCopy.static("MessageServiceError", "notConnected")
        assertEquals(L.errorChatSendQueuePersistFailed(resources, underlying.resolve(resources)),
            ErrorCopy.queuePersistFailed(underlying).resolve(resources))
    }
    @OriginalCase("ErrorUserFacingMessageTests::interpolated cases carry associated values()")
    @Test fun interpolation() = prove {
        assertEquals(L.errorMeshCoreParseError(resources, "bad frame"), message(MeshCoreException.ParseError("bad frame")))
        assertEquals(L.errorMeshCoreDataTooLarge(resources, 200, 184), message(MeshCoreException.DataTooLarge(184, 200)))
    }
    @OriginalCase("ErrorUserFacingMessageTests::device error maps known codes through protocol error()")
    @Test fun knownDeviceCodes() = prove {
        for (code in ErrorCode.entries) assertEquals(mapper.protocolError(code).resolve(resources),
            message(MeshCoreException.DeviceError(code.rawValue)))
    }
    @OriginalCase("ErrorUserFacingMessageTests::device error falls back for unknown code()")
    @Test fun unknownDeviceCode() = prove {
        assertEquals(L.errorDeviceUnknown(resources, 0x42), message(MeshCoreException.DeviceError(0x42u)))
    }
    @OriginalCase("ErrorUserFacingMessageTests::connection lost recurses into underlying error()")
    @Test fun lostConnection() = prove {
        assertEquals(L.errorMeshCoreConnectionLost(resources, resources.getString(L.errorMeshCoreBluetoothPoweredOff)),
            message(MeshCoreException.ConnectionLost(MeshCoreException.BluetoothPoweredOff())))
        assertEquals(resources.getString(L.errorMeshCoreConnectionLostNoDetail), message(MeshCoreException.ConnectionLost()))
    }
}
