// AndroidOnly: WP-304 Real Runtime/BLE consumers and explicitly uncredited service-payload copy policies.
package com.meshcoreone.android.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meshcoreone.android.core.ble.*
import com.meshcoreone.android.core.contracts.domain.errors.*
import com.meshcoreone.android.core.l10n.generated.AppLocalizableStrings as L
import com.meshcoreone.android.core.l10n.generated.AppSettingsStrings as S
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.runtime.ConnectionError
import com.meshcoreone.android.core.runtime.TimeoutError
import com.meshcoreone.android.core.connectivity.pairing.CompanionSetupError
import com.meshcoreone.android.core.services.sync.SyncCoordinatorError
import java.io.IOException
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "en-rUS")
class SourceFaultPresentationTest {
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources
    private val mapper = UiErrorMapper(reporter = UiErrorReporter {})

    @Test fun everyCompanionProducerDispatchesWithoutConfusingNativeRemovalAndPickerCancellation() {
        val reason = "pairing rejected \u4e2d\u6587"
        val cases = listOf(
            CompanionSetupError.SessionNotActive() to ErrorCopy.static("AccessorySetupKitError", "sessionNotActive"),
            CompanionSetupError.SessionInvalidated() to ErrorCopy.static("AccessorySetupKitError", "sessionInvalidated"),
            CompanionSetupError.PickerDismissed() to ErrorCopy.static("AccessorySetupKitError", "pickerDismissed"),
            CompanionSetupError.PickerRestricted() to ErrorCopy.static("AccessorySetupKitError", "pickerRestricted"),
            CompanionSetupError.PickerAlreadyActive() to ErrorCopy.static("AccessorySetupKitError", "pickerAlreadyActive"),
            CompanionSetupError.PairingFailed(reason) to ErrorCopy.accessoryPairingFailed(reason),
            CompanionSetupError.NoBluetoothIdentifier() to ErrorCopy.static("AccessorySetupKitError", "noBluetoothIdentifier"),
            CompanionSetupError.DiscoveryTimeout() to ErrorCopy.static("AccessorySetupKitError", "discoveryTimeout"),
            CompanionSetupError.ConnectionFailed() to ErrorCopy.static("AccessorySetupKitError", "connectionFailed"),
            CompanionSetupError.UserCancelled() to ErrorCopy.static("RemoteNodeError", "cancelled"),
        )
        assertEquals(10, cases.size)
        val cause = IOException("private producer cause")
        for ((failure, expected) in cases) {
            failure.initCause(cause)
            val presented = mapper.present(failure)
            assertSame(failure, presented.originalFailure)
            assertSame(cause, presented.originalFailure.cause)
            assertEquals(failure.sourceServiceFault, presented.content.sourceFault)
            assertEquals(expected.resolve(resources), presented.content.message.resolve(resources))
            assertEquals(UiRecovery.INSPECT_FAILURE, presented.content.recovery)
            assertFalse(presented.content.message.resolve(resources).contains("private producer cause"))
        }
        assertEquals(CompanionSetupFault.UserCancelled, cases.last().first.sourceServiceFault)
        assertNotEquals(cases[2].second.resolve(resources), cases.last().second.resolve(resources))
        assertFailsWith<CancellationException> { mapper.present(CancellationException("cooperative cancel")) }
    }

    @Test fun everySyncProducerDispatchesWithReadableSourceDescriptionAndUnmodifiedReason() {
        val reason = "storage unavailable \u4e2d\u6587"
        val cases = listOf(
            SyncCoordinatorError.NotConnected() to ErrorCopy.static("SyncCoordinatorError", "notConnected"),
            SyncCoordinatorError.SyncFailed(reason) to ErrorCopy.syncFailed(reason),
            SyncCoordinatorError.AlreadySyncing() to ErrorCopy.static("SyncCoordinatorError", "alreadySyncing"),
        )
        assertEquals(3, cases.size)
        for ((failure, expected) in cases) {
            val presented = mapper.present(failure)
            assertSame(failure, presented.originalFailure)
            assertEquals(failure.sourceServiceFault, presented.content.sourceFault)
            assertEquals(expected.resolve(resources), presented.content.message.resolve(resources))
            assertEquals(UiRecovery.INSPECT_FAILURE, presented.content.recovery)
            assertNotNull(failure.message)
        }
        assertEquals(SyncFault.SyncFailed(reason), cases[1].first.sourceServiceFault)
        assertTrue(mapper.message(cases[1].first).resolve(resources).contains(reason))
    }

