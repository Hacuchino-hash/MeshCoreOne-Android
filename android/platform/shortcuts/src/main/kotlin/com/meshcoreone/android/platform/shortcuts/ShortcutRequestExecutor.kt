// AndroidOnly: WP-404 Runs a typed request through the confirmation-gated flows and reports via a presenter.
package com.meshcoreone.android.platform.shortcuts

/** App-provided presentation seam: brief messages and foreground hand-off. */
interface ShortcutPresenter {
    fun show(message: String)
    /** Bring the app forward so the user can finish the request (e.g. choose a recipient or text). */
    fun openApp(request: ShortcutRequest)
}

class ShortcutRequestExecutor(
    private val radio: ShortcutRadioPort,
    confirmation: ShortcutConfirmationPort,
    private val text: ShortcutText,
    private val presenter: ShortcutPresenter,
) {
    private val sendFlow = SendMessageFlow(radio, confirmation, text)
    private val advertFlow = AdvertFlow(radio, confirmation, text)

    suspend fun run(request: ShortcutRequest) {
        try {
            when (request) {
                ShortcutRequest.Status -> presenter.show(StatusFlow.dialog(radio.status(), text))
                is ShortcutRequest.Advert -> runAdvert(request)
                is ShortcutRequest.Send -> runSend(request)
            }
        } catch (error: ShortcutException) {
            presenter.show(text.error(error.error))
        }
    }

    private suspend fun runAdvert(request: ShortcutRequest.Advert) {
        when (val result = advertFlow.execute(request.reach)) {
            is AdvertResult.Sent -> presenter.show(result.message)
            AdvertResult.Declined -> Unit
        }
    }

    private suspend fun runSend(request: ShortcutRequest.Send) {
        val targetId = request.targetId
        val message = request.message
        if (targetId == null || message == null) {
            presenter.openApp(request)
            return
        }
        when (sendFlow.execute(targetId, message)) {
            SendResult.Queued, SendResult.Declined -> Unit
            SendResult.NeedsForeground -> {
                presenter.show(text.sendForeground)
                presenter.openApp(request)
            }
        }
    }
}
