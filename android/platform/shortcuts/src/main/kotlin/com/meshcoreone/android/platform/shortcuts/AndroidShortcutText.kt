// AndroidOnly: WP-404 Resolves ShortcutText from the shared core:l10n resources.
package com.meshcoreone.android.platform.shortcuts

import android.content.Context
import com.meshcoreone.android.core.l10n.R

class AndroidShortcutText(private val context: Context) : ShortcutText {
    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    override fun sendConfirm(recipientName: String) = s(R.string.l10n_app_tools_intent_send_confirm, recipientName)
    override fun advertConfirm(reach: AdvertReach) = s(R.string.l10n_app_tools_intent_advert_title) + " (" + advertReachLabel(reach) + ")?"
    override fun advertSent(reach: AdvertReach) = when (reach) {
        AdvertReach.ZERO_HOP -> s(R.string.l10n_app_tools_intent_advert_dialog_sentzerohop)
        AdvertReach.FLOOD -> s(R.string.l10n_app_tools_intent_advert_dialog_sentflood)
    }
    override fun error(error: ShortcutError) = when (error) {
        ShortcutError.NOT_CONNECTED -> s(R.string.l10n_app_localizable_error_intent_notconnected)
        ShortcutError.INVALID_RECIPIENT -> s(R.string.l10n_app_localizable_error_intent_invalidrecipient)
        ShortcutError.MESSAGE_TOO_LONG -> s(R.string.l10n_app_localizable_error_intent_messagetoolong)
        ShortcutError.SEND_FAILED, ShortcutError.ADVERT_FAILED, ShortcutError.DENIED ->
            s(R.string.l10n_app_localizable_error_intent_sendfailed)
    }
    override val sendForeground get() = s(R.string.l10n_app_tools_intent_send_foreground)
    override val statusNotReady get() = s(R.string.l10n_app_tools_intent_status_dialog_notready)
    override val statusRadioFallbackName get() = s(R.string.l10n_app_tools_intent_status_radiofallbackname)
    override val statusDisconnectedUnknown get() = s(R.string.l10n_app_tools_intent_status_dialog_disconnectedunknown)
    override fun statusConnectedNoBattery(name: String) = s(R.string.l10n_app_tools_intent_status_dialog_connectednobattery, name)
    override fun statusConnectedWithBattery(name: String, percent: Int) =
        s(R.string.l10n_app_tools_intent_status_dialog_connectedwithbattery, name, percent)
    override fun statusConnecting(name: String) = s(R.string.l10n_app_tools_intent_status_dialog_connecting, name)
    override fun statusDisconnectedNamed(name: String) = s(R.string.l10n_app_tools_intent_status_dialog_disconnectednamed, name)
    override val channelSubtitle get() = s(R.string.l10n_app_tools_intent_entity_channel)
    override val sendShortTitle get() = s(R.string.l10n_app_tools_intent_send_shorttitle)
    override val statusShortTitle get() = s(R.string.l10n_app_tools_intent_status_shorttitle)
    override val advertShortTitle get() = s(R.string.l10n_app_tools_intent_advert_shorttitle)
    override fun advertReachLabel(reach: AdvertReach) = when (reach) {
        AdvertReach.ZERO_HOP -> s(R.string.l10n_app_tools_intent_advert_reach_zerohop)
        AdvertReach.FLOOD -> s(R.string.l10n_app_tools_intent_advert_reach_flood)
    }
}