    // This fixture tests neutral copy policy only; no original service-family receipt is emitted.
    private class NeutralPolicyFixture(override val sourceServiceFault: SourceServiceFault) :
        Exception("private fixture diagnostic"), SourceServiceFaultCarrier

    @Test fun actualRuntimeFailuresUseLocalizedCopyAndKeepTheirOriginalNeutralPayload() {
        val cause = IOException("synthetic original cause")
        val cases = listOf(
            ConnectionError.ConnectionFailed("unreachable", cause) to ErrorCopy.connectionFailed("unreachable"),
            ConnectionError.DeviceNotFound() to ErrorCopy.static("ConnectionError", "deviceNotFound"),
            ConnectionError.NotConnected() to ErrorCopy.static("ConnectionError", "notConnected"),
            ConnectionError.InitializationFailed("rejected", cause) to ErrorCopy.initializationFailed("rejected"),
            ConnectionError.UnsupportedCapability("private capability") to UiText.Resource(L.commonErrorFailedToLoad),
            ConnectionError.ForeignPhysicalOwner() to UiText.Resource(L.commonErrorFailedToLoad),
            ConnectionError.RetainedPhysicalLink() to UiText.Resource(L.commonErrorFailedToLoad),
            ConnectionError.InvalidIdentity() to UiText.Resource(L.commonErrorFailedToLoad),
            ConnectionError.FactoryOwnershipViolation() to UiText.Resource(L.commonErrorFailedToLoad),
        )
        assertEquals(9, cases.size)
        for ((failure, expected) in cases) {
            val presented = mapper.present(failure)
            assertSame(failure, presented.originalFailure)
            assertEquals(failure.sourceServiceFault, presented.content.sourceFault)
            assertEquals(expected.resolve(resources), presented.content.message.resolve(resources))
            assertEquals(if (failure is ConnectionError.NotConnected) UiRecovery.CONNECT else UiRecovery.INSPECT_FAILURE,
                presented.content.recovery)
            assertFalse(presented.content.message.resolve(resources).contains("private capability"))
        }
        for (operation in listOf("session.start", "queryDevice", "getTime")) {
            val failure = TimeoutError(operation, 5.seconds)
            val presented = mapper.present(failure)
            assertEquals(resources.getString(L.errorTimeoutOperationTimedOut), presented.content.message.resolve(resources))
            assertFalse(presented.content.message.resolve(resources).contains(operation))
            assertEquals(RuntimeTimeoutFault(operation, 5.seconds), presented.content.sourceFault)
            assertEquals(UiRecovery.RETRY, presented.content.recovery)
        }
    }

