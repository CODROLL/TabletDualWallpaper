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
        private val KLS = floatPreferencesKey("kls"); private val KLX = floatPreferencesKey("klx"); private val KLY = floatPreferencesKey("kly")
        private val KPS = floatPreferencesKey("kps"); private val KPX = floatPreferencesKey("kpx"); private val KPY = floatPreferencesKey("kpy")
        private val BG = stringPreferencesKey("bg"); private val COLOR = longPreferencesKey("color")
        private val PARALLAX = booleanPreferencesKey("parallax"); private val MEMORY = stringPreferencesKey("memory")
    }

    val config: Flow<WallpaperConfig> = context.configStore.data.map { p ->
        val landscape=OrientationTransform(p[LS] ?: 1f, p[LX] ?: 0f, p[LY] ?: 0f)
        val portrait=OrientationTransform(p[PS] ?: 1f, p[PX] ?: 0f, p[PY] ?: 0f)
        WallpaperConfig(
            imageUri=p[URI], landscape=landscape, portrait=portrait,
            lockLandscape=OrientationTransform(p[KLS] ?: landscape.scale,p[KLX] ?: landscape.normalizedOffsetX,p[KLY] ?: landscape.normalizedOffsetY),
            lockPortrait=OrientationTransform(p[KPS] ?: portrait.scale,p[KPX] ?: portrait.normalizedOffsetX,p[KPY] ?: portrait.normalizedOffsetY),
            backgroundMode=runCatching { BackgroundMode.valueOf(p[BG] ?: "BLACK") }.getOrDefault(BackgroundMode.BLACK),
            backgroundColor=p[COLOR] ?: 0xFF000000, parallaxEnabled=p[PARALLAX] ?: false,
            memoryMode=runCatching { MemoryMode.valueOf(p[MEMORY] ?: "BALANCED") }.getOrDefault(MemoryMode.BALANCED)
        )
    }

    suspend fun save(c: WallpaperConfig) {
        context.configStore.edit { p ->
            if (c.imageUri == null) p.remove(URI) else p[URI] = c.imageUri
            p[LS]=c.landscape.scale; p[LX]=c.landscape.normalizedOffsetX; p[LY]=c.landscape.normalizedOffsetY
            p[PS]=c.portrait.scale; p[PX]=c.portrait.normalizedOffsetX; p[PY]=c.portrait.normalizedOffsetY
            p[KLS]=c.lockLandscape.scale; p[KLX]=c.lockLandscape.normalizedOffsetX; p[KLY]=c.lockLandscape.normalizedOffsetY
            p[KPS]=c.lockPortrait.scale; p[KPX]=c.lockPortrait.normalizedOffsetX; p[KPY]=c.lockPortrait.normalizedOffsetY
            p[BG]=c.backgroundMode.name; p[COLOR]=c.backgroundColor; p[PARALLAX]=c.parallaxEnabled; p[MEMORY]=c.memoryMode.name
        }
        context.sendBroadcast(Intent(ACTION_CONFIG_CHANGED).setPackage(context.packageName))
    }
}
