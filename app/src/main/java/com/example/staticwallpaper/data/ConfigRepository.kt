package com.example.staticwallpaper.data

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.staticwallpaper.render.TransformCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.configStore by preferencesDataStore("wallpaper_config")

class ConfigRepository(private val context: Context) {
    companion object {
        const val ACTION_CONFIG_CHANGED = "com.example.staticwallpaper.CONFIG_CHANGED"
        private val SCHEMA = intPreferencesKey("schema")
        private val D_URI = stringPreferencesKey("desktop_uri"); private val K_URI = stringPreferencesKey("lock_uri")
        private val K_PORTRAIT_URI = stringPreferencesKey("lock_portrait_uri")
        private fun f(name: String) = floatPreferencesKey(name)
        private fun b(name: String) = booleanPreferencesKey(name)
        private val DLS=f("dls");private val DLCX=f("dlcx");private val DLCY=f("dlcy");private val DLAB=b("dlab")
        private val DPS=f("dps");private val DPCX=f("dpcx");private val DPCY=f("dpcy");private val DPAB=b("dpab")
        private val KLS=f("v2_kls");private val KLCX=f("klcx");private val KLCY=f("klcy");private val KLAB=b("klab")
        private val KPS=f("v2_kps");private val KPCX=f("kpcx");private val KPCY=f("kpcy");private val KPAB=b("kpab")
        private val BG=stringPreferencesKey("bg");private val COLOR=longPreferencesKey("color")
        private val PARALLAX=booleanPreferencesKey("parallax");private val MEMORY=stringPreferencesKey("memory")
        private val AUTO_LOCK=booleanPreferencesKey("auto_lock_enabled")
        private val LEGACY_TARGET=stringPreferencesKey("legacy_dynamic_target")

        // Version-1 keys retained only for one-time migration.
        private val OLD_URI=stringPreferencesKey("uri")
        private val LS=f("ls");private val LX=f("lx");private val LY=f("ly")
        private val PS=f("ps");private val PX=f("px");private val PY=f("py")
        private val OLD_KLS=f("kls");private val OLD_KLX=f("klx");private val OLD_KLY=f("kly")
        private val OLD_KPS=f("kps");private val OLD_KPX=f("kpx");private val OLD_KPY=f("kpy")
    }

    val config: Flow<WallpaperConfig> = context.configStore.data.map { p ->
        val background=runCatching { BackgroundMode.valueOf(p[BG] ?: "BLACK") }.getOrDefault(BackgroundMode.BLACK)
        val memory=runCatching { MemoryMode.valueOf(p[MEMORY] ?: "BALANCED") }.getOrDefault(MemoryMode.BALANCED)
        if ((p[SCHEMA] ?: 1) >= 2) {
            WallpaperConfig(
                desktop=WallpaperSourceConfig(p[D_URI],readTransform(p,DLS,DLCX,DLCY,DLAB),readTransform(p,DPS,DPCX,DPCY,DPAB)),
                lock=WallpaperSourceConfig(
                    imageUri=p[K_URI],portraitImageUri=p[K_PORTRAIT_URI],
                    landscape=readTransform(p,KLS,KLCX,KLCY,KLAB),portrait=readTransform(p,KPS,KPCX,KPCY,KPAB)
                ),
                backgroundMode=background,backgroundColor=p[COLOR] ?: 0xFF000000,
                parallaxEnabled=p[PARALLAX] ?: false,memoryMode=memory,
                autoLockEnabled=p[AUTO_LOCK] ?: false,
                legacyDynamicTarget=runCatching{WallpaperTarget.valueOf(p[LEGACY_TARGET]?:"DESKTOP")}.getOrDefault(WallpaperTarget.DESKTOP)
            )
        } else {
            val oldUri=p[OLD_URI]
            val legacy=if(oldUri==null) null else LegacyConfig(
                LegacyTransform(p[LS]?:1f,p[LX]?:0f,p[LY]?:0f),LegacyTransform(p[PS]?:1f,p[PX]?:0f,p[PY]?:0f),
                LegacyTransform(p[OLD_KLS]?:p[LS]?:1f,p[OLD_KLX]?:p[LX]?:0f,p[OLD_KLY]?:p[LY]?:0f),
                LegacyTransform(p[OLD_KPS]?:p[PS]?:1f,p[OLD_KPX]?:p[PX]?:0f,p[OLD_KPY]?:p[PY]?:0f)
            )
            WallpaperConfig(desktop=WallpaperSourceConfig(oldUri),lock=WallpaperSourceConfig(oldUri),backgroundMode=background,backgroundColor=p[COLOR]?:0xFF000000,parallaxEnabled=p[PARALLAX]?:false,memoryMode=memory,autoLockEnabled=p[AUTO_LOCK]?:false,legacy=legacy)
        }
    }

