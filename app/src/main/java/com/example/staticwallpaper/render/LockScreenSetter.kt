package com.example.staticwallpaper.render

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.staticwallpaper.data.WallpaperConfig
import com.example.staticwallpaper.data.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.max
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
        var renderedFile: File? = null
        try {
            val selection=config.renderSelection(WallpaperTarget.LOCK,width,height)
            val uri=requireNotNull(selection.imageUri){"请先选择当前方向的锁屏图片"}
            val manager=WallpaperManager.getInstance(context)
            check(manager.isWallpaperSupported){"此设备不支持由应用设置壁纸"}
            check(manager.isSetWallpaperAllowed){"系统策略不允许应用修改壁纸"}
            val (safeWidth,safeHeight)=safeOutputSize(width,height)
            lease=BitmapCache.acquire(context.contentResolver,uri,max(safeWidth,safeHeight).coerceAtMost(3072))?:error("无法读取锁屏图片")
            output=Bitmap.createBitmap(safeWidth,safeHeight,Bitmap.Config.RGB_565)
            WallpaperRenderer.draw(
                Canvas(output),lease.bitmap,config,
                selection.transform,safeWidth,safeHeight
            )
            renderedFile=File.createTempFile("lock-wallpaper-",".jpg",context.cacheDir)
            FileOutputStream(renderedFile).use{stream->check(output.compress(Bitmap.CompressFormat.JPEG,95,stream)){"无法生成锁屏文件"}}
            // Huawei's compatibility layer is more stable when the graphics memory has been released
            // before the wallpaper service receives a file stream instead of a large Binder Bitmap.
            output.recycle();output=null
            lease.close();lease=null
            val wallpaperId=FileInputStream(renderedFile).use{stream->manager.setStream(stream,null,true,WallpaperManager.FLAG_LOCK)}
            check(wallpaperId!=0){"华为系统未接受锁屏图片"}
            Result.success(Unit)
        } catch (error: OutOfMemoryError) {
            Result.failure(IllegalStateException("生成锁屏图片时内存不足，请切换到节省内存模式后重试",error))
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            output?.takeUnless { it.isRecycled }?.recycle()
            lease?.close()
            renderedFile?.delete()
        }
    }
}
