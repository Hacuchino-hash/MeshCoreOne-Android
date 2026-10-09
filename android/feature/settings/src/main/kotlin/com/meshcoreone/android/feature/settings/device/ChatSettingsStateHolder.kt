// PortedFrom: MC1/Views/Settings/ChatSettingsView.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Settings/Sections/MessagesSettingsSection.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.settings.device

import kotlinx.coroutines.flow.StateFlow

/** The chat page's switches: reply-with-quote and which routing details show on incoming messages. */
class ChatSettingsStateHolder(private val port: ChatPreferencePort) {
    val values: StateFlow<Map<ChatPreference, Boolean>> get() = port.values

    /** Defaults when nothing is stored: all off (heard-count defaults to the shared `AppStorageKey` value, supplied by the port). */
    fun isOn(preference: ChatPreference): Boolean = port.values.value[preference] ?: false

    fun set(preference: ChatPreference, value: Boolean) = port.set(preference, value)
}
