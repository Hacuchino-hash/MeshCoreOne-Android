// AndroidOnly: WP-317 Wires the feature's state holders from one dependency bundle; the app layer (WP-303) owns the scope and the ports.
package com.meshcoreone.android.feature.settings.device

/** One holder per settings section, sharing a single radio-write gate and environment. Create once per settings session. */
class SettingsHolders(val deps: SettingsFeatureDependencies, val env: SettingsEnvironment) {
    val radioWriteGate = RadioWriteGate()
    val presetLocationSession = PresetLocationSession(env, deps.location, deps.regions, deps.regionLookup)
    val radioPreset = RadioPresetStateHolder(env, deps.connection, deps.regions, deps.radioCatalog, deps.settingsService, deps.deviceStore, radioWriteGate)
    val pathHash = PathHashModeStateHolder(env, deps.connection, deps.settingsService)
    val advancedRadio = AdvancedRadioStateHolder(env, deps.connection, deps.settingsService, radioWriteGate)
    val advancedPage = AdvancedSettingsStateHolder(env, deps.connection, deps.settingsService)
    val floodScope = DefaultFloodScopeStateHolder(env, deps.connection, deps.settingsService, deps.regionDiscovery, deps.deviceStore)
    val contacts = ContactsSettingsStateHolder(env, deps.connection, deps.settingsService)
    val staleCleanup = StaleNodeCleanupStateHolder(env, deps.connection, deps.staleCleanup)
    val telemetry = TelemetrySettingsStateHolder(env, deps.connection, deps.settingsService)
    val directMessages = DirectMessagesStateHolder(env, deps.connection, deps.settingsService)
    val batteryCurve = BatteryCurveStateHolder(env, deps.connection, deps.deviceStore)
    val deviceActions = DeviceActionsStateHolder(env, deps.settingsService)
    val regenerateIdentity = RegenerateIdentityStateHolder(env, deps.identityKeys, deps.settingsService)
    val dangerZone = DangerZoneStateHolder(env, deps.settingsService, { deps.connection.connectedDevice.value }, deps.maintenance)
    val diagnostics = DiagnosticsStateHolder(env, deps.diagnostics)
    val notifications = NotificationSettingsStateHolder(env, deps.notificationPreferences, deps.notificationPermission, deps.discoveryChoices)
    val location = LocationSettingsStateHolder(env, deps.connection, deps.settingsService, deps.devicePreferences, deps.location)
    val bluetoothPin = BluetoothPinStateHolder(env, deps.connection, deps.settingsService)
    val deviceInfo = DeviceInfoStateHolder(env, deps.connection, deps.nodeIdentity, deps.battery)
    val chat = ChatSettingsStateHolder(deps.chatPreferences)
}
