package com.example.staticwallpaper.render

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import com.example.staticwallpaper.data.WallpaperConfig
import com.example.staticwallpaper.data.transformFor

object LockScreenSetter {
    fun apply(context: Context,config: WallpaperConfig,width: Int,height: Int): Result<Unit> = runCatching {
        require(width>0 && height>0) { "锁屏尺寸无效" }
        val uri=requireNotNull(config.imageUri) { "请先选择图片" }
        val source=BitmapDecoder.decode(context.contentResolver,Uri.parse(uri),config.memoryMode.maxLongEdge)
            ?: error("无法读取图片")
        val output=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try {
            WallpaperRenderer.draw(Canvas(output),source,config,config.transformFor(width>height,true),width,height)
            WallpaperManager.getInstance(context).setBitmap(output,null,true,WallpaperManager.FLAG_LOCK)
        } finally {
            output.recycle();source.recycle()
        }
    }
}
