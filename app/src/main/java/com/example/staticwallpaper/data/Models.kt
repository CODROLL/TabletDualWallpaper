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
    val portrait: CompositionTransform = CompositionTransform()
)

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
}
