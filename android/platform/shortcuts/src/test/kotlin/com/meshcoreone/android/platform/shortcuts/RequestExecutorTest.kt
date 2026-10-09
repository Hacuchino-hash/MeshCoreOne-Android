// AndroidOnly: WP-404 Executor behaviour: confirmation gating, foreground hand-off and localized errors.
package com.meshcoreone.android.platform.shortcuts

import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestExecutorTest {
    private val alice = contact()
    private val id = TargetIdentity.contactId(alice)

    private fun executor(radio: FakeRadio, confirmation: FakeConfirmation, presenter: FakePresenter) =
        ShortcutRequestExecutor(radio, confirmation, FakeText(), presenter)

    @Test
    fun sendRequiresConfirmationAndQueues() = runSuspend {
        val radio = FakeRadio(contacts = listOf(alice))
        val confirmation = FakeConfirmation()
        executor(radio, confirmation, FakePresenter()).run(ShortcutRequest.Send(id, "hi"))
        assertEquals(1, confirmation.requests.size)
        assertEquals(1, radio.queued.size)
        val denied = FakeConfirmation(answer = false)
        val radio2 = FakeRadio(contacts = listOf(alice))
        executor(radio2, denied, FakePresenter()).run(ShortcutRequest.Send(id, "hi"))
        assertTrue(radio2.queued.isEmpty())
    }

    @Test
    fun incompleteShareOpensAppWithoutSending() = runSuspend {
        val radio = FakeRadio(contacts = listOf(alice))
        val presenter = FakePresenter()
        executor(radio, FakeConfirmation(), presenter).run(ShortcutRequest.Send(null, "hi"))
        assertEquals(listOf<ShortcutRequest>(ShortcutRequest.Send(null, "hi")), presenter.opened)
        assertTrue(radio.queued.isEmpty())
    }

    @Test
    fun foregroundEscalationAndErrorsAreSurfaced() = runSuspend {
        val presenter = FakePresenter()
        val radio = FakeRadio(connectionState = DeviceConnectionState.CONNECTING, contacts = listOf(alice))
        executor(radio, FakeConfirmation(), presenter).run(ShortcutRequest.Send(id, "hi"))
        assertEquals(listOf("foreground"), presenter.messages)
        assertEquals(1, presenter.opened.size)
        val invalid = FakePresenter()
        executor(FakeRadio(), FakeConfirmation(), invalid).run(ShortcutRequest.Send("bad", "hi"))
        assertEquals(listOf("error:INVALID_RECIPIENT"), invalid.messages)
    }

    @Test
    fun statusAndAdvertReportThroughPresenter() = runSuspend {
        val presenter = FakePresenter()
        val radio = FakeRadio()
        val executor = executor(radio, FakeConfirmation(), presenter)
        executor.run(ShortcutRequest.Status)
        executor.run(ShortcutRequest.Advert(AdvertReach.ZERO_HOP))
        radio.connectionState = DeviceConnectionState.DISCONNECTED
        executor.run(ShortcutRequest.Advert(AdvertReach.ZERO_HOP))
        assertEquals(listOf("Node:80", "sent:zeroHop", "error:NOT_CONNECTED"), presenter.messages)
    }
}
