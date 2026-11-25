package com.nexora.app

import android.content.Context

/** Touch layout preferences are isolated from ROM metadata and all game save data. */
data class TouchControlSettings(
    val dpadSizeDp: Int = 184,
    val actionButtonSizeDp: Int = 70,
    val shoulderButtonSizeDp: Int = 72,
    val systemButtonSizeDp: Int = 48,
    val opacityPercent: Int = 100,
    val gameScreenScalePercent: Int = 100,
    val dpadOffsetXDp: Int = 0,
    val dpadOffsetYDp: Int = 0,
    val actionOffsetXDp: Int = 0,
    val actionOffsetYDp: Int = 0,
    val shoulderOffsetXDp: Int = 0,
    val shoulderOffsetYDp: Int = 0,
    val systemOffsetXDp: Int = 0,
    val systemOffsetYDp: Int = 0,
    val aSizeDp: Int = 70, val aOffsetXDp: Int = 0, val aOffsetYDp: Int = 0,
    val bSizeDp: Int = 70, val bOffsetXDp: Int = 0, val bOffsetYDp: Int = 0,
    val lSizeDp: Int = 72, val lOffsetXDp: Int = 0, val lOffsetYDp: Int = 0,
    val rSizeDp: Int = 72, val rOffsetXDp: Int = 0, val rOffsetYDp: Int = 0,
    val menuSizeDp: Int = 48, val menuOffsetXDp: Int = 0, val menuOffsetYDp: Int = 0,
    val selectSizeDp: Int = 48, val selectOffsetXDp: Int = 0, val selectOffsetYDp: Int = 0,
    val startSizeDp: Int = 48, val startOffsetXDp: Int = 0, val startOffsetYDp: Int = 0,
    val defaultSpeed: Float = 1.0f,
    val audioVolumePercent: Int = 100
)

object TouchControlSettingsStore {
    private const val PREFS_NAME = "nexora_touch_controls"

