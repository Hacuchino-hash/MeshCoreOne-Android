// AndroidOnly: WP-213 port for the WP-218 inline-image dimensions store whose aspects feed MessageBuildInputs.
package com.meshcoreone.android.core.services.rendering

import kotlinx.coroutines.channels.ReceiveChannel

/**
 * What the rendering pipeline needs from `InlineImageDimensionsStore` (implementation owned by WP-218).
 * The view model resolves [aspect] into [MessageBuildInputs.inlineImageAspect] /
 * [MessageBuildInputs.previewHeroAspect] at build time and rebuilds when [resolutionUpdates] emits.
 *
 * Contract (from the Swift store and its tests): the store is disposable — a missing or undecodable
 * backing file starts empty; [aspect] is wait-free and keyed by the URL's absolute string; saves with a
 * non-positive width or height are rejected silently and do not emit; every save (including an
 * idempotent re-save) emits the URL to every subscriber registered before it; each subscriber buffers
 * the newest [RESOLUTION_BUFFER_DEPTH] events.
 */
interface InlineImageDimensionsStoring {
    /** Width-over-height ratio last saved for [url], or null. Never suspends. */
    fun aspect(url: WebURL): Double?

    /** Upserts `width / height` for [url] and emits it; non-positive dimensions are ignored. */
    suspend fun save(url: WebURL, width: Double, height: Double)

    /** A new subscription, registered on return, receiving every URL saved afterwards. */
    fun resolutionUpdates(): ReceiveChannel<WebURL>

    companion object {
        const val RESOLUTION_BUFFER_DEPTH: Int = 64
    }
}
