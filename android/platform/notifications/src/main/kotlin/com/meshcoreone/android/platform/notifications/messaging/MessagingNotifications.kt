// AndroidOnly: WP-401 Process-wide response router shared by the receiver (cold start) and the app wiring.
package com.meshcoreone.android.platform.notifications.messaging

/**
 * The receiver is instantiated by the system, possibly in a freshly started process before any service
 * graph exists, so it reaches the router through this holder. The app layer calls
 * `MessagingNotifications.router.attach(sink)` once the WP-215 service is wired (and `detach` on teardown);
 * anything received earlier is replayed in order.
 */
object MessagingNotifications {
    val router: MessagingResponseRouter = MessagingResponseRouter()
}
