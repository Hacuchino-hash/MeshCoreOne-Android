// AndroidOnly: WP-316 Resource ids of the printf-style core:l10n strings, whose generated accessors need a Resources instance.
package com.meshcoreone.android.feature.tools.diagnostics

import com.meshcoreone.android.core.l10n.R

/**
 * The generated `App*Strings` objects expose formatted strings only as functions taking
 * `Resources`; the logic layer formats through [DiagnosticsText.format] with these ids instead.
 * Argument types follow the generated signatures (Long counts, Int where the accessor takes Int).
 */
internal object FormattedStringIds {
    /** `AppToolsStrings.toolsCliChannelsHeader(resources, Long)`. */
    val CLI_CHANNELS_HEADER: Int get() = R.string.l10n_app_tools_tools_cli_channelsheader

    /** `AppToolsStrings.toolsCliConfirmPrompt(resources, String)`. */
    val CLI_CONFIRM_PROMPT: Int get() = R.string.l10n_app_tools_tools_cli_confirmprompt

    /** `AppToolsStrings.toolsCliLoggingIn(resources, Int)`. */
    val CLI_LOGGING_IN: Int get() = R.string.l10n_app_tools_tools_cli_loggingin

    /** `AppToolsStrings.toolsCliNodesHeader(resources, Long)`. */
    val CLI_NODES_HEADER: Int get() = R.string.l10n_app_tools_tools_cli_nodesheader

    /** `AppToolsStrings.toolsCliWelcomeConnected(resources, String)`. */
    val CLI_WELCOME_CONNECTED: Int get() = R.string.l10n_app_tools_tools_cli_welcomeconnected

    /** `AppRemoteNodesStrings.remoteNodesNodeCliBannerConnected(resources, String)`. */
    val NODE_CLI_BANNER_CONNECTED: Int get() = R.string.l10n_app_remotenodes_remotenodes_nodecli_bannerconnected

    /** `AppContactsStrings.contactsRouteHops(resources, Int)`. */
    val CONTACTS_ROUTE_HOPS: Int get() = R.string.l10n_app_contacts_contacts_route_hops

    /** `AppToolsStrings.toolsNoiseFloorChartAccessibility(resources, Long, Long, Long, Long, String)`. */
    val NOISE_FLOOR_CHART_ACCESSIBILITY: Int get() = R.string.l10n_app_tools_tools_noisefloor_chartaccessibility

    /** `AppToolsStrings.toolsRxLogPacketsCount(resources, Long)`. */
    val RX_LOG_PACKETS_COUNT: Int get() = R.string.l10n_app_tools_tools_rxlog_packetscount

    /** `AppToolsStrings.toolsRxLogReceivedTimes(resources, Long)`. */
    val RX_LOG_RECEIVED_TIMES: Int get() = R.string.l10n_app_tools_tools_rxlog_receivedtimes
}
