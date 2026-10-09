// AndroidOnly: WP-313 Native checks of repeater/room settings wiring, behavior and room-access sections.
package com.meshcoreone.android.feature.remotenodes.settings

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.l10n.generated.AppRemoteNodesStrings
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.RemoteNodeRole
import com.meshcoreone.android.core.protocol.event.ContactMessage
import com.meshcoreone.android.core.protocol.event.NeighboursResponse
import com.meshcoreone.android.core.protocol.event.OwnerInfoResponse
import com.meshcoreone.android.core.protocol.event.StatusResponse
import com.meshcoreone.android.core.protocol.event.TelemetryResponse
import com.meshcoreone.android.feature.remotenodes.common.RemoteNodesText
import com.meshcoreone.android.feature.remotenodes.dependencies.RepeaterAdminPort
import com.meshcoreone.android.feature.remotenodes.dependencies.RoomAdminPort
import com.meshcoreone.android.feature.remotenodes.support.TEST_RADIO
import com.meshcoreone.android.feature.remotenodes.support.VirtualClock
import com.meshcoreone.android.feature.remotenodes.support.bytes
import com.meshcoreone.android.feature.remotenodes.support.key
import com.meshcoreone.android.feature.remotenodes.support.runSuspend
import com.meshcoreone.android.feature.remotenodes.support.session
import java.time.Instant
import java.util.Locale
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.yield
import org.junit.Test

class AdminSettingsTest {
    /** Admin service double: routes CLI to a recorder and keeps one CLI handler slot like WP-210's services. */
    private class FakeAdmin(val recorder: CommandRecorder) : RepeaterAdminPort, RoomAdminPort {
        var cliHandler: (suspend (ContactMessage, ContactDTO) -> Unit)? = null
        val ownerInfoRequests = mutableListOf<EntityKey>()
        override suspend fun sendCommand(session: EntityKey, command: String, timeout: Duration) = recorder.send(session, command, timeout)
        override suspend fun sendRawCommand(session: EntityKey, command: String, timeout: Duration) =
            recorder.send(session, "raw:$command", timeout)
        override fun setCLIHandler(handler: suspend (ContactMessage, ContactDTO) -> Unit) { cliHandler = handler }
        override suspend fun requestStatus(session: EntityKey, timeout: Duration?): StatusResponse = error("unused")
        override suspend fun requestTelemetry(session: EntityKey, timeout: Duration?): TelemetryResponse = error("unused")
        override fun setStatusHandler(handler: suspend (StatusResponse) -> Unit) = Unit
        override fun setTelemetryHandler(handler: suspend (TelemetryResponse) -> Unit) = Unit
        override fun clearHandlers() { cliHandler = null }
        override fun clearStatusHandlers() = Unit
        override suspend fun requestOwnerInfo(session: EntityKey, timeout: Duration?): OwnerInfoResponse {
            ownerInfoRequests += session
            return OwnerInfoResponse("v1.16.0", "Binary Name", "Owner|Line")
        }
        override suspend fun fetchAllNeighbors(session: EntityKey, timeout: Duration?): NeighboursResponse = error("unused")
        override fun setNeighboursHandler(handler: suspend (NeighboursResponse) -> Unit) = Unit

        suspend fun deliverLate(text: String) {
            val contact = ContactDTO(radioId = TEST_RADIO, publicKey = bytes(32, 1), name = "n", lastHeardTimestamp = null)
            cliHandler?.invoke(ContactMessage(bytes(6, 1), 0u, 0u, Instant.EPOCH, null, text, null), contact)
        }
    }

    @Test
    fun `repeater configure wires the admin service and pre-fetches owner info`() = runSuspend {
        val admin = FakeAdmin(CommandRecorder())
        val holder = RepeaterSettingsStateHolder(VirtualClock(), TestFaults, this)
        val node = session(name = "Ridge")
        holder.configure({ admin }, node)
        yield()
        assertEquals(listOf(node.key), admin.ownerInfoRequests)
        with(holder.helper.state.value) {
            assertEquals("v1.16.0", firmwareVersion)
            assertEquals("Binary Name", name)
            assertEquals("Owner|Line", ownerInfo)
        }
        // Owner info is already known, so the contact-info fetch sends no CLI query.
        holder.helper.fetchContactInfo()
        assertTrue(admin.recorder.commands.isEmpty())
        assertNotNull(holder.makeNodeCLISend(node)).invoke("ver", Duration.ZERO)
        assertEquals(listOf("raw:ver"), admin.recorder.commands)
        holder.cleanup()
        assertNotNull(admin.cliHandler, "cleanup replaces the CLI slot with a no-op rather than clearing others")
    }