    @Test fun everyOriginalBleCaseDispatchesAndNativeMetadataRemainsAvailableWithoutDeveloperCopy() {
        val reason = "synthetic source reason"
        val cases = listOf(
            BleError.BluetoothUnavailable to ErrorCopy.static("BLEError", "bluetoothUnavailable"),
            BleError.BluetoothUnauthorized to ErrorCopy.static("BLEError", "bluetoothUnauthorized"),
            BleError.BluetoothPoweredOff to ErrorCopy.static("BLEError", "bluetoothPoweredOff"),
            BleError.DeviceNotFound to ErrorCopy.static("BLEError", "deviceNotFound"),
            BleError.ConnectionFailed(reason) to ErrorCopy.bleConnectionFailed(reason),
            BleError.ConnectionTimeout to ErrorCopy.static("BLEError", "connectionTimeout"),
            BleError.NotConnected to ErrorCopy.static("BLEError", "notConnected"),
            BleError.CharacteristicNotFound to ErrorCopy.static("BLEError", "characteristicNotFound"),
            BleError.WriteError(reason) to ErrorCopy.bleWriteError(reason),
            BleError.InvalidResponse to ErrorCopy.static("BLEError", "invalidResponse"),
            BleError.OperationTimeout to ErrorCopy.static("BLEError", "operationTimeout"),
            BleError.AuthenticationFailed to ErrorCopy.static("BLEError", "authenticationFailed"),
            BleError.PairingFailed(reason) to ErrorCopy.blePairingFailed(reason),
            BleError.DeviceConnectedToOtherApp to ErrorCopy.static("BLEError", "deviceConnectedToOtherApp"),
        )
        assertEquals(14, cases.size)
        val cause = IOException("private native diagnostic")
        for ((error, expected) in cases) {
            val failure = BleTransportException(error, GattOperationKind.Write, 133, cause, GattStatusDomain.Att)
            val presented = mapper.present(failure)
            assertSame(failure, presented.originalFailure)
            assertSame(cause, presented.originalFailure.cause)
            assertSame(error, failure.error)
            assertEquals(expected.resolve(resources), presented.content.message.resolve(resources))
            val fault = assertIs<BleTransportFault>(presented.content.sourceFault)
            assertEquals(failure.sourceServiceFault, fault)
            assertEquals(BleFaultOperation.Write, fault.operation)
            assertEquals(133, fault.status)
            assertEquals(BleFaultStatusDomain.Att, fault.statusDomain)
            assertEquals(failure.recovery.name, fault.recovery.name)
            assertFalse(presented.content.message.resolve(resources).contains("private native diagnostic"))
            assertFalse(presented.content.message.resolve(resources).contains("ble."))
        }
        val failure = BleTransportException(BleError.StaleGeneration(Long.MAX_VALUE, Long.MIN_VALUE))
        val presented = mapper.present(failure)
        assertEquals(resources.getString(L.commonErrorFailedToLoad), presented.content.message.resolve(resources))
        assertEquals(BleFault.StaleGeneration(Long.MAX_VALUE, Long.MIN_VALUE),
            assertIs<BleTransportFault>(presented.content.sourceFault).error)
        assertEquals(UiRecovery.CONNECT, presented.content.recovery)
        assertNull(presented.recoveryAction(UiErrorActions(retry = { fail("Reconnect cannot be a generic retry") })))
    }

