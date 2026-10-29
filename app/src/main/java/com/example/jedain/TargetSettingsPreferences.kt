package com.example.jedain

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit

val PresetDailyTargetMinutes = listOf(30, 60, 120, 180)

fun validateDailyTargetMinutes(minutes: Int): Int {
    val clamped = minutes.coerceIn(
        TargetSettingsPreferences.MIN_DAILY_TARGET_MINUTES,
        TargetSettingsPreferences.MAX_DAILY_TARGET_MINUTES,
    )
    val snapped = ((clamped + 7) / TargetSettingsPreferences.DAILY_TARGET_STEP_MINUTES) *
        TargetSettingsPreferences.DAILY_TARGET_STEP_MINUTES
    return snapped.coerceIn(
        TargetSettingsPreferences.MIN_DAILY_TARGET_MINUTES,
        TargetSettingsPreferences.MAX_DAILY_TARGET_MINUTES,
    )
}

fun formatDailyTargetDuration(totalMinutes: Int): String {
    val validated = validateDailyTargetMinutes(totalMinutes)
    val hours = validated / 60
    val minutes = validated % 60
    return when {
        hours == 0 -> "$minutes menit"
        minutes == 0 -> "$hours jam"
        else -> "$hours jam $minutes menit"
    }
}

@Immutable
data class TargetSettingsState(
    val dailyTargetMinutes: Int = TargetSettingsPreferences.DEFAULT_DAILY_TARGET_MINUTES,
    val isCustomDailyTarget: Boolean = dailyTargetMinutes !in PresetDailyTargetMinutes,
    val customDailyTargetMinutes: Int = if (dailyTargetMinutes !in PresetDailyTargetMinutes) {
        validateDailyTargetMinutes(dailyTargetMinutes)
    } else {
        TargetSettingsPreferences.DEFAULT_CUSTOM_DAILY_TARGET_MINUTES
    },
    val sessionDurationMinutes: Int = TargetSettingsPreferences.DEFAULT_SESSION_MINUTES,
    val reminderOnSessionEnd: Boolean = TargetSettingsPreferences.DEFAULT_REMINDER_ENABLED,
) {
    val validatedDailyTargetMinutes: Int
        get() = validateDailyTargetMinutes(dailyTargetMinutes)

    val validatedCustomDailyTargetMinutes: Int
        get() = validateDailyTargetMinutes(customDailyTargetMinutes)

    val dailyTargetHours: Int
        get() = (validatedDailyTargetMinutes / 60).coerceIn(1, 4)
}

class TargetSettingsPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSettings(): TargetSettingsState {
        val rawMinutes = if (prefs.contains(KEY_DAILY_TARGET_MINUTES)) {
            prefs.getInt(KEY_DAILY_TARGET_MINUTES, DEFAULT_DAILY_TARGET_MINUTES)
        } else {
            val legacyHours = prefs.getInt(KEY_DAILY_TARGET_HOURS, DEFAULT_DAILY_TARGET_HOURS)
            legacyHours * 60
        }
        val validatedMinutes = validateDailyTargetMinutes(rawMinutes)

        val isCustom = if (prefs.contains(KEY_IS_CUSTOM_DAILY_TARGET)) {
            prefs.getBoolean(KEY_IS_CUSTOM_DAILY_TARGET, false)
        } else {
            validatedMinutes !in PresetDailyTargetMinutes
        }

        val rawCustomMinutes = prefs.getInt(
            KEY_CUSTOM_DAILY_TARGET_MINUTES,
            if (isCustom) validatedMinutes else DEFAULT_CUSTOM_DAILY_TARGET_MINUTES,
        )
        val validatedCustomMinutes = if (isCustom) {
            validatedMinutes
        } else {
            validateDailyTargetMinutes(rawCustomMinutes)
        }

        return TargetSettingsState(
            dailyTargetMinutes = validatedMinutes,
            isCustomDailyTarget = isCustom,
            customDailyTargetMinutes = validatedCustomMinutes,
            sessionDurationMinutes = prefs.getInt(KEY_SESSION_DURATION_MINUTES, DEFAULT_SESSION_MINUTES),
            reminderOnSessionEnd = prefs.getBoolean(KEY_REMINDER_ON_SESSION_END, DEFAULT_REMINDER_ENABLED),
        )
    }

    fun saveSettings(settings: TargetSettingsState) {
        val validatedMinutes = settings.validatedDailyTargetMinutes
        val validatedCustomMinutes = if (settings.isCustomDailyTarget) {
            validatedMinutes
        } else {
            settings.validatedCustomDailyTargetMinutes
        }
        prefs.edit {
            putInt(KEY_DAILY_TARGET_MINUTES, validatedMinutes)
            putBoolean(KEY_IS_CUSTOM_DAILY_TARGET, settings.isCustomDailyTarget)
            putInt(KEY_CUSTOM_DAILY_TARGET_MINUTES, validatedCustomMinutes)
            putInt(KEY_DAILY_TARGET_HOURS, settings.dailyTargetHours)
            putInt(KEY_SESSION_DURATION_MINUTES, settings.sessionDurationMinutes)
            putBoolean(KEY_REMINDER_ON_SESSION_END, settings.reminderOnSessionEnd)
        }
    }

    companion object {
        private const val PREFS_NAME = "jedain_target_settings"
        private const val KEY_DAILY_TARGET_HOURS = "daily_target_hours"
        private const val KEY_DAILY_TARGET_MINUTES = "daily_target_minutes"
        private const val KEY_IS_CUSTOM_DAILY_TARGET = "is_custom_daily_target"
        private const val KEY_CUSTOM_DAILY_TARGET_MINUTES = "custom_daily_target_minutes"
        private const val KEY_SESSION_DURATION_MINUTES = "session_duration_minutes"
        private const val KEY_REMINDER_ON_SESSION_END = "reminder_on_session_end"

        const val MIN_DAILY_TARGET_MINUTES = 15
        const val MAX_DAILY_TARGET_MINUTES = 240
        const val DAILY_TARGET_STEP_MINUTES = 15
        const val DEFAULT_DAILY_TARGET_HOURS = 3
        const val DEFAULT_DAILY_TARGET_MINUTES = 180
        const val DEFAULT_CUSTOM_DAILY_TARGET_MINUTES = 90
        const val DEFAULT_SESSION_MINUTES = 10
        const val DEFAULT_REMINDER_ENABLED = true
    }
}
