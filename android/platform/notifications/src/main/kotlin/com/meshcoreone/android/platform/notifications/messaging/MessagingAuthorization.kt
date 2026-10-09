// AndroidOnly: WP-401 Permission mapping; denial degrades notifications only, never messaging.
package com.meshcoreone.android.platform.notifications.messaging

import com.meshcoreone.android.core.contracts.notifications.NotificationAuthorizationStatus

/** App-owned permission prompt (needs an Activity), supplied by the app layer. Absent means "cannot ask". */
interface NotificationPermissionGateway {
    /** Whether the runtime POST_NOTIFICATIONS grant (API 33+) is held. Always true below API 33. */
    fun isGranted(): Boolean

    /** Whether the user has already been prompted (distinguishes NOT_DETERMINED from DENIED). */
    fun wasRequested(): Boolean

    /** Shows the prompt; false when denied or no UI is available. */
    suspend fun request(): Boolean
}

object MessagingAuthorization {
    /**
     * [granted] is the runtime grant, [appEnabled] the user's app-level toggle in system settings,
     * [requested] whether the prompt was already shown.
     */
    fun status(granted: Boolean, appEnabled: Boolean, requested: Boolean): NotificationAuthorizationStatus = when {
        granted && appEnabled -> NotificationAuthorizationStatus.AUTHORIZED
        !granted && !requested -> NotificationAuthorizationStatus.NOT_DETERMINED
        else -> NotificationAuthorizationStatus.DENIED
    }
}
