// PortedFrom: MC1Tests/Extensions/ErrorUserFacingMessageTests.swift@db14559b39d32322b06477c6ae676112f583db50
// Concrete available faults execute through UiErrorMapper. Copy-only producer bindings remain explicitly pending, not accepted parity.
package com.meshcoreone.android.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreError
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreException
import com.meshcoreone.android.core.contracts.domain.errors.DeviceServiceError
import com.meshcoreone.android.core.contracts.domain.errors.DeviceServiceException
import com.meshcoreone.android.core.contracts.domain.errors.MessageServiceError
import com.meshcoreone.android.core.contracts.domain.errors.MessageServiceException
import com.meshcoreone.android.core.contracts.domain.errors.MessagePollingError
import com.meshcoreone.android.core.contracts.domain.errors.MessagePollingException
import com.meshcoreone.android.core.contracts.domain.errors.ChatSendQueueServiceError
import com.meshcoreone.android.core.contracts.domain.errors.ChatSendQueueServiceException
import com.meshcoreone.android.core.contracts.domain.errors.SettingsServiceError
import com.meshcoreone.android.core.contracts.domain.errors.SettingsServiceException
import com.meshcoreone.android.core.datastore.KeyGenerationFailure
import com.meshcoreone.android.core.datastore.StorageFailure
import com.meshcoreone.android.core.datastore.StorageOperation
import com.meshcoreone.android.core.datastore.StorageProblem
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
    @OriginalCase("ErrorUserFacingMessageTests::message service error dispatches to concrete mapping()")
    @Test fun actualSendFaultSuppressesItsRawReasonWithoutLosingThePayload() = prove {
        val fault = MessageServiceError.SendFailed("queue rejected")
        val failure = MessageServiceException(fault)
        val text = message(failure)
        assertEquals(resources.getString(L.errorMessageServiceSendFailed), text); assertFalse(text.contains("queue rejected"))
        assertSame(fault, failure.error); assertEquals("queue rejected", fault.reason)
        assertSame(failure, mapper.present(failure).originalFailure)
    }
    @ProducerBindingPending("WP-209")
    @OriginalCase("ErrorUserFacingMessageTests::channel service error dispatches to concrete mapping()")
    @Test fun channelCopyPolicy() = prove {
        assertEquals(L.errorChannelServiceCircuitBreakerOpen(resources, 3), ErrorCopy.circuitBreakerOpen(3).resolve(resources))
    }
    @OriginalCase("ErrorUserFacingMessageTests::chat send queue service error dispatches to concrete mapping()")
    @Test fun queueConcreteDispatch() = prove {
        assertEquals(resources.getString(L.errorChatSendQueueNotConnected),
            message(ChatSendQueueServiceException(ChatSendQueueServiceError.NotConnected)))
    }
    @OriginalCase("ErrorUserFacingMessageTests::message polling error dispatches to concrete mapping()")
    @Test fun pollingConcreteDispatch() = prove {
        assertEquals(resources.getString(L.errorMessagePollingPollingFailed),
            message(MessagePollingException(MessagePollingError.PollingFailed)))
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
    @OriginalCase("ErrorUserFacingMessageTests::device service error dispatches to concrete mapping()")
    @Test fun deviceConcreteDispatch() = prove {
        val cause = IllegalStateException("synthetic write cause")
        val failure = DeviceServiceException(DeviceServiceError.PersistenceFailed("write rejected"), cause)
        assertEquals(L.errorDeviceServicePersistenceFailed(resources, "write rejected"),
            message(failure))
        assertSame(cause, mapper.present(failure).originalFailure.cause)
    }
    @OriginalCase("ErrorUserFacingMessageTests::settings service error dispatches to concrete mapping()")
    @Test fun settingsConcreteDispatch() = prove {
        val fault = SettingsServiceError.VerificationFailed("915.5", "868.0")
        val failure = SettingsServiceException(fault)
        assertEquals(L.errorSettingsVerificationFailed(resources, "915.5", "868.0"),
            message(failure))
        assertEquals("915.5", fault.expected); assertEquals("868.0", fault.actual)
    }
    @NativeAdaptation("native-storage-family-without-Apple-status")
    @OriginalCase("ErrorUserFacingMessageTests::keychain error dispatches to concrete mapping()")
    @Test fun actualNativeStorageFaultDispatchesWithoutManufacturedAppleStatus() = prove {
        val failure = StorageFailure(StorageProblem.SecretAlreadyExists, StorageOperation.WRITE)
        assertEquals(resources.getString(R.string.ui_storage_save_failed) + "\n" +
            resources.getString(R.string.ui_storage_already_exists), message(failure))
        assertEquals(NativeStorageErrorFamily.STORAGE, failure.nativeFamily)
        assertSame(failure, mapper.present(failure).originalFailure)
        assertEquals(UiStorageIssue(StorageOperation.WRITE, StorageProblem.SecretAlreadyExists), mapper.present(failure).content.storageIssue)
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
    @OriginalCase("ErrorUserFacingMessageTests::device GPS verification failed picks boolean variant key()")
    @Test fun gpsConcreteBooleanVariants() = prove {
        val expectedOn = SettingsServiceError.DeviceGPSVerificationFailed(true, false)
        val expectedOff = SettingsServiceError.DeviceGPSVerificationFailed(false, true)
        assertEquals(resources.getString(L.errorSettingsGpsNotSavedExpectedOn), message(SettingsServiceException(expectedOn)))
        assertEquals(resources.getString(L.errorSettingsGpsNotSavedExpectedOff), message(SettingsServiceException(expectedOff)))
        assertFalse(expectedOn.actualEnabled); assertTrue(expectedOff.actualEnabled)
    }
    @OriginalCase("ErrorUserFacingMessageTests::unmapped error falls back to localized description()")
    @Test fun unmapped() = prove { assertEquals("Something went wrong", message(IllegalStateException("Something went wrong"))) }
    @ProducerBindingPending("WP-209-210")
    @OriginalCase("ErrorUserFacingMessageTests::session error delegates to central mesh core mapping()")
    @Test fun availableSessionWrappersDelegateWhileSixOriginalProducerWrappersRemainBlocked() = prove {
        val bindings = listOf(
            MessageServiceException(MessageServiceError.SessionError(MeshCoreException.NotConnected())) to L.errorMeshCoreNotConnected,
            MessagePollingException(MessagePollingError.SessionError(MeshCoreException.BluetoothPoweredOff())) to L.errorMeshCoreBluetoothPoweredOff,
            SettingsServiceException(SettingsServiceError.SessionError(MeshCoreException.Timeout())) to L.errorMeshCoreTimeout,
        )
        for ((failure, expected) in bindings) {
            assertEquals(resources.getString(expected), message(failure))
            assertSame(failure, mapper.present(failure).originalFailure)
            assertNotNull(failure.cause)
        }
    }
    @OriginalCase("ErrorUserFacingMessageTests::persist failed recurses into underlying error()")
    @Test fun actualQueuePersistFailureRecursesThroughTheOriginalMessageFault() = prove {
        val underlying = MessageServiceException(MessageServiceError.NotConnected)
        val failure = ChatSendQueueServiceException(ChatSendQueueServiceError.PersistFailed(underlying))
        assertSame(underlying, failure.cause)
        assertEquals(L.errorChatSendQueuePersistFailed(resources, resources.getString(L.errorMessageServiceNotConnected)),
            message(failure))
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
