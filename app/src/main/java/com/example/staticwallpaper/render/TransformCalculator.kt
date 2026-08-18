package com.example.staticwallpaper.render

import com.example.staticwallpaper.data.OrientationTransform
import kotlin.math.max
import kotlin.math.min

data class TransformResult(val scale: Float, val translateX: Float, val translateY: Float)

object TransformCalculator {
    fun centerCropScale(iw: Float, ih: Float, vw: Float, vh: Float) = max(vw / iw, vh / ih)
    fun fitCenterScale(iw: Float, ih: Float, vw: Float, vh: Float) = min(vw / iw, vh / ih)

    /** scale is relative to fitCenter; offsets are fractions of the remaining pan range. */
    fun calculate(iw: Float, ih: Float, vw: Float, vh: Float, t: OrientationTransform): TransformResult {
        if (iw <= 0 || ih <= 0 || vw <= 0 || vh <= 0) return TransformResult(1f, 0f, 0f)
        val s = fitCenterScale(iw, ih, vw, vh) * t.scale.coerceIn(0.05f, 20f)
        val scaledW = iw * s
        val scaledH = ih * s
        val panX = max(0f, (scaledW - vw) / 2f)
        val panY = max(0f, (scaledH - vh) / 2f)
        val x = (vw - scaledW) / 2f + t.normalizedOffsetX.coerceIn(-1f, 1f) * panX
        val y = (vh - scaledH) / 2f + t.normalizedOffsetY.coerceIn(-1f, 1f) * panY
        return TransformResult(s, x, y)
    }

    fun clamp(iw: Float, ih: Float, vw: Float, vh: Float, t: OrientationTransform, requireFill: Boolean): OrientationTransform {
        val fit = fitCenterScale(iw, ih, vw, vh)
        val minRelative = if (requireFill) centerCropScale(iw, ih, vw, vh) / fit else 1f
        return t.copy(scale = t.scale.coerceIn(minRelative, 20f), normalizedOffsetX = t.normalizedOffsetX.coerceIn(-1f, 1f), normalizedOffsetY = t.normalizedOffsetY.coerceIn(-1f, 1f))
    }

    fun pixelDeltaToNormalized(iw: Float, ih: Float, vw: Float, vh: Float, t: OrientationTransform, dx: Float, dy: Float): OrientationTransform {
        val r = calculate(iw, ih, vw, vh, t)
        val px = max(0f, (iw * r.scale - vw) / 2f)
        val py = max(0f, (ih * r.scale - vh) / 2f)
        return t.copy(
            normalizedOffsetX = (t.normalizedOffsetX + if (px == 0f) 0f else dx / px).coerceIn(-1f, 1f),
            normalizedOffsetY = (t.normalizedOffsetY + if (py == 0f) 0f else dy / py).coerceIn(-1f, 1f)
        )
    }

    fun defaultFor(iw: Float, ih: Float, vw: Float, vh: Float, fill: Boolean = true): OrientationTransform {
        val fit = fitCenterScale(iw, ih, vw, vh)
        return OrientationTransform(if (fill) centerCropScale(iw, ih, vw, vh) / fit else 1f, 0f, 0f)
    }
}
