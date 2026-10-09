// AndroidOnly: WP-404 Shared fakes and a stdlib-only suspend runner (no new test dependencies).
package com.meshcoreone.android.platform.shortcuts

import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.protocol.bytes.Bytes
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SourceCases(vararg val value: String)

/** Fakes never truly suspend, so the coroutine completes synchronously. */
fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    return checkNotNull(result) { "coroutine suspended" }.getOrThrow()
}

val RADIO_A = RadioId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
val RADIO_B = RadioId(UUID.fromString("22222222-2222-2222-2222-222222222222"))

fun contact(radio: RadioId = RADIO_A, key: Int = 1, name: String = "Alice", type: UByte = 1u, row: UUID = UUID.randomUUID()) =
    ContactDTO(id = row, radioId = radio, publicKey = Bytes(ByteArray(32) { key.toByte() }), name = name, typeRawValue = type, lastHeardTimestamp = null)

fun channel(radio: RadioId = RADIO_A, index: Int = 0, name: String = "Ops", secret: Int = 7, row: UUID = UUID.randomUUID()) =
    ChannelDTO(id = row, radioId = radio, index = index.toUByte(), name = name, secret = Bytes(ByteArray(16) { secret.toByte() }))

class FakeText : ShortcutText {
    override fun sendConfirm(recipientName: String) = "confirm:$recipientName"
    override fun advertConfirm(reach: AdvertReach) = "advert?:${reach.rawValue}"
    override fun advertSent(reach: AdvertReach) = "sent:${reach.rawValue}"
    override fun error(error: ShortcutError) = "error:${error.name}"
    override val sendForeground = "foreground"
    override val statusNotReady = "notready"
    override val statusRadioFallbackName = "Your radio"
    override val statusDisconnectedUnknown = "none"
    override fun statusConnectedNoBattery(name: String) = "$name:nobattery"
    override fun statusConnectedWithBattery(name: String, percent: Int) = "$name:$percent"
    override fun statusConnecting(name: String) = "$name:connecting"
    override fun statusDisconnectedNamed(name: String) = "$name:offline"
    override val channelSubtitle = "Channel"
    override val sendShortTitle = "Send"
    override val statusShortTitle = "Status"
    override val advertShortTitle = "Advert"
    override fun advertReachLabel(reach: AdvertReach) = reach.rawValue
}

class FakeRadio(
    override var connectionState: DeviceConnectionState = DeviceConnectionState.READY,
    override var currentRadioId: RadioId? = RADIO_A,
    override var hasRestorableRadio: Boolean = false,
    override var connectedNodeNameByteCount: Int = 5,
    var contacts: List<ContactDTO> = emptyList(),
    var channels: List<ChannelDTO> = emptyList(),
    var snapshot: RadioStatusSnapshot = RadioStatusSnapshot(DeviceConnectionState.READY, "Node", null, 80),
    var queueFailure: Exception? = null,
    var advertFailure: Exception? = null,
) : ShortcutRadioPort {
    val queued = mutableListOf<Pair<ShortcutRecipient, String>>()
    val adverts = mutableListOf<Boolean>()
    override fun status() = snapshot
    override suspend fun contacts(radioId: RadioId) = contacts.filter { it.radioId == radioId }
    override suspend fun channels(radioId: RadioId) = channels.filter { it.radioId == radioId }
    override suspend fun queueMessage(recipient: ShortcutRecipient, text: String) {
        queueFailure?.let { throw it }
        queued += recipient to text
    }
    override suspend fun sendAdvert(flood: Boolean) {
        advertFailure?.let { throw it }
        adverts += flood
    }
}

class FakeConfirmation(var answer: Boolean = true, private val during: () -> Unit = {}) : ShortcutConfirmationPort {
    val requests = mutableListOf<ConfirmationRequest>()
    override suspend fun confirm(request: ConfirmationRequest): Boolean {
        requests += request
        during()
        return answer
    }
}

class FakePresenter : ShortcutPresenter {
    val messages = mutableListOf<String>()
    val opened = mutableListOf<ShortcutRequest>()
    override fun show(message: String) { messages += message }
    override fun openApp(request: ShortcutRequest) { opened += request }
}
