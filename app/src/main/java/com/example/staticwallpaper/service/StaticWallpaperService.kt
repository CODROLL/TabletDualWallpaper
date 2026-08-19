package com.example.staticwallpaper.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.os.UserManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.example.staticwallpaper.data.ConfigRepository
import com.example.staticwallpaper.data.DirectBootConfigStore
import com.example.staticwallpaper.data.DisplayProfile
import com.example.staticwallpaper.data.WallpaperConfig
import com.example.staticwallpaper.data.WallpaperTarget
import com.example.staticwallpaper.render.BitmapCache
import com.example.staticwallpaper.render.WallpaperRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Shared event-driven renderer used by the normal and experimental components. */
abstract class RenderingWallpaperService : WallpaperService() {
    protected abstract val renderingTarget: WallpaperTarget

    protected open suspend fun loadConfig(): WallpaperConfig =
        ConfigRepository(applicationContext).config.first()

    override fun onCreateEngine(): Engine = RenderingEngine()

    inner class RenderingEngine : Engine() {
        private val thread = HandlerThread("wallpaper-render-${hashCode()}").apply { start() }
        private val handler = Handler(thread.looper)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var lease: BitmapCache.Lease? = null
        private var config = WallpaperConfig()
        private var configLoaded = false
        private var visible = false
        private var width = 0
        private var height = 0
        private var xOffset = .5f
        private var configGeneration = 0
        private var bitmapGeneration = 0
        private var legacyMigrationStarted = false

        @Volatile private var destroyed = false

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = reloadConfig()
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setOffsetNotificationsEnabled(true)
            val filter = IntentFilter(ConfigRepository.ACTION_CONFIG_CHANGED).apply {
                addAction(Intent.ACTION_USER_UNLOCKED)
            }
            ContextCompat.registerReceiver(
                this@RenderingWallpaperService,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            reloadConfig()
        }

        private fun target(): WallpaperTarget = renderingTarget

        private fun reloadConfig() {
            val request = ++configGeneration
            scope.launch {
                val next = runCatching { loadConfig() }
                handler.post {
                    if (destroyed || request != configGeneration) return@post
                    next.onSuccess {
                        config = it
                        configLoaded = true
                        maybeMigrate()
                    }
                    requestBitmapAndDraw()
                }
            }
        }

        /** URI selection happens only after Android supplies the real Surface size. */
        private fun requestBitmapAndDraw() {
            if (destroyed || !configLoaded || !visible || width <= 0 || height <= 0) return
            val uri = config.renderSelection(target(), width, height).imageUri
            val desiredKey = uri?.let { BitmapCache.Key(it, config.memoryMode.maxLongEdge) }
            if (lease?.key == desiredKey) {
                drawFrame()
                return
            }

            val request = ++bitmapGeneration
            if (uri == null) {
                lease?.close()
                lease = null
                drawFrame()
                return
            }
            val memoryMode = config.memoryMode
            scope.launch {
                val nextLease = BitmapCache.acquire(contentResolver, uri, memoryMode)
                handler.post {
                    if (destroyed || request != bitmapGeneration || !visible) {
                        nextLease?.close()
                        return@post
                    }
                    lease?.close()
                    lease = nextLease
                    maybeMigrate()
                    drawFrame()
                }
            }
        }

        private fun maybeMigrate() {
            val bitmap = lease?.bitmap ?: return
            if (config.legacy == null || width <= 0 || height <= 0 || legacyMigrationStarted) return
            legacyMigrationStarted = true
            val snapshot = config
            scope.launch {
                ConfigRepository(applicationContext).migrateLegacy(
                    snapshot,
                    bitmap.width,
                    bitmap.height,
                    DisplayProfile(width, height, resources.displayMetrics.densityDpi)
                )
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            requestBitmapAndDraw()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, newWidth: Int, newHeight: Int) {
            width = newWidth
            height = newHeight
            requestBitmapAndDraw()
        }

        override fun onVisibilityChanged(value: Boolean) {
            visible = value
            if (value) {
                requestBitmapAndDraw()
            } else {
                bitmapGeneration++
                handler.removeCallbacksAndMessages(null)
                lease?.close()
                lease = null
            }
        }

        override fun onOffsetsChanged(
            x: Float,
            xStep: Float,
            y: Float,
            yStep: Float,
            xPixels: Int,
            yPixels: Int
        ) {
            xOffset = x
            if (config.parallaxEnabled && target() == WallpaperTarget.DESKTOP) drawFrame()
        }

        private fun drawFrame() {
            if (destroyed || !visible || width <= 0 || height <= 0) return
            handler.removeCallbacks(drawRunnable)
            handler.post(drawRunnable)
        }

        private val drawRunnable = Runnable {
            val bitmap = lease?.bitmap
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas() ?: return@Runnable
                canvas.drawColor(Color.BLACK)
                if (bitmap != null && !bitmap.isRecycled) {
                    val selectedTarget = target()
                    val transform = config.renderSelection(selectedTarget, width, height).transform
                    val parallax = if (config.parallaxEnabled && selectedTarget == WallpaperTarget.DESKTOP) {
                        ((xOffset - .5f) * .2f).coerceIn(-.1f, .1f)
                    } else 0f
                    WallpaperRenderer.draw(canvas, bitmap, config, transform, width, height, parallax)
                }
            } catch (_: Throwable) {
                // OEM launchers may destroy a Surface between visibility and lockCanvas.
            } finally {
                if (canvas != null) runCatching { surfaceHolder.unlockCanvasAndPost(canvas) }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            bitmapGeneration++
            handler.removeCallbacksAndMessages(null)
            lease?.close()
            lease = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            destroyed = true
            configGeneration++
            bitmapGeneration++
            runCatching { unregisterReceiver(receiver) }
            scope.cancel()
            handler.removeCallbacksAndMessages(null)
            lease?.close()
            lease = null
            thread.quitSafely()
            super.onDestroy()
        }
    }
}

/** Stable component that remains dedicated to the desktop configuration. */
class StaticWallpaperService : RenderingWallpaperService() {
    override val renderingTarget: WallpaperTarget = WallpaperTarget.DESKTOP
}

/** Dedicated experiment that always renders the lock configuration. */
class ExperimentalLockWallpaperService : RenderingWallpaperService() {
    override val renderingTarget: WallpaperTarget = WallpaperTarget.LOCK

    override suspend fun loadConfig(): WallpaperConfig {
        val userManager = getSystemService(UserManager::class.java)
        return if (userManager?.isUserUnlocked == false) {
            DirectBootConfigStore.read(this) ?: WallpaperConfig()
        } else {
            super.loadConfig()
        }
    }
}
