package com.nexora.app

import android.content.Context

/** User-adjustable touch layout preferences, stored separately from game data. */
data class TouchControlSettings(
    val dpadSizeDp: Int = 184,
    val actionButtonSizeDp: Int = 70,
    val shoulderButtonSizeDp: Int = 72,
    val opacityPercent: Int = 100,
    val dpadOffsetXDp: Int = 0,
    val dpadOffsetYDp: Int = 0,
    val actionOffsetXDp: Int = 0,
    val actionOffsetYDp: Int = 0
)

object TouchControlSettingsStore {
    private const val PREFS_NAME = "nexora_touch_controls"

    fun load(context: Context): TouchControlSettings {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return TouchControlSettings(
            dpadSizeDp = p.getInt("dpad_size", 184).coerceIn(144, 200),
            actionButtonSizeDp = p.getInt("action_size", 70).coerceIn(48, 92),
            shoulderButtonSizeDp = p.getInt("shoulder_size", 72).coerceIn(48, 92),
            opacityPercent = p.getInt("opacity", 100).coerceIn(35, 100),
            dpadOffsetXDp = p.getInt("dpad_x", 0).coerceIn(-36, 36),
            dpadOffsetYDp = p.getInt("dpad_y", 0).coerceIn(-36, 36),
            actionOffsetXDp = p.getInt("action_x", 0).coerceIn(-36, 36),
            actionOffsetYDp = p.getInt("action_y", 0).coerceIn(-36, 36)
        )
    }

    fun save(context: Context, s: TouchControlSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt("dpad_size", s.dpadSizeDp.coerceIn(144, 200))
            .putInt("action_size", s.actionButtonSizeDp.coerceIn(48, 92))
            .putInt("shoulder_size", s.shoulderButtonSizeDp.coerceIn(48, 92))
            .putInt("opacity", s.opacityPercent.coerceIn(35, 100))
            .putInt("dpad_x", s.dpadOffsetXDp.coerceIn(-36, 36))
            .putInt("dpad_y", s.dpadOffsetYDp.coerceIn(-36, 36))
            .putInt("action_x", s.actionOffsetXDp.coerceIn(-36, 36))
            .putInt("action_y", s.actionOffsetYDp.coerceIn(-36, 36))
            .apply()
    }
}