    fun load(context: Context, isLandscape: Boolean): TouchControlSettings {
        val legacy = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val profileName = if (isLandscape) PREFS_NAME + "_landscape" else PREFS_NAME + "_portrait"
        val p = context.getSharedPreferences(profileName, Context.MODE_PRIVATE)
        if (!p.contains("dpad_size")) {
            // Copy the previous shared layout into each orientation profile once.
            val editor = p.edit()
            legacy.all.forEach { (key, value) ->
                when (value) {
                    is Int -> editor.putInt(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                }
            }
            editor.apply()
        }
        return TouchControlSettings(
            dpadSizeDp = p.getInt("dpad_size", 184).coerceIn(100, 240),
            actionButtonSizeDp = p.getInt("action_size", 70).coerceIn(36, 120),
            shoulderButtonSizeDp = p.getInt("shoulder_size", 72).coerceIn(36, 120),
            systemButtonSizeDp = p.getInt("system_size", 48).coerceIn(28, 96),
            opacityPercent = p.getInt("opacity", 100).coerceIn(20, 100),
            gameScreenScalePercent = p.getInt("game_screen_scale", 100).coerceIn(60, 120),
            dpadOffsetXDp = p.getInt("dpad_x", 0).coerceIn(-180, 180),
            dpadOffsetYDp = p.getInt("dpad_y", 0).coerceIn(-180, 180),
            actionOffsetXDp = p.getInt("action_x", 0).coerceIn(-180, 180),
            actionOffsetYDp = p.getInt("action_y", 0).coerceIn(-180, 180),
            shoulderOffsetXDp = p.getInt("shoulder_x", 0).coerceIn(-180, 180),
            shoulderOffsetYDp = p.getInt("shoulder_y", 0).coerceIn(-180, 180),
            systemOffsetXDp = p.getInt("system_x", 0).coerceIn(-180, 180),
            systemOffsetYDp = p.getInt("system_y", 0).coerceIn(-180, 180),
            aSizeDp = p.getInt("a_size", p.getInt("action_size", 70)).coerceIn(36, 120), aOffsetXDp = p.getInt("a_x", p.getInt("action_x", 0)).coerceIn(-180, 180), aOffsetYDp = p.getInt("a_y", p.getInt("action_y", 0)).coerceIn(-180, 180),
            bSizeDp = p.getInt("b_size", p.getInt("action_size", 70)).coerceIn(36, 120), bOffsetXDp = p.getInt("b_x", p.getInt("action_x", 0)).coerceIn(-180, 180), bOffsetYDp = p.getInt("b_y", p.getInt("action_y", 0)).coerceIn(-180, 180),
            lSizeDp = p.getInt("l_size", p.getInt("shoulder_size", 72)).coerceIn(36, 120), lOffsetXDp = p.getInt("l_x", p.getInt("shoulder_x", 0)).coerceIn(-180, 180), lOffsetYDp = p.getInt("l_y", p.getInt("shoulder_y", 0)).coerceIn(-180, 180),
            rSizeDp = p.getInt("r_size", p.getInt("shoulder_size", 72)).coerceIn(36, 120), rOffsetXDp = p.getInt("r_x", p.getInt("shoulder_x", 0)).coerceIn(-180, 180), rOffsetYDp = p.getInt("r_y", p.getInt("shoulder_y", 0)).coerceIn(-180, 180),
            menuSizeDp = p.getInt("menu_size", p.getInt("system_size", 48)).coerceIn(28, 96), menuOffsetXDp = p.getInt("menu_x", p.getInt("system_x", 0)).coerceIn(-180, 180), menuOffsetYDp = p.getInt("menu_y", p.getInt("system_y", 0)).coerceIn(-180, 180),
            selectSizeDp = p.getInt("select_size", p.getInt("system_size", 48)).coerceIn(28, 96), selectOffsetXDp = p.getInt("select_x", p.getInt("system_x", 0)).coerceIn(-180, 180), selectOffsetYDp = p.getInt("select_y", p.getInt("system_y", 0)).coerceIn(-180, 180),
            startSizeDp = p.getInt("start_size", p.getInt("system_size", 48)).coerceIn(28, 96), startOffsetXDp = p.getInt("start_x", p.getInt("system_x", 0)).coerceIn(-180, 180), startOffsetYDp = p.getInt("start_y", p.getInt("system_y", 0)).coerceIn(-180, 180),
            defaultSpeed = legacy.getFloat("default_speed", 1.0f).takeIf { it in listOf(1.0f, 1.5f, 2.0f, 3.0f) } ?: 1.0f,
            audioVolumePercent = legacy.getInt("audio_volume", 100).coerceIn(0, 100)
        )
    }

    fun save(context: Context, s: TouchControlSettings, isLandscape: Boolean) {
        val profileName = if (isLandscape) PREFS_NAME + "_landscape" else PREFS_NAME + "_portrait"
        context.getSharedPreferences(profileName, Context.MODE_PRIVATE).edit()
            .putInt("dpad_size", s.dpadSizeDp.coerceIn(100, 240))
            .putInt("action_size", s.actionButtonSizeDp.coerceIn(36, 120))
            .putInt("shoulder_size", s.shoulderButtonSizeDp.coerceIn(36, 120))
            .putInt("system_size", s.systemButtonSizeDp.coerceIn(28, 96))
            .putInt("opacity", s.opacityPercent.coerceIn(20, 100))
            .putInt("game_screen_scale", s.gameScreenScalePercent.coerceIn(60, 120))
            .putInt("dpad_x", s.dpadOffsetXDp.coerceIn(-180, 180))
            .putInt("dpad_y", s.dpadOffsetYDp.coerceIn(-180, 180))
            .putInt("action_x", s.actionOffsetXDp.coerceIn(-180, 180))
            .putInt("action_y", s.actionOffsetYDp.coerceIn(-180, 180))
            .putInt("shoulder_x", s.shoulderOffsetXDp.coerceIn(-180, 180))
            .putInt("shoulder_y", s.shoulderOffsetYDp.coerceIn(-180, 180))
            .putInt("system_x", s.systemOffsetXDp.coerceIn(-180, 180))
            .putInt("system_y", s.systemOffsetYDp.coerceIn(-180, 180))
            .putInt("a_size", s.aSizeDp.coerceIn(36, 120)).putInt("a_x", s.aOffsetXDp.coerceIn(-180, 180)).putInt("a_y", s.aOffsetYDp.coerceIn(-180, 180))
            .putInt("b_size", s.bSizeDp.coerceIn(36, 120)).putInt("b_x", s.bOffsetXDp.coerceIn(-180, 180)).putInt("b_y", s.bOffsetYDp.coerceIn(-180, 180))
            .putInt("l_size", s.lSizeDp.coerceIn(36, 120)).putInt("l_x", s.lOffsetXDp.coerceIn(-180, 180)).putInt("l_y", s.lOffsetYDp.coerceIn(-180, 180))
            .putInt("r_size", s.rSizeDp.coerceIn(36, 120)).putInt("r_x", s.rOffsetXDp.coerceIn(-180, 180)).putInt("r_y", s.rOffsetYDp.coerceIn(-180, 180))
            .putInt("menu_size", s.menuSizeDp.coerceIn(28, 96)).putInt("menu_x", s.menuOffsetXDp.coerceIn(-180, 180)).putInt("menu_y", s.menuOffsetYDp.coerceIn(-180, 180))
            .putInt("select_size", s.selectSizeDp.coerceIn(28, 96)).putInt("select_x", s.selectOffsetXDp.coerceIn(-180, 180)).putInt("select_y", s.selectOffsetYDp.coerceIn(-180, 180))
            .putInt("start_size", s.startSizeDp.coerceIn(28, 96)).putInt("start_x", s.startOffsetXDp.coerceIn(-180, 180)).putInt("start_y", s.startOffsetYDp.coerceIn(-180, 180))
            .apply()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat("default_speed", s.defaultSpeed.takeIf { it in listOf(1.0f, 1.5f, 2.0f, 3.0f) } ?: 1.0f)
            .putInt("audio_volume", s.audioVolumePercent.coerceIn(0, 100))
            .apply()
    }
}
