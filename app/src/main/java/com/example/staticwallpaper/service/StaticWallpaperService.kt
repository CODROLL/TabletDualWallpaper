package com.example.staticwallpaper.service

import android.app.WallpaperManager
import android.content.*
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.example.staticwallpaper.data.*
import com.example.staticwallpaper.render.BitmapCache
import com.example.staticwallpaper.render.WallpaperRenderer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class StaticWallpaperService:WallpaperService(){
    override fun onCreateEngine():Engine=StaticEngine()

    inner class StaticEngine:Engine(){
        private val thread=HandlerThread("wallpaper-render-${hashCode()}").apply{start()}
        private val handler=Handler(thread.looper)
        private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        private var lease:BitmapCache.Lease?=null
        private var config=WallpaperConfig()
        private var visible=false;private var width=0;private var height=0;private var xOffset=.5f
        private var generation=0
        private var legacyMigrationStarted=false
        @Volatile private var destroyed=false
        private val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){reload()}}

        override fun onCreate(holder:SurfaceHolder){super.onCreate(holder);setOffsetNotificationsEnabled(true);ContextCompat.registerReceiver(this@StaticWallpaperService,receiver,IntentFilter(ConfigRepository.ACTION_CONFIG_CHANGED),ContextCompat.RECEIVER_NOT_EXPORTED);reload()}

        private fun target(value:WallpaperConfig=config)=if(Build.VERSION.SDK_INT>=34){if((wallpaperFlags and WallpaperManager.FLAG_LOCK)!=0)WallpaperTarget.LOCK else WallpaperTarget.DESKTOP}else value.legacyDynamicTarget

        private fun reload(){
            val request=++generation
            scope.launch{
                val next=ConfigRepository(applicationContext).config.first();val source=next.source(target(next));val uri=source.imageUri
                val nextLease=if(uri==null)null else if(lease?.key==BitmapCache.Key(uri,next.memoryMode.maxLongEdge))lease else BitmapCache.acquire(contentResolver,uri,next.memoryMode)
                handler.post{
                    if(destroyed||request!=generation){if(nextLease!==lease)nextLease?.close();return@post}
                    if(nextLease!==lease){lease?.close();lease=nextLease};config=next;maybeMigrate();drawFrame()
                }
            }
        }

        private fun maybeMigrate(){
            val b=lease?.bitmap?:return;if(config.legacy==null||width<=0||height<=0||legacyMigrationStarted)return
            legacyMigrationStarted=true;val snapshot=config;scope.launch{ConfigRepository(applicationContext).migrateLegacy(snapshot,b.width,b.height,DisplayProfile(width,height,resources.displayMetrics.densityDpi))}
        }

        override fun onSurfaceCreated(holder:SurfaceHolder){super.onSurfaceCreated(holder);drawFrame()}
        override fun onSurfaceChanged(holder:SurfaceHolder,format:Int,w:Int,h:Int){width=w;height=h;maybeMigrate();drawFrame()}
        override fun onVisibilityChanged(value:Boolean){visible=value;if(value){if(lease==null)reload()else drawFrame()}else{handler.removeCallbacksAndMessages(null);lease?.close();lease=null}}
        override fun onOffsetsChanged(x:Float,xs:Float,y:Float,ys:Float,xp:Int,yp:Int){xOffset=x;if(config.parallaxEnabled)drawFrame()}
        override fun onWallpaperFlagsChanged(which:Int){super.onWallpaperFlagsChanged(which);reload()}

        private fun drawFrame(){if(destroyed||!visible||width<=0||height<=0)return;handler.removeCallbacks(drawRunnable);handler.post(drawRunnable)}
        private val drawRunnable=Runnable{
            val bitmap=lease?.bitmap;var canvas:Canvas?=null
            try{canvas=surfaceHolder.lockCanvas()?:return@Runnable;canvas.drawColor(Color.BLACK);if(bitmap!=null&&!bitmap.isRecycled){
                val t=config.transform(target(),width>height);val parallax=if(config.parallaxEnabled&&target()==WallpaperTarget.DESKTOP)((xOffset-.5f)*.2f).coerceIn(-.1f,.1f) else 0f
                WallpaperRenderer.draw(canvas,bitmap,config,t,width,height,parallax)
            }}catch(_:Throwable){}finally{if(canvas!=null)runCatching{surfaceHolder.unlockCanvasAndPost(canvas)}}
        }
        override fun onSurfaceDestroyed(holder:SurfaceHolder){handler.removeCallbacksAndMessages(null);lease?.close();lease=null;super.onSurfaceDestroyed(holder)}
        override fun onDestroy(){destroyed=true;generation++;runCatching{unregisterReceiver(receiver)};scope.cancel();handler.removeCallbacksAndMessages(null);lease?.close();lease=null;thread.quitSafely();super.onDestroy()}
    }
}