    private fun readTransform(p: Preferences,s: Preferences.Key<Float>,x: Preferences.Key<Float>,y: Preferences.Key<Float>,ab: Preferences.Key<Boolean>) =
        CompositionTransform(p[s]?:1f,p[x]?:.5f,p[y]?:.5f,p[ab]?:false)

    suspend fun save(c: WallpaperConfig) {
        context.configStore.edit { p ->
            p[SCHEMA]=3
            putNullable(p,D_URI,c.desktop.imageUri);putNullable(p,K_URI,c.lock.imageUri);putNullable(p,K_PORTRAIT_URI,c.lock.portraitImageUri)
            writeTransform(p,DLS,DLCX,DLCY,DLAB,c.desktop.landscape);writeTransform(p,DPS,DPCX,DPCY,DPAB,c.desktop.portrait)
            writeTransform(p,KLS,KLCX,KLCY,KLAB,c.lock.landscape);writeTransform(p,KPS,KPCX,KPCY,KPAB,c.lock.portrait)
            p[BG]=c.backgroundMode.name;p[COLOR]=c.backgroundColor;p[PARALLAX]=c.parallaxEnabled;p[MEMORY]=c.memoryMode.name;p[AUTO_LOCK]=c.autoLockEnabled;p[LEGACY_TARGET]=c.legacyDynamicTarget.name
        }
        context.sendBroadcast(Intent(ACTION_CONFIG_CHANGED).setPackage(context.packageName))
    }

    suspend fun migrateLegacy(c: WallpaperConfig,imageWidth: Int,imageHeight: Int,profile: DisplayProfile): WallpaperConfig {
        val old=c.legacy ?: return c
        val l=profile.landscape;val p=profile.portrait
        val migrated=c.copy(
            desktop=c.desktop.copy(
                landscape=TransformCalculator.migrateLegacy(imageWidth.toFloat(),imageHeight.toFloat(),l.width.toFloat(),l.height.toFloat(),old.landscape),
                portrait=TransformCalculator.migrateLegacy(imageWidth.toFloat(),imageHeight.toFloat(),p.width.toFloat(),p.height.toFloat(),old.portrait)),
            lock=c.lock.copy(
                landscape=TransformCalculator.migrateLegacy(imageWidth.toFloat(),imageHeight.toFloat(),l.width.toFloat(),l.height.toFloat(),old.lockLandscape),
                portrait=TransformCalculator.migrateLegacy(imageWidth.toFloat(),imageHeight.toFloat(),p.width.toFloat(),p.height.toFloat(),old.lockPortrait)),
            legacy=null
        )
        save(migrated)
        return migrated
    }

    private fun putNullable(p: MutablePreferences,key: Preferences.Key<String>,value: String?) { if(value==null)p.remove(key) else p[key]=value }
    private fun writeTransform(p: MutablePreferences,s: Preferences.Key<Float>,x: Preferences.Key<Float>,y: Preferences.Key<Float>,ab: Preferences.Key<Boolean>,t: CompositionTransform) {
        p[s]=t.zoom;p[x]=t.centerX;p[y]=t.centerY;p[ab]=t.allowBackground
    }
}
