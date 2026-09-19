package net.icantpy.cosmetics.morph

/** Older loaders cannot reposition the crosshair, so keep their first-person view aligned. */
internal object MorphCameraHeight {
    fun resolve(
        firstPerson: Boolean,
        followMorphEyeHeight: Boolean,
        playerEyeHeight: Float,
        morphEyeHeight: Float?,
        crosshairHookAvailable: Boolean = true,
    ): Float = if (!followMorphEyeHeight || (firstPerson && !crosshairHookAvailable)) {
        playerEyeHeight
    } else {
        morphEyeHeight ?: playerEyeHeight
    }
}