    @Test
    fun `null admin service mirrors a disconnected radio`() = runSuspend {
        val holder = RepeaterSettingsStateHolder(VirtualClock(), TestFaults, this)
        holder.configure({ null }, session())
        assertNull(holder.makeNodeCLISend(session()))
        holder.helper.applyRadioSettings()
        assertEquals(RemoteNodesText.resource(AppRemoteNodesStrings.remoteNodesSettingsRadioNotLoaded), holder.helper.state.value.errorMessage)
        holder.helper.setRadio(915.0, 250.0, 10, 5)
        holder.helper.applyRadioSettings()
        assertEquals(RemoteNodesText.resource(AppRemoteNodesStrings.remoteNodesSettingsNoService), holder.helper.state.value.errorMessage)
    }

    @Test
    fun `repeater behavior fetch apply and late recovery`() = runSuspend {
        val recorder = CommandRecorder().apply {
            repliesByCommand = mutableMapOf("get repeat" to "on", "get advert.interval" to "120", "get flood.max" to "> 8")
            errorsByCommand = mutableMapOf("get flood.advert.interval" to FakeTimeout())
        }
        val admin = FakeAdmin(recorder)
        val holder = RepeaterSettingsStateHolder(VirtualClock(), TestFaults, CoroutineScope(coroutineContext))
        holder.configure({ admin }, session())
        holder.fetchBehaviorSettings()
        with(holder.state.value) {
            assertEquals(true, repeaterEnabled)
            assertEquals(120L, advertIntervalMinutes)
            assertNull(floodAdvertIntervalHours)
            assertEquals(8L, floodMaxHops)
            assertTrue(behaviorError)
        }
        admin.deliverLate("36")
        assertEquals(36L, holder.state.value.floodAdvertIntervalHours)
        assertFalse(holder.state.value.behaviorError)

        holder.setAdvertIntervalMinutes(30)
        holder.applyBehaviorSettings()
        assertNotNull(holder.state.value.advertIntervalError)
        recorder.resetCommands()
        holder.setAdvertIntervalMinutes(0)
        holder.setRepeaterEnabled(false)
        holder.applyBehaviorSettings()
        assertEquals(listOf("set repeat off", "set advert.interval 0"), recorder.commands)
        assertFalse(holder.state.value.behaviorSettingsModified)
        assertNull(holder.state.value.advertIntervalError)

        recorder.reply = "Err"
        holder.setFloodMaxHops(9)
        holder.applyBehaviorSettings()
        assertEquals(RemoteNodesText.resource(AppRemoteNodesStrings.remoteNodesSettingsSomeSettingsFailedToApply), holder.helper.state.value.errorMessage)
        assertFalse(holder.helper.state.value.isApplying)
    }

    @Test
    fun `room access reads guest password and read-only mode`() = runSuspend {
        val recorder = CommandRecorder().apply {
            repliesByCommand = mutableMapOf("raw:get guest.password" to "  > hello world \n", "raw:get allow.read.only" to "> ON")
        }
        val admin = FakeAdmin(recorder)
        val holder = RoomSettingsStateHolder(VirtualClock(), TestFaults, this)
        holder.configure({ admin }, session(role = RemoteNodeRole.ROOM_SERVER, name = "Room"))
        yield()
        assertEquals(listOf("ver", "clock"), recorder.commands, "room configure fetches device info over CLI")
        holder.fetchRoomAccess()
        assertEquals("hello world", holder.state.value.guestPassword)
        assertEquals(true, holder.state.value.allowReadOnly)

        recorder.repliesByCommand["raw:get guest.password"] = "Error: unknown command"
        holder.fetchRoomAccess()
        assertEquals("", holder.state.value.guestPassword)

        recorder.resetCommands()
        holder.setGuestPassword("pw")
        holder.setAllowReadOnly(false)
        holder.applyRoomAccess()
        assertEquals(listOf("set guest.password pw", "set allow.read.only off"), recorder.commands)
        assertFalse(holder.state.value.roomAccessModified)
        assertFalse(holder.state.value.isApplyingRoomAccess)
        assertFalse(holder.state.value.roomAccessApplySuccess)
    }

    @Test
    fun `room behavior timeout flags the section and late replies complete it`() = runSuspend {
        val recorder = CommandRecorder().apply { errorsByCommand = mutableMapOf("get flood.max" to FakeTimeout()) }
        recorder.repliesByCommand = mutableMapOf("get advert.interval" to "60", "get flood.advert.interval" to "12")
        val admin = FakeAdmin(recorder)
        val holder = RoomSettingsStateHolder(VirtualClock(), TestFaults, CoroutineScope(coroutineContext))
        holder.configure({ admin }, session(role = RemoteNodeRole.ROOM_SERVER))
        holder.fetchBehaviorSettings()
        assertTrue(holder.state.value.behaviorError)
        admin.deliverLate("4")
        assertEquals(4L, holder.state.value.floodMaxHops)
        assertFalse(holder.state.value.behaviorError)
        holder.setFloodMaxHops(70)
        holder.applyBehaviorSettings()
        assertNotNull(holder.state.value.floodMaxHopsError)
    }

