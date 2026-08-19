package com.example.staticwallpaper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.staticwallpaper.MainActivity
import com.example.staticwallpaper.R
import com.example.staticwallpaper.data.ConfigRepository
import com.example.staticwallpaper.data.CompositionTransform
import com.example.staticwallpaper.data.DisplayProfile
import com.example.staticwallpaper.data.WallpaperConfig
import com.example.staticwallpaper.data.WallpaperTarget
import com.example.staticwallpaper.render.LockScreenSetter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * User-enabled fallback for OEMs that do not bind third-party live wallpapers to Keyguard.
 * It observes stable display orientation changes and reapplies the corresponding static lock image.
 */
class AutoLockWallpaperService : Service(), DisplayManager.DisplayListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy { ConfigRepository(applicationContext) }
    private lateinit var displayManager: DisplayManager
    private var pendingApply: Job? = null
    private var config = WallpaperConfig()
    private var lastApplied: ApplyKey? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat(notification("正在启动锁屏横竖自动切换…"))
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager.registerDisplayListener(this, null)
        scope.launch {
            repository.config.collectLatest { next ->
                config = next
                if (!next.autoLockEnabled) {
                    stopServiceNow()
                } else {
                    scheduleApply(force = true)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                val current = repository.config.first()
                repository.save(current.copy(autoLockEnabled = false))
                stopServiceNow()
            }
            return START_NOT_STICKY
        }
        scheduleApply(force = false)
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scheduleApply(force = false)
    }

    override fun onDisplayChanged(displayId: Int) = scheduleApply(force = false)
    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) = Unit
    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleApply(force: Boolean) {
        pendingApply?.cancel()
        pendingApply = scope.launch {
            delay(ORIENTATION_DEBOUNCE_MS)
            val snapshot = config
            if (!snapshot.autoLockEnabled) return@launch
            val landscape = currentLandscape()
            val profile = DisplayProfile.from(this@AutoLockWallpaperService)
            val size = profile.canvas(landscape)
            val selection = snapshot.renderSelection(WallpaperTarget.LOCK, size.width, size.height)
            val key = ApplyKey(
                landscape = landscape,
                imageUri = selection.imageUri,
                transform = selection.transform,
                backgroundMode = snapshot.backgroundMode.name,
                backgroundColor = snapshot.backgroundColor,
                memoryMode = snapshot.memoryMode.name
            )
            if (!force && key == lastApplied) return@launch
            if (selection.imageUri == null) {
                updateNotification("当前方向尚未选择锁屏图片")
                return@launch
            }

            updateNotification("正在应用${if (landscape) "横屏" else "竖屏"}锁屏…")
            LockScreenSetter.apply(applicationContext, snapshot, size.width, size.height)
                .onSuccess {
                    lastApplied = key
                    updateNotification("已应用${if (landscape) "横屏" else "竖屏"}锁屏，正在监测方向")
                }
                .onFailure {
                    updateNotification("自动切换失败：${it.message ?: "系统拒绝设置"}")
                }
        }
    }

    private fun currentLandscape(): Boolean {
        return when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> true
            Configuration.ORIENTATION_PORTRAIT -> false
            else -> {
                val display = if (Build.VERSION.SDK_INT >= 30) display else displayManager.getDisplay(0)
                val mode = display?.mode
                if (display == null || mode == null) true
                else {
                    val naturalLandscape = mode.physicalWidth >= mode.physicalHeight
                    val sideways = display.rotation == 1 || display.rotation == 3
                    if (sideways) !naturalLandscape else naturalLandscape
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "锁屏横竖自动切换",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持服务运行并在平板旋转后更新静态锁屏图片"
                setShowBadge(false)
            }
        )
    }

    private fun notification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopService = PendingIntent.getService(
            this,
            1,
            Intent(this, AutoLockWallpaperService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wallpaper)
            .setContentTitle("锁屏横竖自动切换")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_wallpaper),
                    "停止",
                    stopService
                ).build()
            )
            .build()
    }

    private fun startForegroundCompat(value: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, value, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, value)
        }
    }

    private fun updateNotification(message: String) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(message))
        }
    }

    private fun stopServiceNow() {
        pendingApply?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        pendingApply?.cancel()
        if (::displayManager.isInitialized) displayManager.unregisterDisplayListener(this)
        scope.cancel()
        super.onDestroy()
    }

    private data class ApplyKey(
        val landscape: Boolean,
        val imageUri: String?,
        val transform: CompositionTransform,
        val backgroundMode: String,
        val backgroundColor: Long,
        val memoryMode: String
    )

    companion object {
        private const val CHANNEL_ID = "auto_lock_orientation"
        private const val NOTIFICATION_ID = 4102
        private const val ORIENTATION_DEBOUNCE_MS = 900L
        const val ACTION_START = "com.example.staticwallpaper.AUTO_LOCK_START"
        const val ACTION_STOP = "com.example.staticwallpaper.AUTO_LOCK_STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AutoLockWallpaperService::class.java).setAction(ACTION_START)
            )
        }
    }
}

class AutoLockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val config = ConfigRepository(context.applicationContext).config.first()
                if (config.autoLockEnabled) runCatching { AutoLockWallpaperService.start(context) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
