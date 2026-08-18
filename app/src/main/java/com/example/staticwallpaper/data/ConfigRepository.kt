package com.example.staticwallpaper.data

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.configStore by preferencesDataStore("wallpaper_config")

class ConfigRepository(private val context: Context) {
    companion object {
        const val ACTION_CONFIG_CHANGED = "com.example.staticwallpaper.CONFIG_CHANGED"
        private val URI = stringPreferencesKey("uri")
        private val LS = floatPreferencesKey("ls"); private val LX = floatPreferencesKey("lx"); private val LY = floatPreferencesKey("ly")
        private val PS = floatPreferencesKey("ps"); private val PX = floatPreferencesKey("px"); private val PY = floatPreferencesKey("py")
        private val BG = stringPreferencesKey("bg"); private val COLOR = longPreferencesKey("color")
        private val PARALLAX = booleanPreferencesKey("parallax"); private val MEMORY = stringPreferencesKey("memory")
    }

    val config: Flow<WallpaperConfig> = context.configStore.data.map { p ->
        WallpaperConfig(
            p[URI], OrientationTransform(p[LS] ?: 1f, p[LX] ?: 0f, p[LY] ?: 0f),
            OrientationTransform(p[PS] ?: 1f, p[PX] ?: 0f, p[PY] ?: 0f),
            runCatching { BackgroundMode.valueOf(p[BG] ?: "BLACK") }.getOrDefault(BackgroundMode.BLACK),
            p[COLOR] ?: 0xFF000000, p[PARALLAX] ?: false,
            runCatching { MemoryMode.valueOf(p[MEMORY] ?: "BALANCED") }.getOrDefault(MemoryMode.BALANCED)
        )
    }

    suspend fun save(c: WallpaperConfig) {
        context.configStore.edit { p ->
            if (c.imageUri == null) p.remove(URI) else p[URI] = c.imageUri
            p[LS]=c.landscape.scale; p[LX]=c.landscape.normalizedOffsetX; p[LY]=c.landscape.normalizedOffsetY
            p[PS]=c.portrait.scale; p[PX]=c.portrait.normalizedOffsetX; p[PY]=c.portrait.normalizedOffsetY
            p[BG]=c.backgroundMode.name; p[COLOR]=c.backgroundColor; p[PARALLAX]=c.parallaxEnabled; p[MEMORY]=c.memoryMode.name
        }
        context.sendBroadcast(Intent(ACTION_CONFIG_CHANGED).setPackage(context.packageName))
    }
}
