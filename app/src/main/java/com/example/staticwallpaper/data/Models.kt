package com.example.staticwallpaper.data

enum class WallpaperTarget { DESKTOP, LOCK }

data class CompositionTransform(
    val zoom: Float = 1f,
    val centerX: Float = .5f,
    val centerY: Float = .5f,
    val allowBackground: Boolean = false
)

data class WallpaperSourceConfig(
    val imageUri: String? = null,
    val landscape: CompositionTransform = CompositionTransform(),
    val portrait: CompositionTransform = CompositionTransform(),
    /** Optional portrait override. Null keeps existing configurations on one shared image. */
    val portraitImageUri: String? = null
) {
    fun imageUri(landscape: Boolean): String? =
        if (landscape) imageUri else portraitImageUri ?: imageUri

    fun withImageUri(landscape: Boolean, value: String): WallpaperSourceConfig =
        if (landscape) {
            // Preserve the image that portrait was inheriting before landscape changes.
            copy(imageUri = value, portraitImageUri = portraitImageUri ?: imageUri)
        } else {
            copy(portraitImageUri = value)
        }

    fun withSharedImageUri(value: String): WallpaperSourceConfig =
        copy(imageUri = value, portraitImageUri = null)
}

data class LegacyTransform(val scale: Float, val offsetX: Float, val offsetY: Float)
data class LegacyConfig(
    val landscape: LegacyTransform,
    val portrait: LegacyTransform,
    val lockLandscape: LegacyTransform,
    val lockPortrait: LegacyTransform
)

enum class BackgroundMode { BLACK, COLOR, EDGE, BLUR }
enum class MemoryMode(val maxLongEdge: Int) { SAVING(3072), BALANCED(4096), HIGH(6144) }

data class WallpaperConfig(
    val desktop: WallpaperSourceConfig = WallpaperSourceConfig(),
    val lock: WallpaperSourceConfig = WallpaperSourceConfig(),
    val backgroundMode: BackgroundMode = BackgroundMode.BLACK,
    val backgroundColor: Long = 0xFF000000,
    val parallaxEnabled: Boolean = false,
    val memoryMode: MemoryMode = MemoryMode.BALANCED,
    val autoLockEnabled: Boolean = false,
    val legacyDynamicTarget: WallpaperTarget = WallpaperTarget.DESKTOP,
    val legacy: LegacyConfig? = null
) {
    fun source(target: WallpaperTarget) = if (target == WallpaperTarget.DESKTOP) desktop else lock
    fun transform(target: WallpaperTarget, landscape: Boolean): CompositionTransform =
        source(target).let { if (landscape) it.landscape else it.portrait }
    fun withSource(target: WallpaperTarget, source: WallpaperSourceConfig) =
        if (target == WallpaperTarget.DESKTOP) copy(desktop = source) else copy(lock = source)
    fun withTransform(target: WallpaperTarget, landscape: Boolean, value: CompositionTransform): WallpaperConfig {
        val source = source(target)
        return withSource(target, if (landscape) source.copy(landscape = value) else source.copy(portrait = value))
    }

    fun renderSelection(target: WallpaperTarget, width: Int, height: Int): WallpaperRenderSelection {
        val landscape = width > height
        return WallpaperRenderSelection(
            imageUri = source(target).imageUri(landscape),
            transform = transform(target, landscape),
            landscape = landscape
        )
    }
}

data class WallpaperRenderSelection(
    val imageUri: String?,
    val transform: CompositionTransform,
    val landscape: Boolean
)
