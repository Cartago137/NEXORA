package com.nexora.app

import android.content.Context
import android.net.Uri
import java.io.File

data class BackgroundSettings(
    val mode: Int = 0, // 0 = none, 1 = full screen, 2 = side areas
    val separateOrientations: Boolean = false,
    val portraitImagePath: String = "",
    val landscapeImagePath: String = "",
    val cropScale: Float = 1f,
    val cropOffsetX: Float = 0f,
    val cropOffsetY: Float = 0f
) {
    fun imagePath(isLandscape: Boolean): String =
        if (separateOrientations) {
            if (isLandscape) landscapeImagePath else portraitImagePath
        } else {
            portraitImagePath.ifBlank { landscapeImagePath }
        }
}

object BackgroundSettingsStore {
    private const val PREFS = "nexora_background_settings"

    fun load(context: Context): BackgroundSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return BackgroundSettings(
            mode = p.getInt("mode", 0).coerceIn(0, 2),
            separateOrientations = p.getBoolean("separate_orientations", false),
            portraitImagePath = p.getString("portrait_image", "") ?: "",
            landscapeImagePath = p.getString("landscape_image", "") ?: "",
            cropScale = p.getFloat("crop_scale", 1f).coerceIn(1f, 3f),
            cropOffsetX = p.getFloat("crop_offset_x", 0f).coerceIn(-1f, 1f),
            cropOffsetY = p.getFloat("crop_offset_y", 0f).coerceIn(-1f, 1f)
        )
    }

    fun save(context: Context, settings: BackgroundSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("mode", settings.mode.coerceIn(0, 2))
            .putBoolean("separate_orientations", settings.separateOrientations)
            .putString("portrait_image", settings.portraitImagePath)
            .putString("landscape_image", settings.landscapeImagePath)
            .putFloat("crop_scale", settings.cropScale.coerceIn(1f, 3f))
            .putFloat("crop_offset_x", settings.cropOffsetX.coerceIn(-1f, 1f))
            .putFloat("crop_offset_y", settings.cropOffsetY.coerceIn(-1f, 1f))
            .apply()
    }

    fun copyImageIntoPrivateStorage(context: Context, uri: Uri, isLandscape: Boolean): String? {
        return runCatching {
            val directory = File(context.filesDir, "backgrounds").apply { mkdirs() }
            val target = File(directory, if (isLandscape) "landscape-background" else "portrait-background")
            val temporary = File(directory, target.name + ".tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (temporary.length() <= 0L) {
                temporary.delete()
                return null
            }
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            target.absolutePath
        }.getOrNull()
    }

    fun removeImage(context: Context, path: String) {
        if (path.isNotBlank()) runCatching { File(path).delete() }
    }
}
