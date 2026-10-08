// PortedFrom: MC1Services/Sources/MC1Services/Services/ResolvedFloodScope.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.contacts

import com.meshcoreone.android.core.protocol.model.FloodScope

/**
 * The flood-scope action [ChannelFloodScopeResolver] resolves to for a conversation.
 *
 * Distinguishes a true un-scoped override (firmware sub-command 1, sets `send_unscoped`) from a
 * concrete [FloodScope] push (sub-command 0). A zero-key [FloodScope.Disabled] resets the session
 * scope and lets the device fall back to its persisted default, so it cannot stand in for an
 * explicit "all regions" override on firmware that supports one.
 */
sealed interface ResolvedFloodScope {
    /** Force un-scoped flood broadcasts via `setFloodScopeUnscoped()`. Requires firmware v12+. */
    data object Unscoped : ResolvedFloodScope

    /** Push a concrete [FloodScope] via `setFloodScope(scope)`. */
    data class Scope(val scope: FloodScope) : ResolvedFloodScope
}
