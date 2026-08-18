package com.example.staticwallpaper.render

import com.example.staticwallpaper.data.CompositionTransform
import com.example.staticwallpaper.data.LegacyTransform
import kotlin.math.max
import kotlin.math.min

data class TransformResult(val scale: Float, val translateX: Float, val translateY: Float)
data class SourceViewport(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

object TransformCalculator {
    fun centerCropScale(iw: Float, ih: Float, vw: Float, vh: Float) = max(vw / iw, vh / ih)
    fun fitCenterScale(iw: Float, ih: Float, vw: Float, vh: Float) = min(vw / iw, vh / ih)

    fun calculate(iw: Float, ih: Float, vw: Float, vh: Float, t: CompositionTransform): TransformResult {
        if (iw <= 0 || ih <= 0 || vw <= 0 || vh <= 0) return TransformResult(1f, 0f, 0f)
        val scale = fitCenterScale(iw, ih, vw, vh) * t.zoom.coerceIn(.05f, 20f)
        return TransformResult(scale, vw / 2f - iw * t.centerX * scale, vh / 2f - ih * t.centerY * scale)
    }

    fun viewport(iw: Float, ih: Float, vw: Float, vh: Float, t: CompositionTransform): SourceViewport {
        val result = calculate(iw, ih, vw, vh, t)
        val halfW = vw / result.scale / 2f
        val halfH = vh / result.scale / 2f
        val cx = iw * t.centerX
        val cy = ih * t.centerY
        return SourceViewport(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
    }

    fun centerCrop(iw: Float, ih: Float, vw: Float, vh: Float) = CompositionTransform(
        zoom = centerCropScale(iw, ih, vw, vh) / fitCenterScale(iw, ih, vw, vh),
        centerX = .5f, centerY = .5f, allowBackground = false
    )

    fun fitCenter() = CompositionTransform(zoom = 1f, centerX = .5f, centerY = .5f, allowBackground = true)

    fun panBySource(t: CompositionTransform, iw: Float, ih: Float, dxSource: Float, dySource: Float) =
        t.copy(centerX = t.centerX + dxSource / iw, centerY = t.centerY + dySource / ih)

    /** Moves the rendered image with the finger. Center coordinates therefore move in the opposite direction. */
    fun moveImageByCanvasPixels(
        t: CompositionTransform, iw: Float, ih: Float, vw: Float, vh: Float,
        dxCanvas: Float, dyCanvas: Float
    ): CompositionTransform {
        val scale = calculate(iw, ih, vw, vh, t).scale.coerceAtLeast(.0001f)
        return panBySource(t, iw, ih, -dxCanvas / scale, -dyCanvas / scale)
    }

    fun zoomAround(t: CompositionTransform, iw: Float, ih: Float, focusX: Float, focusY: Float, factor: Float): CompositionTransform {
        val safeFactor = factor.coerceIn(.5f, 2f)
        val oldCx = t.centerX * iw
        val oldCy = t.centerY * ih
        val newCx = focusX - (focusX - oldCx) / safeFactor
        val newCy = focusY - (focusY - oldCy) / safeFactor
        return t.copy(zoom = (t.zoom * safeFactor).coerceIn(.05f, 20f), centerX = newCx / iw, centerY = newCy / ih)
    }

    fun clamp(iw: Float, ih: Float, vw: Float, vh: Float, input: CompositionTransform, minVisibleFraction: Float = .2f): CompositionTransform {
        if (iw <= 0 || ih <= 0 || vw <= 0 || vh <= 0) return input
        val fit = fitCenterScale(iw, ih, vw, vh)
        val minFillZoom = centerCropScale(iw, ih, vw, vh) / fit
        val zoom = if (input.allowBackground) input.zoom.coerceIn(.05f, 20f) else input.zoom.coerceIn(minFillZoom, 20f)
        val scale = fit * zoom
        val scaledW = iw * scale
        val scaledH = ih * scale
        val xRange: ClosedFloatingPointRange<Float>
        val yRange: ClosedFloatingPointRange<Float>
        if (!input.allowBackground) {
            val hx = vw / 2f / scaledW
            val hy = vh / 2f / scaledH
            xRange = hx..(1f - hx)
            yRange = hy..(1f - hy)
        } else {
            val requiredX = min(vw * minVisibleFraction, scaledW)
            val requiredY = min(vh * minVisibleFraction, scaledH)
            xRange = ((requiredX - vw / 2f) / scaledW)..(1f + (vw / 2f - requiredX) / scaledW)
            yRange = ((requiredY - vh / 2f) / scaledH)..(1f + (vh / 2f - requiredY) / scaledH)
        }
        fun clampAxis(value: Float, range: ClosedFloatingPointRange<Float>): Float {
            // Exact-fit dimensions can cross by a few ULPs (for example 0.50000006..0.49999994).
            // Treat that degenerate interval as the single centered position instead of calling coerceIn.
            return if (range.start <= range.endInclusive) value.coerceIn(range) else (range.start + range.endInclusive) / 2f
        }
        return input.copy(zoom = zoom, centerX = clampAxis(input.centerX,xRange), centerY = clampAxis(input.centerY,yRange))
    }

    fun migrateLegacy(iw: Float, ih: Float, vw: Float, vh: Float, legacy: LegacyTransform): CompositionTransform {
        val fit = fitCenterScale(iw, ih, vw, vh)
        val scale = fit * legacy.scale.coerceIn(.05f, 20f)
        val scaledW = iw * scale
        val scaledH = ih * scale
        val panX = max(0f, (scaledW - vw) / 2f)
        val panY = max(0f, (scaledH - vh) / 2f)
        val tx = (vw - scaledW) / 2f + legacy.offsetX.coerceIn(-1f, 1f) * panX
        val ty = (vh - scaledH) / 2f + legacy.offsetY.coerceIn(-1f, 1f) * panY
        val centerX = (vw / 2f - tx) / scale / iw
        val centerY = (vh / 2f - ty) / scale / ih
        val fillZoom = centerCropScale(iw, ih, vw, vh) / fit
        return CompositionTransform(legacy.scale, centerX, centerY, legacy.scale + .0001f < fillZoom)
    }
}
