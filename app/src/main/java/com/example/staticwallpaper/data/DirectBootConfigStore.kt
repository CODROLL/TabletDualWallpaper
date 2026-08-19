package com.example.staticwallpaper.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * A minimal device-protected mirror for the experimental lock wallpaper.
 *
 * Persisted SAF grants and the normal DataStore can be unavailable before the first unlock.
 * Keeping local source copies and only the lock rendering fields here lets a direct-boot-aware
 * WallpaperService render without relying on credential-protected storage.
 */
object DirectBootConfigStore {
    private const val PREFS = "experimental_lock_direct_boot"
    private const val READY = "ready"
    private const val LANDSCAPE_SOURCE = "landscape_source"
    private const val PORTRAIT_SOURCE = "portrait_source"
    private const val LANDSCAPE_FILE = "experimental-lock-landscape.source"
    private const val PORTRAIT_FILE = "experimental-lock-portrait.source"

    suspend fun sync(context: Context, config: WallpaperConfig) = withContext(Dispatchers.IO) {
        val protectedContext = context.createDeviceProtectedStorageContext()
        val preferences = protectedContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val landscapeUri = config.lock.imageUri(true)
        val portraitUri = config.lock.imageUri(false)
        val landscapeCache = cacheSource(
            context,
            protectedContext,
            landscapeUri,
            preferences.getString(LANDSCAPE_SOURCE, null),
            LANDSCAPE_FILE
        )
        val portraitCache = if (portraitUri == landscapeUri) {
            landscapeCache
        } else {
            cacheSource(
                context,
                protectedContext,
                portraitUri,
                preferences.getString(PORTRAIT_SOURCE, null),
                PORTRAIT_FILE
            )
        }

        preferences.edit()
            .putBoolean(READY, true)
            .putNullable(LANDSCAPE_SOURCE, landscapeUri)
            .putNullable(PORTRAIT_SOURCE, portraitUri)
            .putNullable("landscape_cache", landscapeCache)
            .putNullable("portrait_cache", portraitCache)
            .putTransform("landscape", config.lock.landscape)
            .putTransform("portrait", config.lock.portrait)
            .putString("background_mode", config.backgroundMode.name)
            .putLong("background_color", config.backgroundColor)
            .putString("memory_mode", config.memoryMode.name)
            .commit()
    }

    fun read(context: Context): WallpaperConfig? {
        val protectedContext = context.createDeviceProtectedStorageContext()
        val preferences = protectedContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(READY, false)) return null
        val landscapeCache = preferences.getString("landscape_cache", null)
            ?.takeIf { File(Uri.parse(it).path.orEmpty()).isFile }
        val portraitCache = preferences.getString("portrait_cache", null)
            ?.takeIf { File(Uri.parse(it).path.orEmpty()).isFile }
        return WallpaperConfig(
            lock = WallpaperSourceConfig(
                imageUri = landscapeCache,
                portraitImageUri = portraitCache,
                landscape = preferences.readTransform("landscape"),
                portrait = preferences.readTransform("portrait")
            ),
            backgroundMode = runCatching {
                BackgroundMode.valueOf(preferences.getString("background_mode", null) ?: "BLACK")
            }.getOrDefault(BackgroundMode.BLACK),
            backgroundColor = preferences.getLong("background_color", 0xFF000000),
            memoryMode = runCatching {
                MemoryMode.valueOf(preferences.getString("memory_mode", null) ?: "BALANCED")
            }.getOrDefault(MemoryMode.BALANCED)
        )
    }

    private fun cacheSource(
        context: Context,
        protectedContext: Context,
        sourceUri: String?,
        cachedSourceUri: String?,
        fileName: String
    ): String? {
        val destination = File(protectedContext.filesDir, fileName)
        if (sourceUri == null) {
            destination.delete()
            return null
        }
        if (sourceUri == cachedSourceUri && destination.isFile && destination.length() > 0L) {
            return Uri.fromFile(destination).toString()
        }

        val temporary = File(protectedContext.cacheDir, "$fileName.tmp")
        try {
            val input = requireNotNull(context.contentResolver.openInputStream(Uri.parse(sourceUri))) {
                "无法读取实验锁屏图片"
            }
            input.use { source ->
                FileOutputStream(temporary).use { output -> source.copyTo(output) }
            }
            check(temporary.length() > 0L) { "实验锁屏图片为空" }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            return Uri.fromFile(destination).toString()
        } finally {
            temporary.delete()
        }
    }

    private fun android.content.SharedPreferences.Editor.putNullable(key: String, value: String?) =
        if (value == null) remove(key) else putString(key, value)

    private fun android.content.SharedPreferences.Editor.putTransform(
        prefix: String,
        transform: CompositionTransform
    ) = putFloat("${prefix}_zoom", transform.zoom)
        .putFloat("${prefix}_center_x", transform.centerX)
        .putFloat("${prefix}_center_y", transform.centerY)
        .putBoolean("${prefix}_background", transform.allowBackground)

    private fun android.content.SharedPreferences.readTransform(prefix: String) = CompositionTransform(
        zoom = getFloat("${prefix}_zoom", 1f),
        centerX = getFloat("${prefix}_center_x", .5f),
        centerY = getFloat("${prefix}_center_y", .5f),
        allowBackground = getBoolean("${prefix}_background", false)
    )
}
