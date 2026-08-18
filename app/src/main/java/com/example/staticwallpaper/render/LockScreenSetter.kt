package com.example.staticwallpaper.render

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.staticwallpaper.data.MemoryMode
import com.example.staticwallpaper.data.WallpaperConfig
import com.example.staticwallpaper.data.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

object LockScreenSetter {
    private const val MAX_OUTPUT_PIXELS = 4_194_304L

    fun safeOutputSize(width: Int, height: Int, maxPixels: Long = MAX_OUTPUT_PIXELS): Pair<Int, Int> {
        require(width > 0 && height > 0) { "锁屏尺寸无效" }
        val pixels = width.toLong() * height.toLong()
        if (pixels <= maxPixels) return width to height
        val factor = sqrt(maxPixels.toDouble() / pixels.toDouble())
        return (width * factor).toInt().coerceAtLeast(1) to (height * factor).toInt().coerceAtLeast(1)
    }

    suspend fun apply(context:Context,config:WallpaperConfig,width:Int,height:Int):Result<Unit> = withContext(Dispatchers.IO) {
        var lease: BitmapCache.Lease? = null
        var output: Bitmap? = null
        try {
            val uri=requireNotNull(config.lock.imageUri){"请先选择锁屏图片"}
            val manager=WallpaperManager.getInstance(context)
            check(manager.isWallpaperSupported){"此设备不支持由应用设置壁纸"}
            check(manager.isSetWallpaperAllowed){"系统策略不允许应用修改壁纸"}
            // 3072px 足以覆盖主流平板锁屏，同时可与首页预览复用缓存，避免再解码一份 4K/6K Bitmap。
            lease=BitmapCache.acquire(context.contentResolver,uri,MemoryMode.SAVING)?:error("无法读取锁屏图片")
            val (safeWidth,safeHeight)=safeOutputSize(width,height)
            output=Bitmap.createBitmap(safeWidth,safeHeight,Bitmap.Config.RGB_565)
            WallpaperRenderer.draw(
                Canvas(output),lease.bitmap,config,
                config.transform(WallpaperTarget.LOCK,width>height),safeWidth,safeHeight
            )
            manager.setBitmap(output,null,true,WallpaperManager.FLAG_LOCK)
            Result.success(Unit)
        } catch (error: OutOfMemoryError) {
            Result.failure(IllegalStateException("生成锁屏图片时内存不足，请切换到节省内存模式后重试",error))
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            output?.takeUnless { it.isRecycled }?.recycle()
            lease?.close()
        }
    }
}
