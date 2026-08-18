package com.example.staticwallpaper.data

data class OrientationTransform(
    val scale: Float = 1f,
    val normalizedOffsetX: Float = 0f,
    val normalizedOffsetY: Float = 0f
)

enum class BackgroundMode { BLACK, COLOR, EDGE, BLUR }
enum class MemoryMode(val maxLongEdge: Int) { SAVING(3072), BALANCED(4096), HIGH(6144) }

data class WallpaperConfig(
    val imageUri: String? = null,
    val landscape: OrientationTransform = OrientationTransform(),
    val portrait: OrientationTransform = OrientationTransform(),
    val lockLandscape: OrientationTransform = landscape,
    val lockPortrait: OrientationTransform = portrait,
    val backgroundMode: BackgroundMode = BackgroundMode.BLACK,
    val backgroundColor: Long = 0xFF000000,
    val parallaxEnabled: Boolean = false,
    val memoryMode: MemoryMode = MemoryMode.BALANCED
)

fun WallpaperConfig.transformFor(isLandscape: Boolean, isLockScreen: Boolean): OrientationTransform =
    when {
        isLockScreen && isLandscape -> lockLandscape
        isLockScreen -> lockPortrait
        isLandscape -> landscape
        else -> portrait
    }