    @Test fun allFrozenServicePayloadsHaveExhaustivePolicyWithoutCreditingUnavailableProducers() {
        val session = MeshCoreException.Timeout()
        val timeout = UiText.Resource(L.errorMeshCoreTimeout)
        val reason = "source reason"
        val cases: List<Pair<SourceServiceFault, UiText>> = listOf(
            ContactServiceFault.NotConnected to ErrorCopy.static("ContactServiceError", "notConnected"),
            ContactServiceFault.SendFailed to ErrorCopy.static("ContactServiceError", "sendFailed"),
            ContactServiceFault.InvalidResponse to ErrorCopy.static("ContactServiceError", "invalidResponse"),
            ContactServiceFault.SyncInterrupted to ErrorCopy.static("ContactServiceError", "syncInterrupted"),
            ContactServiceFault.ContactNotFound to ErrorCopy.static("ContactServiceError", "contactNotFound"),
            ContactServiceFault.ContactTableFull to ErrorCopy.static("ContactServiceError", "contactTableFull"),
            ContactServiceFault.ShareContactUnavailable to ErrorCopy.static("ContactServiceError", "shareContactUnavailable"),
            ContactServiceFault.SessionError(session) to timeout,
            ChannelServiceFault.NotConnected to ErrorCopy.static("ChannelServiceError", "notConnected"),
            ChannelServiceFault.ChannelNotFound to ErrorCopy.static("ChannelServiceError", "channelNotFound"),
            ChannelServiceFault.InvalidChannelIndex to ErrorCopy.static("ChannelServiceError", "invalidChannelIndex"),
            ChannelServiceFault.SecretHashingFailed to ErrorCopy.static("ChannelServiceError", "secretHashingFailed"),
            ChannelServiceFault.SaveFailed(reason) to ErrorCopy.channelSaveFailed(reason),
            ChannelServiceFault.SendFailed(reason) to ErrorCopy.channelSendFailed(reason),
            ChannelServiceFault.SessionError(session) to timeout,
            ChannelServiceFault.SyncAlreadyInProgress to ErrorCopy.static("ChannelServiceError", "syncAlreadyInProgress"),
            ChannelServiceFault.CircuitBreakerOpen(Int.MAX_VALUE) to ErrorCopy.circuitBreakerOpen(Int.MAX_VALUE.toLong()),
            AdvertisementFault.NotConnected to ErrorCopy.static("AdvertisementError", "notConnected"),
            AdvertisementFault.SendFailed to ErrorCopy.static("AdvertisementError", "sendFailed"),
            AdvertisementFault.InvalidResponse to ErrorCopy.static("AdvertisementError", "invalidResponse"),
            AdvertisementFault.SessionError(session) to timeout,
            RemoteNodeFault.NotConnected to ErrorCopy.static("RemoteNodeError", "notConnected"),
            RemoteNodeFault.LoginFailed(reason) to ErrorCopy.static("RemoteNodeError", "loginFailed"),
            RemoteNodeFault.SendFailed(reason) to ErrorCopy.static("RemoteNodeError", "sendFailed"),
            RemoteNodeFault.InvalidResponse to ErrorCopy.static("RemoteNodeError", "invalidResponse"),
            RemoteNodeFault.PermissionDenied to ErrorCopy.static("RemoteNodeError", "permissionDenied"),
            RemoteNodeFault.Timeout to ErrorCopy.static("RemoteNodeError", "timeout"),
            RemoteNodeFault.SessionNotFound to ErrorCopy.static("RemoteNodeError", "sessionNotFound"),
            RemoteNodeFault.PasswordNotFound to ErrorCopy.static("RemoteNodeError", "passwordNotFound"),
            RemoteNodeFault.FloodRouted to ErrorCopy.static("RemoteNodeError", "floodRouted"),
            RemoteNodeFault.PathDiscoveryFailed to ErrorCopy.static("RemoteNodeError", "pathDiscoveryFailed"),
            RemoteNodeFault.ContactNotFound to ErrorCopy.static("RemoteNodeError", "contactNotFound"),
            RemoteNodeFault.RadioContactsFull to ErrorCopy.static("RemoteNodeError", "radioContactsFull"),
            RemoteNodeFault.Cancelled to ErrorCopy.static("RemoteNodeError", "cancelled"),
            RemoteNodeFault.SessionError(session) to timeout,
            RoomServerFault.NotConnected to ErrorCopy.static("RoomServerError", "notConnected"),
            RoomServerFault.SessionNotFound to ErrorCopy.static("RoomServerError", "sessionNotFound"),
            RoomServerFault.SendFailed(reason) to ErrorCopy.static("RoomServerError", "sendFailed"),
            RoomServerFault.PermissionDenied to ErrorCopy.static("RoomServerError", "permissionDenied"),
            RoomServerFault.InvalidResponse to ErrorCopy.static("RoomServerError", "invalidResponse"),
            RoomServerFault.SessionError(session) to timeout,
            BinaryProtocolFault.NotConnected to ErrorCopy.static("BinaryProtocolError", "notConnected"),
            BinaryProtocolFault.SendFailed to ErrorCopy.static("BinaryProtocolError", "sendFailed"),
            BinaryProtocolFault.Timeout to ErrorCopy.static("BinaryProtocolError", "timeout"),
            BinaryProtocolFault.InvalidResponse to ErrorCopy.static("BinaryProtocolError", "invalidResponse"),
            BinaryProtocolFault.SessionError(session) to timeout,
            NodeConfigServiceFault.InvalidChannelSecret(2, 30) to ErrorCopy.invalidChannelSecret(2, 30),
            NodeConfigServiceFault.InvalidContactPublicKey("Node") to ErrorCopy.invalidContactPublicKey("Node"),
            NodeConfigServiceFault.InvalidPathHashMode("Node", 255u) to ErrorCopy.invalidPathHashMode("Node", 255u),
            NodeConfigServiceFault.InvalidPrivateKey(30) to ErrorCopy.invalidPrivateKey(30),
            NodeConfigServiceFault.InvalidRadioSettings(NodeConfigRadioField.FREQUENCY) to
                ErrorCopy.radioOutOfRange(UiText.Resource(S.configImportFieldFrequency)),
            NodeConfigServiceFault.NoAvailableChannelSlot("Channel") to ErrorCopy.noAvailableChannelSlot("Channel"),
            NodeConfigServiceFault.InvalidCoordinate(NodeConfigCoordinateField.PositionLatitude) to
                ErrorCopy.positionInvalid(UiText.Resource(S.configImportFieldLatitude)),
            NodeConfigServiceFault.InvalidOutPath("Node") to ErrorCopy.invalidOutPath("Node"),
            NodeConfigServiceFault.ContactCapacityExceeded(Int.MAX_VALUE, 0) to
                ErrorCopy.contactCapacityExceeded(Int.MAX_VALUE.toLong(), 0),
        )
        assertEquals(55, cases.size)
        for ((fault, expected) in cases) {
            val failure = NeutralPolicyFixture(fault)
            val presented = mapper.present(failure)
            assertSame(failure, presented.originalFailure)
            assertSame(fault, presented.content.sourceFault)
            assertEquals(expected.resolve(resources), presented.content.message.resolve(resources))
            assertFalse(presented.content.message.resolve(resources).contains("private fixture diagnostic"))
            if (fault is RemoteNodeFault) assertEquals(if (fault.isRetryable) UiRecovery.RETRY else UiRecovery.INSPECT_FAILURE,
                presented.content.recovery)
        }
    }

