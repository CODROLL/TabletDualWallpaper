package com.example.staticwallpaper.data

import android.app.Activity
import android.os.Build
import android.util.DisplayMetrics
import kotlin.math.max
import kotlin.math.min

data class PixelSize(val width: Int, val height: Int)

data class DisplayProfile(val physicalWidth: Int, val physicalHeight: Int, val densityDpi: Int) {
    val landscape = PixelSize(max(physicalWidth, physicalHeight), min(physicalWidth, physicalHeight))
    val portrait = PixelSize(min(physicalWidth, physicalHeight), max(physicalWidth, physicalHeight))
    fun canvas(isLandscape: Boolean) = if (isLandscape) landscape else portrait

    companion object {
        @Suppress("DEPRECATION")
        fun from(activity: Activity): DisplayProfile {
            val display = if (Build.VERSION.SDK_INT >= 30) activity.display else activity.windowManager.defaultDisplay
            val mode = display?.mode
            if (mode != null && mode.physicalWidth > 0 && mode.physicalHeight > 0) {
                return DisplayProfile(mode.physicalWidth, mode.physicalHeight, activity.resources.displayMetrics.densityDpi)
            }
            val metrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getRealMetrics(metrics)
            return DisplayProfile(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        }
    }
}
