// AndroidOnly: WP-316 Feature-owned service ports for the CLI terminals; WP-303 adapts the core:services implementations.
package com.meshcoreone.android.feature.tools.diagnostics.cli

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.DeviceDTO
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.BatteryInfo
import com.meshcoreone.android.core.protocol.model.DeviceCapabilities
import com.meshcoreone.android.core.protocol.model.SelfInfo
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Per-connection dependencies for the Tools CLI, each read live at its call site so a null read
 * means disconnected (Swift `CLIToolViewModel.Dependencies`). Feature modules may not depend on
 * core:services, so the services are reached through the narrow feature-owned ports below; the
 * WP-303 app wiring adapts `RepeaterAdminService`, `RemoteNodeService`, `SettingsService` and the
 * persistence store to them.
 */
data class CliToolFeatureDependencies(
    /** Identity is the connection marker: a different instance means a new or lost connection. */
    val repeaterAdminService: () -> CliRepeaterAdminPort?,
    val remoteNodeService: () -> CliRemoteNodePort?,
    val settingsService: () -> CliSettingsPort?,
    val dataStore: () -> CliNodeDirectory?,
    val radioId: () -> RadioId?,
    val connectedDevice: () -> DeviceDTO?,
    /** `AppState.sendSelfAdvert(flood:allowLocationPrompt: false)`. */
    val sendSelfAdvert: suspend (flood: Boolean) -> Unit,
)

/** The `RepeaterAdminService.sendRawCommand` slice used by remote CLI sessions. */
fun interface CliRepeaterAdminPort {
    suspend fun sendRawCommand(session: EntityKey, command: String, timeout: Duration): String
}

/** The `RemoteNodeService` slice used by `login`/`logout` (WP-210 signatures, narrowed). */
interface CliRemoteNodePort {
    /** Creates or reuses the remote session for [contact]. */
    suspend fun createSession(radioId: RadioId, contact: ContactDTO): EntityKey

    /** `login(...).success`; [onTimeoutKnown] receives whole seconds once the firmware accepts the send. */
    suspend fun login(session: EntityKey, password: String, pathLength: UByte, onTimeoutKnown: suspend (Long) -> Unit): Boolean

    suspend fun logout(session: EntityKey)

    /** Returns null when no password is stored (or the keychain read fails), as in Swift. */
    suspend fun retrievePassword(contact: ContactDTO): String?

    suspend fun storePassword(password: String, publicKey: Bytes)

    suspend fun deletePassword(contact: ContactDTO)
}

/**
 * The `SettingsService` slice behind the local radio commands. Signatures match core:services
 * `SettingsService` so the adapter is pure delegation; failures surface as the service's
 * `SettingsServiceException` (core:contracts).
 */
interface CliSettingsPort {
    suspend fun getTime(): Instant
    suspend fun setTime(date: Instant)
    suspend fun queryDevice(): DeviceCapabilities
    suspend fun getSelfInfo(): SelfInfo
    suspend fun getBattery(): BatteryInfo
    suspend fun setNodeNameVerified(name: String): SelfInfo
    suspend fun setManualLocationVerified(latitude: Double, longitude: Double): SelfInfo
    suspend fun setTxPowerVerified(power: Byte): SelfInfo
    suspend fun setRadioParamsVerified(frequencyKHz: UInt, bandwidthKHz: UInt, spreadingFactor: UByte, codingRate: UByte): SelfInfo
    suspend fun setOtherParamsVerified(device: DeviceDTO, multiAcks: UByte): SelfInfo
    suspend fun setPathHashModeVerified(mode: UByte): UByte
    suspend fun getCustomVars(): Map<String, String>
    suspend fun setCustomVar(key: String, value: String)
    suspend fun reboot()
}

/** The two persistence reads the CLI needs (`ContactPersisting`/`ChannelPersisting` narrowed). */
interface CliNodeDirectory {
    suspend fun fetchContacts(radioId: RadioId): List<ContactDTO>
    suspend fun fetchChannels(radioId: RadioId): List<ChannelDTO>
}

/**
 * Feature-local mirror of the `RemoteNodeError` cases the terminals branch on. `RemoteNodeError`
 * lives in core:services (WP-210); after it merges, WP-303 classifies through its
 * `RemoteNodeFault` projection.
 */
sealed interface CliRemoteFault {
    data object Timeout : CliRemoteFault
    data object PasswordNotFound : CliRemoteFault
    data class LoginFailed(val reason: String) : CliRemoteFault
    data object Cancelled : CliRemoteFault
}

/** How the terminals classify and describe service failures (Swift `as RemoteNodeError` / `localizedDescription`). */
data class CliErrorPresentation(
    val remoteFault: (Throwable) -> CliRemoteFault? = { null },
    val describe: (Throwable) -> String = { it.message ?: it.toString() },
    /** Swift `logger.error` sites (contact/channel fetch failures, swallowed keychain writes). */
    val log: (String, Throwable) -> Unit = { _, _ -> },
)

/** Mirror of the `RemoteOperationTimeoutPolicy` constants the terminals pass (core:services, WP-210). */
object CliTimeouts {
    /** Default wait for a CLI reply. */
    val DEFAULT_CLI: Duration = 10.seconds

    /** Wait for commands that get no reply by design (`reboot`). */
    val FIRE_AND_FORGET_CLI: Duration = 2.seconds
}