    @Test fun typedNodeFieldLabelsAndSuppressedRawReasonsSurviveNeutralPresentation() {
        val radioFields = mapOf(
            NodeConfigRadioField.FREQUENCY to S.configImportFieldFrequency,
            NodeConfigRadioField.BANDWIDTH to S.configImportFieldBandwidth,
            NodeConfigRadioField.SPREADING_FACTOR to S.configImportFieldSpreadingFactor,
            NodeConfigRadioField.CODING_RATE to S.configImportFieldCodingRate,
            NodeConfigRadioField.TX_POWER to S.configImportFieldTxPower,
        )
        assertEquals(NodeConfigRadioField.entries.toSet(), radioFields.keys)
        for ((field, label) in radioFields) assertEquals(
            ErrorCopy.radioOutOfRange(UiText.Resource(label)).resolve(resources),
            mapper.message(NeutralPolicyFixture(NodeConfigServiceFault.InvalidRadioSettings(field))).resolve(resources))
        val fields = listOf(
            NodeConfigCoordinateField.PositionLatitude to ErrorCopy.positionInvalid(UiText.Resource(S.configImportFieldLatitude)),
            NodeConfigCoordinateField.PositionLongitude to ErrorCopy.positionInvalid(UiText.Resource(S.configImportFieldLongitude)),
            NodeConfigCoordinateField.ContactLatitude("Node") to
                ErrorCopy.contactCoordinateInvalid("Node", UiText.Resource(S.configImportFieldLatitude)),
            NodeConfigCoordinateField.ContactLongitude("Node") to
                ErrorCopy.contactCoordinateInvalid("Node", UiText.Resource(S.configImportFieldLongitude)),
        )
        for ((field, expected) in fields) assertEquals(expected.resolve(resources),
            mapper.message(NeutralPolicyFixture(NodeConfigServiceFault.InvalidCoordinate(field))).resolve(resources))
        for (fault in listOf(RemoteNodeFault.LoginFailed("private reason"), RemoteNodeFault.SendFailed("private reason"),
            RoomServerFault.SendFailed("private reason"))) {
            val presented = mapper.present(NeutralPolicyFixture(fault))
            assertSame(fault, presented.content.sourceFault)
            assertFalse(presented.content.message.resolve(resources).contains("private reason"))
        }
    }

    @Test fun sessionPayloadsStillUseCentralRecursionAndPropagateActualCancellation() {
        val underlying = MeshCoreException.ConnectionLost(MeshCoreException.BluetoothPoweredOff())
        val sessionFaults = listOf(
            ContactServiceFault.SessionError(underlying), ChannelServiceFault.SessionError(underlying),
            AdvertisementFault.SessionError(underlying), RemoteNodeFault.SessionError(underlying),
            RoomServerFault.SessionError(underlying), BinaryProtocolFault.SessionError(underlying),
        )
        for (fault in sessionFaults) assertEquals(mapper.message(underlying).resolve(resources),
            mapper.message(NeutralPolicyFixture(fault)).resolve(resources))
        val cancelled = MeshCoreException.ConnectionLost(CancellationException("synthetic cancellation"))
        for (fault in listOf(
            ContactServiceFault.SessionError(cancelled), ChannelServiceFault.SessionError(cancelled),
            AdvertisementFault.SessionError(cancelled), RemoteNodeFault.SessionError(cancelled),
            RoomServerFault.SessionError(cancelled), BinaryProtocolFault.SessionError(cancelled),
        )) assertFailsWith<CancellationException> { mapper.present(NeutralPolicyFixture(fault)) }
    }
}
