package com.example.jedain

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit

@Immutable
data class TargetSettingsState(
    val dailyTargetHours: Int = 3,
    val sessionDurationMinutes: Int = 10,
    val reminderOnSessionEnd: Boolean = true,
)

class TargetSettingsPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSettings(): TargetSettingsState {
        return TargetSettingsState(
            dailyTargetHours = prefs.getInt(KEY_DAILY_TARGET_HOURS, DEFAULT_DAILY_TARGET_HOURS),
            sessionDurationMinutes = prefs.getInt(KEY_SESSION_DURATION_MINUTES, DEFAULT_SESSION_MINUTES),
            reminderOnSessionEnd = prefs.getBoolean(KEY_REMINDER_ON_SESSION_END, DEFAULT_REMINDER_ENABLED),
        )
    }

    fun saveSettings(settings: TargetSettingsState) {
        prefs.edit {
            putInt(KEY_DAILY_TARGET_HOURS, settings.dailyTargetHours)
            putInt(KEY_SESSION_DURATION_MINUTES, settings.sessionDurationMinutes)
            putBoolean(KEY_REMINDER_ON_SESSION_END, settings.reminderOnSessionEnd)
        }
    }

    companion object {
        private const val PREFS_NAME = "jedain_target_settings"
        private const val KEY_DAILY_TARGET_HOURS = "daily_target_hours"
        private const val KEY_SESSION_DURATION_MINUTES = "session_duration_minutes"
        private const val KEY_REMINDER_ON_SESSION_END = "reminder_on_session_end"

        const val DEFAULT_DAILY_TARGET_HOURS = 3
        const val DEFAULT_SESSION_MINUTES = 10
        const val DEFAULT_REMINDER_ENABLED = true
    }
}
