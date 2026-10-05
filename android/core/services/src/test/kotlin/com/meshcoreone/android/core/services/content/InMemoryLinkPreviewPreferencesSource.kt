// AndroidOnly: WP-218 test-only in-memory LinkPreviewPreferencesSource. Lives in test source
// (not src/main) per coordinator review: a synchronous in-memory reference must never be
// reachable as a silent production fallback that bypasses the real DataStore-backed adapter.
// Uses the same StateFlow-read/suspend-write shape the real adapter must honor, so tests
// exercise the actual producer contract rather than a shortcut shape.
package com.meshcoreone.android.core.services.content

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryLinkPreviewPreferencesSource(
    initial: LinkPreviewPreferencesSnapshot = LinkPreviewPreferencesSnapshot(),
) : LinkPreviewPreferencesSource {
    private val state = MutableStateFlow(initial)
    override val preferences: StateFlow<LinkPreviewPreferencesSnapshot> = state.asStateFlow()

    override suspend fun update(snapshot: LinkPreviewPreferencesSnapshot) {
        state.value = snapshot
    }
}
