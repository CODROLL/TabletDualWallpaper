package com.example.staticwallpaper.render

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import com.example.staticwallpaper.data.MemoryMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

object BitmapCache {
    data class Key(val uri:String,val maxLongEdge:Int)
    class Lease internal constructor(val key:Key,val bitmap:Bitmap):AutoCloseable{
        private var closed=false
        override fun close(){synchronized(this){if(!closed){closed=true;release(key)}}}
    }
    private data class Entry(val bitmap:Bitmap,var references:Int)
    private val entries=mutableMapOf<Key,Entry>()

    suspend fun acquire(resolver:ContentResolver,uri:String,mode:MemoryMode):Lease? = acquire(resolver,uri,mode.maxLongEdge)

    suspend fun acquire(resolver:ContentResolver,uri:String,maxLongEdge:Int):Lease?{
        val key=Key(uri,maxLongEdge)
        synchronized(entries){entries[key]?.let{it.references++;return Lease(key,it.bitmap)}}
        val decoded=withContext(Dispatchers.IO+NonCancellable){runCatching{BitmapDecoder.decode(resolver,Uri.parse(uri),maxLongEdge)}.getOrNull()}?:return null
        if(!currentCoroutineContext().isActive){decoded.recycle();return null}
        synchronized(entries){
            val existing=entries[key]
            return if(existing!=null){existing.references++;decoded.recycle();Lease(key,existing.bitmap)}else{entries[key]=Entry(decoded,1);Lease(key,decoded)}
        }
    }

    private fun release(key:Key){synchronized(entries){val entry=entries[key]?:return;entry.references--;if(entry.references<=0){entries.remove(key);entry.bitmap.recycle()}}}
}
