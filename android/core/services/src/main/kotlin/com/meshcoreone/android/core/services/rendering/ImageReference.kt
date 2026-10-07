// PortedFrom: MC1Services/Sources/MC1Services/Models/Rendering/ImageReference.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.rendering

import java.util.UUID

/**
 * Handle to a decoded image owned by the view-model layer. Equality is structural (cache key plus
 * role); the bubble resolves the actual bitmap through an image-resolver callback at render time.
 */
data class ImageReference(val cacheKey: UUID, val role: Role) {
    enum class Role { INLINE, LINK_PREVIEW_IMAGE, LINK_PREVIEW_ICON }
}
