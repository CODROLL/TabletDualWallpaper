package com.example.staticwallpaper.service

import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.example.staticwallpaper.data.*
import com.example.staticwallpaper.render.BitmapDecoder
import com.example.staticwallpaper.render.TransformCalculator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class StaticWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = StaticEngine()

    inner class StaticEngine : Engine() {
        private val thread = HandlerThread("wallpaper-render-${hashCode()}").apply { start() }
        private val handler = Handler(thread.looper)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var bitmap: Bitmap? = null
        private var config = WallpaperConfig()
        private var visible = false
        private var width = 0; private var height = 0; private var xOffset = .5f
        @Volatile private var destroyed = false
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { reload(true) }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setOffsetNotificationsEnabled(true)
            registerReceiver(receiver, IntentFilter(ConfigRepository.ACTION_CONFIG_CHANGED), RECEIVER_NOT_EXPORTED)
            reload(true)
        }

        private fun reload(forceDecode: Boolean) {
            scope.launch {
                val next = ConfigRepository(applicationContext).config.first()
                val uriChanged = next.imageUri != config.imageUri || next.memoryMode != config.memoryMode
                config = next
                if ((forceDecode || uriChanged) && next.imageUri != null) {
                    val decoded = runCatching { BitmapDecoder.decode(contentResolver, Uri.parse(next.imageUri), next.memoryMode.maxLongEdge) }.getOrNull()
                    handler.post {
                        if (destroyed) decoded?.recycle() else {
                            val old = bitmap; bitmap = decoded; old?.takeIf { it !== decoded }?.recycle(); drawFrame()
                        }
                    }
                } else handler.post { drawFrame() }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) { super.onSurfaceCreated(holder); drawFrame() }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) { width=w; height=h; drawFrame() }
        override fun onVisibilityChanged(v: Boolean) { visible=v; if (v) { if (bitmap == null) reload(true) else drawFrame() } else handler.removeCallbacksAndMessages(null) }
        override fun onOffsetsChanged(x: Float, xs: Float, y: Float, ys: Float, xp: Int, yp: Int) { xOffset=x; if (config.parallaxEnabled) drawFrame() }

        private fun drawFrame() {
            if (destroyed || !visible || width <= 0 || height <= 0) return
            handler.removeCallbacks(drawRunnable); handler.post(drawRunnable)
        }
        private val drawRunnable = Runnable {
            val b = bitmap
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas() ?: return@Runnable
                val bg = when (config.backgroundMode) {
                    BackgroundMode.BLACK -> Color.BLACK
                    BackgroundMode.COLOR -> config.backgroundColor.toInt()
                    BackgroundMode.EDGE -> b?.let { it.getPixel(it.width / 2, it.height / 2) } ?: Color.BLACK
                    BackgroundMode.BLUR -> Color.BLACK
                }
                canvas.drawColor(bg)
                if (b != null && !b.isRecycled) {
                    if (config.backgroundMode == BackgroundMode.BLUR) drawBlurBackdrop(canvas, b)
                    val base = if (width > height) config.landscape else config.portrait
                    val parallax = if (config.parallaxEnabled) ((xOffset-.5f)*.20f).coerceIn(-.1f,.1f) else 0f
                    val t = base.copy(normalizedOffsetX=(base.normalizedOffsetX+parallax).coerceIn(-1f,1f))
                    val r = TransformCalculator.calculate(b.width.toFloat(), b.height.toFloat(), width.toFloat(), height.toFloat(), t)
                    val m = Matrix().apply { postScale(r.scale,r.scale); postTranslate(r.translateX,r.translateY) }
                    canvas.drawBitmap(b,m,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
                }
            } catch (_: Throwable) { } finally { if (canvas != null) runCatching { surfaceHolder.unlockCanvasAndPost(canvas) } }
        }
        private fun drawBlurBackdrop(c: Canvas, b: Bitmap) {
            val s=TransformCalculator.centerCropScale(b.width.toFloat(),b.height.toFloat(),width.toFloat(),height.toFloat())
            val m=Matrix().apply { postScale(s,s); postTranslate((width-b.width*s)/2,(height-b.height*s)/2) }
            val p=Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter=PorterDuffColorFilter(0x99000000.toInt(),PorterDuff.Mode.SRC_OVER) }
            c.drawBitmap(b,m,p)
        }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) { handler.removeCallbacksAndMessages(null); bitmap?.recycle(); bitmap=null; super.onSurfaceDestroyed(holder) }
        override fun onDestroy() {
            destroyed=true; runCatching { unregisterReceiver(receiver) }; scope.cancel(); handler.removeCallbacksAndMessages(null); bitmap?.recycle(); bitmap=null; thread.quitSafely(); super.onDestroy()
        }
    }
}
