package com.example.jedain

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import java.util.Locale

enum class BreakSessionState {
    ACTIVE,
    PAUSED,
    EXPIRED,
    RESTING,
}

object BreakSessionManager {

    private const val TAG = "JedaInBubble"
    const val PREFS_NAME = "jedain_break_session"

    private const val KEY_REMAINING_MILLIS = "remaining_millis"
    private const val KEY_TOTAL_SESSION_MILLIS = "total_session_millis"
    private const val KEY_STATE = "session_state"
    private const val KEY_REST_END_TIME = "rest_end_time"
    private const val KEY_CONSECUTIVE_EXTENSIONS = "consecutive_extensions"
    private const val KEY_CONFIGURED_SESSION_MINUTES = "configured_session_minutes"

    const val MAX_CONSECUTIVE_EXTENSIONS = 2
    const val REST_DURATION_MILLIS = 5L * 60L * 1000L
    const val DEFAULT_REST_DURATION_MINUTES = 5

    const val KEY_LAST_REST_DURATION_MINUTES = "last_rest_duration_minutes"
    const val KEY_LAST_REST_IS_CUSTOM = "last_rest_is_custom"
    const val KEY_TOTAL_REST_MILLIS = "total_rest_millis"

    const val KEY_SESSION_INTENT_CONFIRMED = "session_intent_confirmed"
    const val KEY_LAST_SESSION_INTENT = "last_session_intent"
    const val KEY_PENDING_INTENT_SNACKBAR = "pending_intent_snackbar"

    val DEFAULT_INTENT_CHOICES = listOf(
        "Cari hiburan sebentar",
        "Cek pesan atau kabar",
        "Cari informasi",
        "Hanya kebiasaan, tidak tahu mau apa",
    )

    val INTENT_REMINDER_MESSAGES = mapOf(
        "Cari hiburan sebentar" to "Cari hiburan secukupnya ya. Nikmati, lalu berhenti.",
        "Cek pesan atau kabar" to "Cek kabar saja. Selesai, langsung keluar.",
        "Cari informasi" to "Cari yang kamu butuhkan, lalu selesai.",
        "Hanya kebiasaan, tidak tahu mau apa" to "Kamu membuka ini tanpa tujuan. Perlu lanjut?",
        "Hanya kebiasaan, tidak tahu" to "Kamu membuka ini tanpa tujuan. Perlu lanjut?",
    )

    fun getIntentReminderMessage(intentChoice: String): String {
        return INTENT_REMINDER_MESSAGES[intentChoice]
            ?: "Gunakan waktu dengan bijak sesuai tujuanmu."
    }

    const val THREE_MINUTES_MILLIS = 3L * 60L * 1000L
    const val SIXTY_SECONDS_MILLIS = 60L * 1000L

    const val KEY_REMINDER_3MIN_SENT = "reminder_3min_sent"
    const val KEY_REMINDER_60SEC_SENT = "reminder_60sec_sent"
    const val KEY_REMINDER_COUNTDOWN_DISMISSED = "reminder_countdown_dismissed"

    // Konstanta legacy dipertahankan untuk kompatibilitas dan unit testing
    const val KEY_NOTIF_2MIN_SENT = "notif_2min_sent"
    const val KEY_NOTIF_30SEC_SENT = "notif_30sec_sent"

    const val TWO_MINUTES_MILLIS = 2L * 60L * 1000L
    const val THIRTY_SECONDS_MILLIS = 30L * 1000L

    private var lastMonotonicTime: Long = 0L

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getExtensionMinutes(sessionDurationMinutes: Int): Int {
        return when (sessionDurationMinutes) {
            1 -> 1
            2 -> 1
            5 -> 2
            10 -> 3
            15 -> 5
            20 -> 5
            30 -> 5
            else -> when {
                sessionDurationMinutes <= 2 -> 1
                sessionDurationMinutes <= 5 -> 2
                sessionDurationMinutes <= 10 -> 3
                else -> 5
            }
        }
    }

    fun getConfirmationTemplate(extensionMinutes: Int): String {
        return "Saya sadar dan memilih melanjutkan selama $extensionMinutes menit."
    }

