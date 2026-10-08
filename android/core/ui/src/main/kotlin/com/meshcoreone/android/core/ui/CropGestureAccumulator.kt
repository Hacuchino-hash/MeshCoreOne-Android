// AndroidOnly: WP-304 Accumulate native pointer deltas between controlled-state recompositions.
package com.meshcoreone.android.core.ui

internal class CropGestureAccumulator(initial: AvatarCropGeometry) {
    private var observed = initial
    private var current = initial

    fun transform(external: AvatarCropGeometry, zoom: Double, translation: CropOffset): AvatarCropGeometry {
        if (external != observed) {
            observed = external
            current = external
        }
        val next = current.liveTransform(zoom, translation)
        current = current.copy(scale = next.scale, offset = next.offset)
        return current
    }
}
