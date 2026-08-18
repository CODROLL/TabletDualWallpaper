package com.example.staticwallpaper.render

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.staticwallpaper.data.WallpaperConfig
import com.example.staticwallpaper.data.WallpaperTarget

object LockScreenSetter {
    suspend fun apply(context:Context,config:WallpaperConfig,width:Int,height:Int):Result<Unit> = runCatching {
        require(width>0&&height>0){"锁屏尺寸无效"}
        val uri=requireNotNull(config.lock.imageUri){"请先选择锁屏图片"}
        val lease=BitmapCache.acquire(context.contentResolver,uri,config.memoryMode)?:error("无法读取锁屏图片")
        val output=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try{
            WallpaperRenderer.draw(Canvas(output),lease.bitmap,config,config.transform(WallpaperTarget.LOCK,width>height),width,height)
            WallpaperManager.getInstance(context).setBitmap(output,null,true,WallpaperManager.FLAG_LOCK)
        }finally{output.recycle();lease.close()}
    }
}