    fun normalizeWords(text: String): List<String> {
        return text.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }
            .map { it.lowercase(Locale.ROOT) }
    }

    fun validateConfirmationInput(input: String, template: String): Boolean {
        val inputWords = normalizeWords(input)
        val templateWords = normalizeWords(template)
        return inputWords == templateWords
    }

    fun getSessionState(context: Context): BreakSessionState {
        val prefs = getPrefs(context)
        val stateStr = prefs.getString(KEY_STATE, BreakSessionState.PAUSED.name)
        val state = runCatching { BreakSessionState.valueOf(stateStr ?: BreakSessionState.PAUSED.name) }
            .getOrDefault(BreakSessionState.PAUSED)

        if (state == BreakSessionState.RESTING) {
            val restEndTime = prefs.getLong(KEY_REST_END_TIME, 0L)
            if (System.currentTimeMillis() >= restEndTime) {
                // Rest period has finished! Reset for new session.
                onRestCompleted(context)
                return BreakSessionState.PAUSED
            }
        }

        return state
    }

    fun isResting(context: Context): Boolean {
        return getSessionState(context) == BreakSessionState.RESTING
    }

    fun getRemainingRestMillis(context: Context): Long {
        val prefs = getPrefs(context)
        val restEndTime = prefs.getLong(KEY_REST_END_TIME, 0L)
        return (restEndTime - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun getRemainingSessionMillis(context: Context): Long {
        val prefs = getPrefs(context)
        ensureInitialized(context)
        return prefs.getLong(KEY_REMAINING_MILLIS, 0L).coerceAtLeast(0L)
    }

    fun getTotalSessionMillis(context: Context): Long {
        val prefs = getPrefs(context)
        ensureInitialized(context)
        return prefs.getLong(KEY_TOTAL_SESSION_MILLIS, 0L).coerceAtLeast(1L)
    }

    fun getConsecutiveExtensions(context: Context): Int {
        return getPrefs(context).getInt(KEY_CONSECUTIVE_EXTENSIONS, 0)
    }

    fun canExtend(context: Context): Boolean {
        return getConsecutiveExtensions(context) < MAX_CONSECUTIVE_EXTENSIONS
    }

    fun is3MinReminderSent(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REMINDER_3MIN_SENT, false)
    }

    fun set3MinReminderSent(context: Context, sent: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_REMINDER_3MIN_SENT, sent) }
    }

    fun is60SecReminderSent(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REMINDER_60SEC_SENT, false)
    }

    fun set60SecReminderSent(context: Context, sent: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_REMINDER_60SEC_SENT, sent) }
    }

    fun isCountdownDismissed(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REMINDER_COUNTDOWN_DISMISSED, false)
    }

    fun setCountdownDismissed(context: Context, dismissed: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_REMINDER_COUNTDOWN_DISMISSED, dismissed) }
    }

    fun is2MinReminderSent(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIF_2MIN_SENT, false)
    }

    fun is30SecReminderSent(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIF_30SEC_SENT, false)
    }

    fun set2MinReminderSent(context: Context, sent: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_NOTIF_2MIN_SENT, sent) }
    }

    fun set30SecReminderSent(context: Context, sent: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_NOTIF_30SEC_SENT, sent) }
    }

    fun resetReminderFlags(context: Context) {
        getPrefs(context).edit {
            putBoolean(KEY_REMINDER_3MIN_SENT, false)
            putBoolean(KEY_REMINDER_60SEC_SENT, false)
            putBoolean(KEY_REMINDER_COUNTDOWN_DISMISSED, false)
            putBoolean(KEY_NOTIF_2MIN_SENT, false)
            putBoolean(KEY_NOTIF_30SEC_SENT, false)
        }
        FloatingSnackbarManager.hideImmediate()
        Log.d(TAG, "BreakSession reminder flags reset and FloatingSnackbar dismissed")
    }

    fun checkAndTriggerReminders(context: Context, remainingMillis: Long) {
        // Logika pengingat jeda lama telah dipindahkan ke FloatingSnackbarManager melalui FloatingBubbleService
        // Fungsi ini tetap ada untuk kompatibilitas pemanggilan internal tanpa memicu notifikasi ganda.
    }

    fun getConfiguredSessionMinutes(context: Context): Int {
        val targetSettings = TargetSettingsPreferences(context).loadSettings()
        return targetSettings.sessionDurationMinutes.coerceAtLeast(1)
    }

    internal fun shouldShowIntentPrompt(
        state: BreakSessionState,
        isIntentConfirmed: Boolean,
        consecutiveExtensions: Int,
        remainingMillis: Long,
    ): Boolean {
        if (state == BreakSessionState.RESTING || state == BreakSessionState.EXPIRED) {
            return false
        }
        if (consecutiveExtensions > 0) {
            return false
        }
        if (remainingMillis <= 0L) {
            return false
        }
        return !isIntentConfirmed
    }

    fun needsIntentPrompt(context: Context): Boolean {
        ensureInitialized(context)
        val state = getSessionState(context)
        val prefs = getPrefs(context)
        val remaining = prefs.getLong(KEY_REMAINING_MILLIS, 0L)
        val total = prefs.getLong(KEY_TOTAL_SESSION_MILLIS, 0L)
        val isConfirmed = if (prefs.contains(KEY_SESSION_INTENT_CONFIRMED)) {
            prefs.getBoolean(KEY_SESSION_INTENT_CONFIRMED, false)
        } else {
            remaining in 1L until total
        }
        val extensions = prefs.getInt(KEY_CONSECUTIVE_EXTENSIONS, 0)
        return shouldShowIntentPrompt(
            state = state,
            isIntentConfirmed = isConfirmed,
            consecutiveExtensions = extensions,
            remainingMillis = remaining,
        )
    }

    fun hasPendingIntentSnackbar(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PENDING_INTENT_SNACKBAR, false)
    }

    fun clearPendingIntentSnackbar(context: Context) {
        getPrefs(context).edit {
            putBoolean(KEY_PENDING_INTENT_SNACKBAR, false)
        }
    }

    @JvmOverloads
    fun confirmSessionIntent(
        context: Context,
        selectedIntent: String,
        durationMinutes: Int? = null,
    ) {
        ensureInitialized(context)
        lastMonotonicTime = SystemClock.elapsedRealtime()
        val sessionMillis = if (durationMinutes != null && durationMinutes > 0) {
            durationMinutes.coerceIn(1, 30) * 60L * 1000L
        } else {
            val configuredMinutes = getConfiguredSessionMinutes(context)
            configuredMinutes * 60L * 1000L
        }

        resetReminderFlags(context)
        SessionReminderNotificationHelper.cancelAllReminders(context)

        getPrefs(context).edit {
            putBoolean(KEY_SESSION_INTENT_CONFIRMED, true)
            putString(KEY_LAST_SESSION_INTENT, selectedIntent)
            putBoolean(KEY_PENDING_INTENT_SNACKBAR, true)
            putString(KEY_STATE, BreakSessionState.ACTIVE.name)
            putLong(KEY_REMAINING_MILLIS, sessionMillis)
            putLong(KEY_TOTAL_SESSION_MILLIS, sessionMillis)
            putInt(KEY_CONSECUTIVE_EXTENSIONS, 0)
        }
        Log.d(TAG, "Session intent confirmed: '$selectedIntent' with duration: ${sessionMillis / 60000} min ($sessionMillis ms)")
    }

    fun getLastSessionIntent(context: Context): String? {
        return getPrefs(context).getString(KEY_LAST_SESSION_INTENT, null)
    }

    fun resetSession(context: Context) {
        val configuredMinutes = getConfiguredSessionMinutes(context)
        val sessionMillis = configuredMinutes * 60L * 1000L
        lastMonotonicTime = 0L
        resetReminderFlags(context)
        SessionReminderNotificationHelper.cancelAllReminders(context)
        val currentState = getSessionState(context)
        getPrefs(context).edit {
            putLong(KEY_REMAINING_MILLIS, sessionMillis)
            putLong(KEY_TOTAL_SESSION_MILLIS, sessionMillis)
            putInt(KEY_CONFIGURED_SESSION_MINUTES, configuredMinutes)
            putInt(KEY_CONSECUTIVE_EXTENSIONS, 0)
            putBoolean(KEY_SESSION_INTENT_CONFIRMED, false)
            putBoolean(KEY_PENDING_INTENT_SNACKBAR, false)
            if (currentState != BreakSessionState.RESTING) {
                putString(KEY_STATE, BreakSessionState.PAUSED.name)
            }
        }
        Log.d(TAG, "BreakSession reset to $configuredMinutes minutes ($sessionMillis ms)")
    }

    private fun ensureInitialized(context: Context) {
        val prefs = getPrefs(context)
        val configuredMinutes = getConfiguredSessionMinutes(context)
        val savedConfiguredMinutes = prefs.getInt(KEY_CONFIGURED_SESSION_MINUTES, -1)

        val currentState = getSessionState(context)
        val hasRemaining = prefs.contains(KEY_REMAINING_MILLIS)

        if (!hasRemaining || savedConfiguredMinutes != configuredMinutes) {
            val sessionMillis = configuredMinutes * 60L * 1000L
            resetReminderFlags(context)
            SessionReminderNotificationHelper.cancelAllReminders(context)
            prefs.edit {
                putLong(KEY_REMAINING_MILLIS, sessionMillis)
                putLong(KEY_TOTAL_SESSION_MILLIS, sessionMillis)
                putInt(KEY_CONFIGURED_SESSION_MINUTES, configuredMinutes)
                putInt(KEY_CONSECUTIVE_EXTENSIONS, 0)
                putBoolean(KEY_SESSION_INTENT_CONFIRMED, false)
                putBoolean(KEY_PENDING_INTENT_SNACKBAR, false)
                if (currentState != BreakSessionState.RESTING) {
                    putString(KEY_STATE, BreakSessionState.PAUSED.name)
                }
            }
            Log.d(TAG, "BreakSessionManager initialized with $configuredMinutes minutes ($sessionMillis ms)")
        }
    }

    fun onMonitoredAppEntered(context: Context) {
        val currentState = getSessionState(context)
        if (currentState == BreakSessionState.RESTING) {
            Log.d(TAG, "Monitored app entered during RESTING period")
            return
        }

        ensureInitialized(context)
        if (needsIntentPrompt(context)) {
            lastMonotonicTime = 0L
            return
        }
        lastMonotonicTime = SystemClock.elapsedRealtime()

        if (currentState == BreakSessionState.PAUSED) {
            getPrefs(context).edit {
                putString(KEY_STATE, BreakSessionState.ACTIVE.name)
            }
            Log.d(TAG, "BreakSession state -> ACTIVE")
        }
        val currentRemaining = getRemainingSessionMillis(context)
        checkAndTriggerReminders(context, currentRemaining)
    }

    fun onMonitoredAppExited(context: Context) {
        lastMonotonicTime = 0L
        val currentState = getSessionState(context)
        if (currentState == BreakSessionState.ACTIVE) {
            getPrefs(context).edit {
                putString(KEY_STATE, BreakSessionState.PAUSED.name)
            }
            Log.d(TAG, "BreakSession state -> PAUSED")
        }
    }

    /**
     * Called on each 1-second tick when a monitored app is active in the foreground.
     * Decrements the remaining session time accurately using monotonic clock.
     * Returns true if the session expired on this tick.
     */
    fun tick(context: Context): Boolean {
        val currentState = getSessionState(context)
        if (currentState == BreakSessionState.RESTING) {
            return false
        }
        if (currentState == BreakSessionState.EXPIRED) {
            SessionReminderNotificationHelper.cancelAllReminders(context)
            return true
        }

        ensureInitialized(context)
        if (needsIntentPrompt(context)) {
            lastMonotonicTime = 0L
            return false
        }

        // Jika tick() berjalan, aplikasi yang dipantau sedang aktif. Pastikan status ACTIVE.
        if (currentState == BreakSessionState.PAUSED) {
            getPrefs(context).edit {
                putString(KEY_STATE, BreakSessionState.ACTIVE.name)
            }
            Log.d(TAG, "BreakSession state -> ACTIVE (via tick)")
        }

        val nowMonotonic = SystemClock.elapsedRealtime()
        if (lastMonotonicTime <= 0L) {
            lastMonotonicTime = nowMonotonic
            return false
        }

        val delta = (nowMonotonic - lastMonotonicTime).coerceAtLeast(0L)
        lastMonotonicTime = nowMonotonic

        val prefs = getPrefs(context)
        val currentRemaining = prefs.getLong(KEY_REMAINING_MILLIS, 0L)
        val newRemaining = (currentRemaining - delta).coerceAtLeast(0L)

        prefs.edit {
            putLong(KEY_REMAINING_MILLIS, newRemaining)
            if (newRemaining <= 0L) {
                putString(KEY_STATE, BreakSessionState.EXPIRED.name)
            }
        }

        if (newRemaining <= 0L) {
            Log.d(TAG, "BreakSession EXPIRED (00:00 reached)!")
            SessionReminderNotificationHelper.cancelAllReminders(context)
            return true
        }

        checkAndTriggerReminders(context, newRemaining)
        return false
    }

    fun getLastRestDurationMinutes(context: Context): Int {
        return getPrefs(context).getInt(KEY_LAST_REST_DURATION_MINUTES, DEFAULT_REST_DURATION_MINUTES).coerceAtLeast(1)
    }

    fun isLastRestCustom(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LAST_REST_IS_CUSTOM, false)
    }

    fun setLastRestDuration(context: Context, minutes: Int, isCustom: Boolean = false) {
        getPrefs(context).edit {
            putInt(KEY_LAST_REST_DURATION_MINUTES, minutes.coerceAtLeast(1))
            putBoolean(KEY_LAST_REST_IS_CUSTOM, isCustom)
        }
    }

    fun getTotalRestMillis(context: Context): Long {
        val prefs = getPrefs(context)
        val saved = prefs.getLong(KEY_TOTAL_REST_MILLIS, -1L)
        if (saved > 0L) {
            return saved
        }
        return REST_DURATION_MILLIS
    }

    fun isSessionActive(context: Context): Boolean {
        val state = getSessionState(context)
        if (state == BreakSessionState.RESTING || state == BreakSessionState.EXPIRED) return false
        val prefs = getPrefs(context)
        val isConfirmed = prefs.getBoolean(KEY_SESSION_INTENT_CONFIRMED, false)
        val remaining = prefs.getLong(KEY_REMAINING_MILLIS, 0L)
        val total = prefs.getLong(KEY_TOTAL_SESSION_MILLIS, 0L)
        return (isConfirmed || (remaining in 1L until total)) && remaining > 0L
    }

    @JvmOverloads
    fun startRest(
        context: Context,
        durationMinutes: Int = getLastRestDurationMinutes(context),
        isCustom: Boolean = false,
    ) {
        val validMinutes = durationMinutes.coerceAtLeast(1)
        val restDurationMillis = validMinutes * 60L * 1000L
        val restEndTime = System.currentTimeMillis() + restDurationMillis
        lastMonotonicTime = 0L
        resetReminderFlags(context)
        SessionReminderNotificationHelper.cancelAllReminders(context)
        setLastRestDuration(context, validMinutes, isCustom)
        getPrefs(context).edit {
            putString(KEY_STATE, BreakSessionState.RESTING.name)
            putLong(KEY_REST_END_TIME, restEndTime)
            putLong(KEY_TOTAL_REST_MILLIS, restDurationMillis)
            // Sisa waktu sesi dibuang saat jeda dimulai lebih awal
            putLong(KEY_REMAINING_MILLIS, 0L)
            putInt(KEY_CONSECUTIVE_EXTENSIONS, 0)
        }
        Log.d(TAG, "Started $validMinutes-minute rest period. Ends at $restEndTime")
    }

    fun onRestCompleted(context: Context) {
        val configuredMinutes = getConfiguredSessionMinutes(context)
        val sessionMillis = configuredMinutes * 60L * 1000L
        lastMonotonicTime = 0L
        resetReminderFlags(context)
        SessionReminderNotificationHelper.cancelAllReminders(context)

        getPrefs(context).edit {
            putLong(KEY_REMAINING_MILLIS, sessionMillis)
            putLong(KEY_TOTAL_SESSION_MILLIS, sessionMillis)
            putInt(KEY_CONSECUTIVE_EXTENSIONS, 0)
            putBoolean(KEY_SESSION_INTENT_CONFIRMED, false)
            putBoolean(KEY_PENDING_INTENT_SNACKBAR, false)
            putString(KEY_STATE, BreakSessionState.PAUSED.name)
            remove(KEY_REST_END_TIME)
            remove(KEY_TOTAL_REST_MILLIS)
        }
        Log.d(TAG, "Rest completed! Reset consecutive extensions and initialized new session of $configuredMinutes min")
    }

    fun extendSession(context: Context, extensionMinutes: Int): Long {
        val extensionMillis = extensionMinutes * 60L * 1000L
        val currentExtensions = getConsecutiveExtensions(context)
        val newExtensions = currentExtensions + 1
        lastMonotonicTime = SystemClock.elapsedRealtime()
        resetReminderFlags(context)
        SessionReminderNotificationHelper.cancelAllReminders(context)

        getPrefs(context).edit {
            putLong(KEY_REMAINING_MILLIS, extensionMillis)
            putLong(KEY_TOTAL_SESSION_MILLIS, extensionMillis)
            putInt(KEY_CONSECUTIVE_EXTENSIONS, newExtensions)
            putBoolean(KEY_SESSION_INTENT_CONFIRMED, true)
            putBoolean(KEY_PENDING_INTENT_SNACKBAR, false)
            putString(KEY_STATE, BreakSessionState.ACTIVE.name)
        }

        Log.d(TAG, "Session extended by $extensionMinutes minutes ($extensionMillis ms). Extension #$newExtensions")
        return extensionMillis
    }
}
