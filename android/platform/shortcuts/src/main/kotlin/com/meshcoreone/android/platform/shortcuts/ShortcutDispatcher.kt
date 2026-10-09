// AndroidOnly: WP-404 Cold-start safe, once-only hand-off of shortcut requests to the app-installed handler.
package com.meshcoreone.android.platform.shortcuts

import java.util.concurrent.atomic.AtomicReference

fun interface ShortcutRequestHandler {
    fun handle(request: ShortcutRequest)
}

/**
 * Holds at most one request until the app installs its handler (cold start), delivers it once,
 * and drops it when stale so an old share cannot surface later. Holding is not authorization.
 */
class ShortcutDispatcher(
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val holdTimeoutMillis: Long = DEFAULT_HOLD_TIMEOUT_MILLIS,
) {
    private data class Held(val request: ShortcutRequest, val atMillis: Long)

    sealed interface Outcome {
        data object Delivered : Outcome
        data object Held : Outcome
    }

    private val handler = AtomicReference<ShortcutRequestHandler?>(null)
    private val pending = AtomicReference<Held?>(null)

    fun submit(request: ShortcutRequest): Outcome {
        val current = handler.get()
        if (current == null) {
            pending.set(Held(request, clockMillis()))
            return Outcome.Held
        }
        current.handle(request)
        return Outcome.Delivered
    }

    /** Installs the handler and delivers a fresh held request exactly once. */
    fun install(newHandler: ShortcutRequestHandler) {
        handler.set(newHandler)
        val held = pending.getAndSet(null) ?: return
        if (clockMillis() - held.atMillis <= holdTimeoutMillis) newHandler.handle(held.request)
    }

    fun uninstall() {
        handler.set(null)
    }

    internal fun hasPending(): Boolean = pending.get() != null

    companion object {
        const val DEFAULT_HOLD_TIMEOUT_MILLIS: Long = 5 * 60 * 1000L
    }
}

/** Process-wide dispatcher the exported activities submit to and the app installs into. */
object ShortcutHost {
    val dispatcher: ShortcutDispatcher = ShortcutDispatcher()
}
