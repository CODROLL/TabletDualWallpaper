package com.example.staticwallpaper.data

import kotlin.math.abs
import kotlin.math.min

/** Converts OrientationEventListener degrees into a screen shape with a diagonal dead zone. */
object PhysicalOrientationResolver {
    const val UNKNOWN = -1

    fun isLandscape(
        degrees: Int,
        naturalLandscape: Boolean,
        toleranceDegrees: Int = 30
    ): Boolean? {
        if (degrees == UNKNOWN || degrees !in 0..359) return null
        val normalized = degrees % 360
        val quarter = ((normalized + 45) / 90) % 4
        val cardinal = quarter * 90
        val directDistance = abs(normalized - cardinal)
        val distance = min(directDistance, 360 - directDistance)
        if (distance > toleranceDegrees.coerceIn(0, 44)) return null
        val alignedWithNaturalOrientation = quarter % 2 == 0
        return if (alignedWithNaturalOrientation) naturalLandscape else !naturalLandscape
    }
}