    @Test
    fun `password change validates then accepts the firmware echo`() = runSuspend {
        val recorder = CommandRecorder("password now: newpw")
        val holder = NodeSettingsStateHolder(VirtualClock(), TestFaults).apply { configure(session(), recorder::send, recorder::send) }
        holder.changePassword()
        assertEquals(RemoteNodesText.resource(AppRemoteNodesStrings.remoteNodesSettingsPasswordEmpty), holder.state.value.errorMessage)
        holder.setNewPassword("newpw")
        holder.setConfirmPassword("other")
        holder.changePassword()
        assertEquals(RemoteNodesText.resource(AppRemoteNodesStrings.remoteNodesSettingsPasswordMismatch), holder.state.value.errorMessage)
        holder.setConfirmPassword("newpw")
        holder.changePassword()
        assertEquals(listOf("password newpw"), recorder.commands)
        assertEquals("", holder.state.value.newPassword)
        assertNull(holder.state.value.errorMessage)
    }

    @Test
    fun `reboot timeout is success and advert reports sent`() = runSuspend {
        val recorder = CommandRecorder().apply { errorsByCommand = mutableMapOf("reboot" to FakeTimeout()) }
        val holder = NodeSettingsStateHolder(VirtualClock(), TestFaults).apply { configure(session(), recorder::send, recorder::send) }
        holder.reboot()
        assertEquals(RemoteNodesText.resource(AppRemoteNodesStrings.remoteNodesSettingsRebootSent), holder.state.value.successMessage)
        assertEquals(Duration.parse("2s"), recorder.timeouts.single())
        assertFalse(holder.state.value.isRebooting)
        holder.forceAdvert()
        assertEquals(RemoteNodesText.resource(AppRemoteNodesStrings.remoteNodesSettingsAdvertSent), holder.state.value.successMessage)
        assertFalse(holder.state.value.isSendingAdvert)
    }

    @Test
    fun `clock sync outcomes map to their messages`() = runSuspend {
        val recorder = CommandRecorder("ERR: clock cannot go backwards")
        val holder = NodeSettingsStateHolder(VirtualClock(), TestFaults).apply { configure(session(), recorder::send, recorder::send) }
        holder.syncTime()
        assertEquals(RemoteNodesText.resource(AppRemoteNodesStrings.remoteNodesSettingsClockAheadError), holder.state.value.errorMessage)
        recorder.reply = "ERR: busy"
        holder.syncTime()
        assertEquals(RemoteNodesText.Verbatim("busy"), holder.state.value.errorMessage)
        // The reply is trimmed before "ERR: " is stripped, so a bare "ERR: " stays "ERR:" (as in Swift).
        recorder.reply = "ERR: "
        holder.syncTime()
        assertEquals(RemoteNodesText.Verbatim("ERR:"), holder.state.value.errorMessage)
        recorder.reply = "weird"
        holder.syncTime()
        assertEquals(
            RemoteNodesText.resource(com.meshcoreone.android.core.l10n.R.string.l10n_app_remotenodes_remotenodes_settings_unexpectedresponse, "weird"),
            holder.state.value.errorMessage,
        )
    }

    @Test
    fun `Swift double interpolation and device time display`() {
        val cases = listOf(
            915.0 to "915.0", 869.525 to "869.525", 45.0 to "45.0", 0.0001 to "0.0001", 0.00001 to "1e-05",
            -0.00005 to "-5e-05", 1e15 to "1000000000000000.0", 1e16 to "1e+16", 1.5e16 to "1.5e+16", -0.0 to "-0.0",
            1.0 / 3.0 to "0.3333333333333333", 5e-324 to "5e-324", Double.MAX_VALUE to "1.7976931348623157e+308",
        )
        cases.forEach { (value, expected) -> assertEquals(expected, SwiftDoubleText.describe(value), "$value") }
        val utc = "15:35 - 14/8/2026 UTC"
        assertEquals("15:35 - 14.08.26", NodeSettingsPresentation.convertUTCToLocal(utc, Locale.GERMANY, ZoneOffset.UTC))
        assertEquals("15:35 - 14/08/26", NodeSettingsPresentation.convertUTCToLocal(utc, Locale.UK, ZoneOffset.UTC))
        assertTrue(NodeSettingsPresentation.convertUTCToLocal(utc, Locale.US, ZoneOffset.UTC).endsWith(" - 08/14/26"))
        assertEquals("not a clock", NodeSettingsPresentation.convertUTCToLocal("not a clock", Locale.US, ZoneOffset.UTC))
        assertEquals(RemoteNodesText.Verbatim("—"), NodeSettingsPresentation.loadPlaceholder(false, false))
        assertEquals(
            RemoteNodesText.resource(AppRemoteNodesStrings.remoteNodesSettingsLoading),
            NodeSettingsPresentation.loadPlaceholder(true, true),
        )
        val state = NodeSettingsState(ownerInfo = "👍🏽".repeat(120), originalOwnerInfo = "x")
        assertEquals(120, state.ownerInfoCharCount)
        assertTrue(state.isOwnerInfoTooLong)
        assertFalse(state.canApplyContactInfo)
        assertEquals("120/119" to true, NodeSettingsPresentation.ownerInfoCounter(state))
    }
}
