package com.example.staticwallpaper.render

import android.graphics.*
import com.example.staticwallpaper.data.BackgroundMode
import com.example.staticwallpaper.data.OrientationTransform
import com.example.staticwallpaper.data.WallpaperConfig

/** Shared Canvas renderer used by both live wallpaper and static lock-screen fallback. */
object WallpaperRenderer {
    private val imagePaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun draw(
        canvas: Canvas,
        bitmap: Bitmap,
        config: WallpaperConfig,
        transform: OrientationTransform,
        width: Int,
        height: Int,
        normalizedParallax: Float = 0f
    ) {
        val background=when(config.backgroundMode) {
            BackgroundMode.BLACK -> Color.BLACK
            BackgroundMode.COLOR -> config.backgroundColor.toInt()
            BackgroundMode.EDGE -> bitmap.getPixel(bitmap.width/2,bitmap.height/2)
            BackgroundMode.BLUR -> Color.BLACK
        }
        canvas.drawColor(background)
        if(config.backgroundMode==BackgroundMode.BLUR) drawBackdrop(canvas,bitmap,width,height)
        val adjusted=transform.copy(normalizedOffsetX=(transform.normalizedOffsetX+normalizedParallax).coerceIn(-1f,1f))
        val result=TransformCalculator.calculate(bitmap.width.toFloat(),bitmap.height.toFloat(),width.toFloat(),height.toFloat(),adjusted)
        val matrix=Matrix().apply { postScale(result.scale,result.scale);postTranslate(result.translateX,result.translateY) }
        canvas.drawBitmap(bitmap,matrix,imagePaint)
    }

    private fun drawBackdrop(canvas: Canvas,bitmap: Bitmap,width: Int,height: Int) {
        val scale=TransformCalculator.centerCropScale(bitmap.width.toFloat(),bitmap.height.toFloat(),width.toFloat(),height.toFloat())
        val matrix=Matrix().apply { postScale(scale,scale);postTranslate((width-bitmap.width*scale)/2f,(height-bitmap.height*scale)/2f) }
        val paint=Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter=PorterDuffColorFilter(0x99000000.toInt(),PorterDuff.Mode.SRC_OVER) }
        canvas.drawBitmap(bitmap,matrix,paint)
    }
}
