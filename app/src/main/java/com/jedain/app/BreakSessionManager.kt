package com.jedain.app

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

    const val KEY_INACTIVE_SINCE_WALL_CLOCK = "inactive_since_wall_clock"
    const val KEY_INACTIVE_SINCE_ELAPSED = "inactive_since_elapsed"
    const val KEY_IS_MONITORED_APP_ACTIVE = "is_monitored_app_active"
    const val KEY_LAST_AUTO_RESET_MILLIS = "last_auto_reset_millis"
    const val KEY_SESSION_VERSION = "session_version"

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
    private var testPrefs: SharedPreferences? = null

    fun setSharedPreferencesForTesting(prefs: SharedPreferences?) {
        testPrefs = prefs
    }

    private fun getPrefs(context: Context?): SharedPreferences {
        testPrefs?.let { return it }
        return context!!.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun cancelRemindersSafely(context: Context?) {
        if (context != null) {
            runCatching { SessionReminderNotificationHelper.cancelAllReminders(context) }
        }
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

    fun getSessionState(context: Context? = null): BreakSessionState {
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

    fun isResting(context: Context? = null): Boolean {
        return getSessionState(context) == BreakSessionState.RESTING
    }

    fun getRemainingRestMillis(context: Context? = null): Long {
        val prefs = getPrefs(context)
        val restEndTime = prefs.getLong(KEY_REST_END_TIME, 0L)
        return (restEndTime - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun getRemainingSessionMillis(
        context: Context? = null,
        currentElapsed: Long = SystemClock.elapsedRealtime(),
        currentWallClock: Long = System.currentTimeMillis(),
    ): Long {
        val prefs = getPrefs(context)
        ensureInitialized(context)
        checkAndApplyAutoReset(context, currentElapsed, currentWallClock)
        return prefs.getLong(KEY_REMAINING_MILLIS, 0L).coerceAtLeast(0L)
    }

    fun getTotalSessionMillis(context: Context? = null): Long {
        val prefs = getPrefs(context)
        ensureInitialized(context)
        return prefs.getLong(KEY_TOTAL_SESSION_MILLIS, 0L).coerceAtLeast(1L)
    }

    fun getConsecutiveExtensions(context: Context? = null): Int {
        return getPrefs(context).getInt(KEY_CONSECUTIVE_EXTENSIONS, 0)
    }

    fun canExtend(context: Context? = null): Boolean {
        return getConsecutiveExtensions(context) < MAX_CONSECUTIVE_EXTENSIONS
    }

    fun is3MinReminderSent(context: Context? = null): Boolean {
        return getPrefs(context).getBoolean(KEY_REMINDER_3MIN_SENT, false)
    }

    fun set3MinReminderSent(context: Context? = null, sent: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_REMINDER_3MIN_SENT, sent) }
    }

    fun is60SecReminderSent(context: Context? = null): Boolean {
        return getPrefs(context).getBoolean(KEY_REMINDER_60SEC_SENT, false)
    }

    fun set60SecReminderSent(context: Context? = null, sent: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_REMINDER_60SEC_SENT, sent) }
    }

    fun isCountdownDismissed(context: Context? = null): Boolean {
        return getPrefs(context).getBoolean(KEY_REMINDER_COUNTDOWN_DISMISSED, false)
    }

    fun setCountdownDismissed(context: Context? = null, dismissed: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_REMINDER_COUNTDOWN_DISMISSED, dismissed) }
    }

    fun is2MinReminderSent(context: Context? = null): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIF_2MIN_SENT, false)
    }

    fun is30SecReminderSent(context: Context? = null): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIF_30SEC_SENT, false)
    }

    fun set2MinReminderSent(context: Context? = null, sent: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_NOTIF_2MIN_SENT, sent) }
    }

    fun set30SecReminderSent(context: Context? = null, sent: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_NOTIF_30SEC_SENT, sent) }
    }

    fun resetReminderFlags(context: Context? = null) {
        getPrefs(context).edit {
            putBoolean(KEY_REMINDER_3MIN_SENT, false)
            putBoolean(KEY_REMINDER_60SEC_SENT, false)
            putBoolean(KEY_REMINDER_COUNTDOWN_DISMISSED, false)
            putBoolean(KEY_NOTIF_2MIN_SENT, false)
            putBoolean(KEY_NOTIF_30SEC_SENT, false)
        }
        runCatching { FloatingSnackbarManager.hideImmediate() }
        Log.d(TAG, "BreakSession reminder flags reset and FloatingSnackbar dismissed")
    }

    fun checkAndTriggerReminders(context: Context? = null, remainingMillis: Long) {
        // Logika pengingat jeda lama telah dipindahkan ke FloatingSnackbarManager melalui FloatingBubbleService
    }

    fun getConfiguredSessionMinutes(context: Context? = null): Int {
        val targetSettings = TargetSettingsPreferences(context, testPrefs).loadSettings()
        return targetSettings.sessionDurationMinutes.coerceAtLeast(1)
    }

    // --- Auto-Reset Sesi Pantau Methods ---

    fun isMonitoredAppActive(context: Context? = null): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_MONITORED_APP_ACTIVE, false)
    }

    fun setMonitoredAppActive(context: Context? = null, active: Boolean) {
        val prefs = getPrefs(context)
        if (active) {
            prefs.edit {
                putBoolean(KEY_IS_MONITORED_APP_ACTIVE, true)
                remove(KEY_INACTIVE_SINCE_ELAPSED)
                remove(KEY_INACTIVE_SINCE_WALL_CLOCK)
            }
        } else {
            val wasActive = prefs.getBoolean(KEY_IS_MONITORED_APP_ACTIVE, false)
            prefs.edit {
                putBoolean(KEY_IS_MONITORED_APP_ACTIVE, false)
                if (wasActive || !prefs.contains(KEY_INACTIVE_SINCE_ELAPSED)) {
                    putLong(KEY_INACTIVE_SINCE_ELAPSED, SystemClock.elapsedRealtime())
                    putLong(KEY_INACTIVE_SINCE_WALL_CLOCK, System.currentTimeMillis())
                }
            }
        }
    }

    fun getAutoResetDurationMinutes(context: Context? = null): Int {
        return TargetSettingsPreferences(context, testPrefs).loadSettings().validatedAutoResetDurationMinutes
    }

    fun getAutoResetDurationMillis(context: Context? = null): Long {
        return getAutoResetDurationMinutes(context) * 60L * 1000L
    }

    fun getInactiveDurationMillis(
        context: Context? = null,
        currentElapsed: Long = SystemClock.elapsedRealtime(),
        currentWallClock: Long = System.currentTimeMillis(),
    ): Long {
        if (isMonitoredAppActive(context)) {
            return 0L
        }
        val prefs = getPrefs(context)
        val elapsedSince = prefs.getLong(KEY_INACTIVE_SINCE_ELAPSED, -1L)
        val wallClockSince = prefs.getLong(KEY_INACTIVE_SINCE_WALL_CLOCK, -1L)
        if (elapsedSince <= 0L && wallClockSince <= 0L) {
            return 0L
        }

        val elapsedDuration = if (elapsedSince > 0L && currentElapsed >= elapsedSince) {
            currentElapsed - elapsedSince
        } else {
            -1L
        }

        val wallClockDuration = if (wallClockSince > 0L && currentWallClock >= wallClockSince) {
            currentWallClock - wallClockSince
        } else {
            -1L
        }

        return when {
            elapsedDuration >= 0L && wallClockDuration >= 0L -> elapsedDuration
            elapsedDuration >= 0L -> elapsedDuration
            wallClockDuration >= 0L -> wallClockDuration
            else -> 0L
        }
    }

    fun getAutoResetRemainingMillis(
        context: Context? = null,
        currentElapsed: Long = SystemClock.elapsedRealtime(),
        currentWallClock: Long = System.currentTimeMillis(),
    ): Long {
        if (isResting(context)) {
            return 0L
        }
        val prefs = getPrefs(context)
        val remainingMillis = prefs.getLong(KEY_REMAINING_MILLIS, -1L)
        val totalMillis = prefs.getLong(KEY_TOTAL_SESSION_MILLIS, -1L)

        // Jika sesi belum pernah digunakan (remaining >= total) dan tidak ada intent aktif
        if (remainingMillis >= totalMillis && remainingMillis > 0L && !prefs.getBoolean(KEY_SESSION_INTENT_CONFIRMED, false)) {
            return getAutoResetDurationMillis(context)
        }

        val inactiveDuration = getInactiveDurationMillis(context, currentElapsed, currentWallClock)
        val autoResetDuration = getAutoResetDurationMillis(context)
        return (autoResetDuration - inactiveDuration).coerceAtLeast(0L)
    }

    fun wasRecentlyAutoReset(
        context: Context? = null,
        currentWallClock: Long = System.currentTimeMillis(),
    ): Boolean {
        val lastReset = getPrefs(context).getLong(KEY_LAST_AUTO_RESET_MILLIS, 0L)
        return lastReset > 0L && (currentWallClock - lastReset) in 0L until 60_000L
    }

    fun clearRecentlyAutoReset(context: Context? = null) {
        getPrefs(context).edit {
            remove(KEY_LAST_AUTO_RESET_MILLIS)
        }
    }

    fun checkAndApplyAutoReset(
        context: Context? = null,
        currentElapsed: Long = SystemClock.elapsedRealtime(),
        currentWallClock: Long = System.currentTimeMillis(),
    ): Boolean {
        if (isResting(context)) {
            return false
        }
        if (isMonitoredAppActive(context)) {
            return false
        }

        val prefs = getPrefs(context)
        val remainingMillis = prefs.getLong(KEY_REMAINING_MILLIS, -1L)
        val totalMillis = prefs.getLong(KEY_TOTAL_SESSION_MILLIS, -1L)

        // Jika sesi belum pernah digunakan sama sekali (remaining >= total)
        // dan tidak ada intent aktif, tidak perlu reset
        if (remainingMillis >= totalMillis && remainingMillis > 0L && !prefs.getBoolean(KEY_SESSION_INTENT_CONFIRMED, false)) {
            return false
        }

        val inactiveDuration = getInactiveDurationMillis(context, currentElapsed, currentWallClock)
        val autoResetDuration = getAutoResetDurationMillis(context)

        if (inactiveDuration >= autoResetDuration) {
            Log.d(TAG, "Auto-reset triggered: inactive for $inactiveDuration ms >= reset limit $autoResetDuration ms")
            val configuredMinutes = getConfiguredSessionMinutes(context)
            val sessionMillis = configuredMinutes * 60L * 1000L
            lastMonotonicTime = 0L
            resetReminderFlags(context)
            cancelRemindersSafely(context)
            val currentSessionVersion = prefs.getLong(KEY_SESSION_VERSION, 0L)
            prefs.edit {
                putLong(KEY_REMAINING_MILLIS, sessionMillis)
                putLong(KEY_TOTAL_SESSION_MILLIS, sessionMillis)
                putInt(KEY_CONFIGURED_SESSION_MINUTES, configuredMinutes)
                putInt(KEY_CONSECUTIVE_EXTENSIONS, 0)
                putBoolean(KEY_SESSION_INTENT_CONFIRMED, false)
                putBoolean(KEY_PENDING_INTENT_SNACKBAR, false)
                putString(KEY_STATE, BreakSessionState.PAUSED.name)
                remove(KEY_INACTIVE_SINCE_ELAPSED)
                remove(KEY_INACTIVE_SINCE_WALL_CLOCK)
                putLong(KEY_LAST_AUTO_RESET_MILLIS, currentWallClock)
                putLong(KEY_SESSION_VERSION, currentSessionVersion + 1)
            }
            return true
        }

        return false
    }

    fun setInactiveSinceForTesting(
        context: Context? = null,
        elapsedMillis: Long,
        wallClockMillis: Long = System.currentTimeMillis(),
    ) {
        getPrefs(context).edit {
            putLong(KEY_INACTIVE_SINCE_ELAPSED, elapsedMillis)
            putLong(KEY_INACTIVE_SINCE_WALL_CLOCK, wallClockMillis)
            putBoolean(KEY_IS_MONITORED_APP_ACTIVE, false)
        }
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

    fun needsIntentPrompt(context: Context? = null): Boolean {
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

    fun hasPendingIntentSnackbar(context: Context? = null): Boolean {
        return getPrefs(context).getBoolean(KEY_PENDING_INTENT_SNACKBAR, false)
    }

    fun clearPendingIntentSnackbar(context: Context? = null) {
        getPrefs(context).edit {
            putBoolean(KEY_PENDING_INTENT_SNACKBAR, false)
        }
    }

    @JvmOverloads
    fun confirmSessionIntent(
        context: Context? = null,
        selectedIntent: String,
        durationMinutes: Int? = null,
    ) {
        ensureInitialized(context)
        lastMonotonicTime = SystemClock.elapsedRealtime()
        val sessionMillis = if (durationMinutes != null && durationMinutes > 0) {
            durationMinutes.coerceIn(
                TargetSettingsPreferences.MIN_SESSION_DURATION_MINUTES,
                TargetSettingsPreferences.MAX_SESSION_DURATION_MINUTES,
            ) * 60L * 1000L
        } else {
            val configuredMinutes = getConfiguredSessionMinutes(context)
            configuredMinutes * 60L * 1000L
        }

        resetReminderFlags(context)
        cancelRemindersSafely(context)

        getPrefs(context).edit {
            putBoolean(KEY_SESSION_INTENT_CONFIRMED, true)
            putString(KEY_LAST_SESSION_INTENT, selectedIntent)
            putBoolean(KEY_PENDING_INTENT_SNACKBAR, true)
            putString(KEY_STATE, BreakSessionState.ACTIVE.name)
            putLong(KEY_REMAINING_MILLIS, sessionMillis)
            putLong(KEY_TOTAL_SESSION_MILLIS, sessionMillis)
            putInt(KEY_CONSECUTIVE_EXTENSIONS, 0)
            remove(KEY_INACTIVE_SINCE_ELAPSED)
            remove(KEY_INACTIVE_SINCE_WALL_CLOCK)
        }
        Log.d(TAG, "Session intent confirmed: '$selectedIntent' with duration: ${sessionMillis / 60000} min ($sessionMillis ms)")
    }

    fun getLastSessionIntent(context: Context? = null): String? {
        return getPrefs(context).getString(KEY_LAST_SESSION_INTENT, null)
    }

    fun resetSession(context: Context? = null) {
        val configuredMinutes = getConfiguredSessionMinutes(context)
        val sessionMillis = configuredMinutes * 60L * 1000L
        lastMonotonicTime = 0L
        resetReminderFlags(context)
        cancelRemindersSafely(context)
        val currentState = getSessionState(context)
        getPrefs(context).edit {
            putLong(KEY_REMAINING_MILLIS, sessionMillis)
            putLong(KEY_TOTAL_SESSION_MILLIS, sessionMillis)
            putInt(KEY_CONFIGURED_SESSION_MINUTES, configuredMinutes)
            putInt(KEY_CONSECUTIVE_EXTENSIONS, 0)
            putBoolean(KEY_SESSION_INTENT_CONFIRMED, false)
            putBoolean(KEY_PENDING_INTENT_SNACKBAR, false)
            remove(KEY_INACTIVE_SINCE_ELAPSED)
            remove(KEY_INACTIVE_SINCE_WALL_CLOCK)
            if (currentState != BreakSessionState.RESTING) {
                putString(KEY_STATE, BreakSessionState.PAUSED.name)
            }
        }
        Log.d(TAG, "BreakSession reset to $configuredMinutes minutes ($sessionMillis ms)")
    }

    private fun ensureInitialized(context: Context? = null) {
        val prefs = getPrefs(context)
        val configuredMinutes = getConfiguredSessionMinutes(context)
        val savedConfiguredMinutes = prefs.getInt(KEY_CONFIGURED_SESSION_MINUTES, -1)

        val currentState = getSessionState(context)
        val hasRemaining = prefs.contains(KEY_REMAINING_MILLIS)

        if (!hasRemaining || savedConfiguredMinutes != configuredMinutes) {
            val sessionMillis = configuredMinutes * 60L * 1000L
            resetReminderFlags(context)
            cancelRemindersSafely(context)
            prefs.edit {
                putLong(KEY_REMAINING_MILLIS, sessionMillis)
                putLong(KEY_TOTAL_SESSION_MILLIS, sessionMillis)
                putInt(KEY_CONFIGURED_SESSION_MINUTES, configuredMinutes)
                putInt(KEY_CONSECUTIVE_EXTENSIONS, 0)
                putBoolean(KEY_SESSION_INTENT_CONFIRMED, false)
                putBoolean(KEY_PENDING_INTENT_SNACKBAR, false)
                remove(KEY_INACTIVE_SINCE_ELAPSED)
                remove(KEY_INACTIVE_SINCE_WALL_CLOCK)
                if (currentState != BreakSessionState.RESTING) {
                    putString(KEY_STATE, BreakSessionState.PAUSED.name)
                }
            }
            Log.d(TAG, "BreakSessionManager initialized with $configuredMinutes minutes ($sessionMillis ms)")
        }
    }

    fun onMonitoredAppEntered(
        context: Context? = null,
        currentElapsed: Long = SystemClock.elapsedRealtime(),
        currentWallClock: Long = System.currentTimeMillis(),
    ) {
        val currentState = getSessionState(context)
        if (currentState == BreakSessionState.RESTING) {
            Log.d(TAG, "Monitored app entered during RESTING period")
            return
        }

        ensureInitialized(context)
        checkAndApplyAutoReset(context, currentElapsed, currentWallClock)
        setMonitoredAppActive(context, true)
        clearRecentlyAutoReset(context)

        if (needsIntentPrompt(context)) {
            lastMonotonicTime = 0L
            return
        }
        lastMonotonicTime = currentElapsed

        if (currentState == BreakSessionState.PAUSED) {
            getPrefs(context).edit {
                putString(KEY_STATE, BreakSessionState.ACTIVE.name)
            }
            Log.d(TAG, "BreakSession state -> ACTIVE")
        }
        val currentRemaining = getRemainingSessionMillis(context, currentElapsed, currentWallClock)
        checkAndTriggerReminders(context, currentRemaining)
    }

    fun onMonitoredAppExited(context: Context? = null) {
        lastMonotonicTime = 0L
        setMonitoredAppActive(context, false)
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
    fun tick(context: Context? = null): Boolean {
        val currentState = getSessionState(context)
        if (currentState == BreakSessionState.RESTING) {
            return false
        }
        if (currentState == BreakSessionState.EXPIRED) {
            cancelRemindersSafely(context)
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
        if (!isMonitoredAppActive(context)) {
            setMonitoredAppActive(context, true)
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
            cancelRemindersSafely(context)
            return true
        }

        checkAndTriggerReminders(context, newRemaining)
        return false
    }

    fun getLastRestDurationMinutes(context: Context? = null): Int {
        return getPrefs(context).getInt(KEY_LAST_REST_DURATION_MINUTES, DEFAULT_REST_DURATION_MINUTES).coerceAtLeast(1)
    }

    fun isLastRestCustom(context: Context? = null): Boolean {
        return getPrefs(context).getBoolean(KEY_LAST_REST_IS_CUSTOM, false)
    }

    fun setLastRestDuration(context: Context? = null, minutes: Int, isCustom: Boolean = false) {
        getPrefs(context).edit {
            putInt(KEY_LAST_REST_DURATION_MINUTES, minutes.coerceAtLeast(1))
            putBoolean(KEY_LAST_REST_IS_CUSTOM, isCustom)
        }
    }

    fun getTotalRestMillis(context: Context? = null): Long {
        val prefs = getPrefs(context)
        val saved = prefs.getLong(KEY_TOTAL_REST_MILLIS, -1L)
        if (saved > 0L) {
            return saved
        }
        return REST_DURATION_MILLIS
    }

    fun isSessionActive(
        context: Context? = null,
        currentElapsed: Long = SystemClock.elapsedRealtime(),
        currentWallClock: Long = System.currentTimeMillis(),
    ): Boolean {
        val state = getSessionState(context)
        if (state == BreakSessionState.RESTING || state == BreakSessionState.EXPIRED) return false
        checkAndApplyAutoReset(context, currentElapsed, currentWallClock)
        val prefs = getPrefs(context)
        val isConfirmed = prefs.getBoolean(KEY_SESSION_INTENT_CONFIRMED, false)
        val remaining = prefs.getLong(KEY_REMAINING_MILLIS, 0L)
        val total = prefs.getLong(KEY_TOTAL_SESSION_MILLIS, 0L)
        return (isConfirmed || (remaining in 1L until total)) && remaining > 0L
    }

    @JvmOverloads
    fun startRest(
        context: Context? = null,
        durationMinutes: Int = getLastRestDurationMinutes(context),
        isCustom: Boolean = false,
    ) {
        val validMinutes = durationMinutes.coerceAtLeast(1)
        val restDurationMillis = validMinutes * 60L * 1000L
        val restEndTime = System.currentTimeMillis() + restDurationMillis
        lastMonotonicTime = 0L
        resetReminderFlags(context)
        cancelRemindersSafely(context)
        setLastRestDuration(context, validMinutes, isCustom)
        getPrefs(context).edit {
            putString(KEY_STATE, BreakSessionState.RESTING.name)
            putLong(KEY_REST_END_TIME, restEndTime)
            putLong(KEY_TOTAL_REST_MILLIS, restDurationMillis)
            // Sisa waktu sesi dibuang saat jeda dimulai lebih awal
            putLong(KEY_REMAINING_MILLIS, 0L)
            putInt(KEY_CONSECUTIVE_EXTENSIONS, 0)
            remove(KEY_INACTIVE_SINCE_ELAPSED)
            remove(KEY_INACTIVE_SINCE_WALL_CLOCK)
        }
        Log.d(TAG, "Started $validMinutes-minute rest period. Ends at $restEndTime")
    }

    fun onRestCompleted(context: Context? = null) {
        val configuredMinutes = getConfiguredSessionMinutes(context)
        val sessionMillis = configuredMinutes * 60L * 1000L
        lastMonotonicTime = 0L
        resetReminderFlags(context)
        cancelRemindersSafely(context)

        getPrefs(context).edit {
            putLong(KEY_REMAINING_MILLIS, sessionMillis)
            putLong(KEY_TOTAL_SESSION_MILLIS, sessionMillis)
            putInt(KEY_CONSECUTIVE_EXTENSIONS, 0)
            putBoolean(KEY_SESSION_INTENT_CONFIRMED, false)
            putBoolean(KEY_PENDING_INTENT_SNACKBAR, false)
            putString(KEY_STATE, BreakSessionState.PAUSED.name)
            remove(KEY_REST_END_TIME)
            remove(KEY_TOTAL_REST_MILLIS)
            remove(KEY_INACTIVE_SINCE_ELAPSED)
            remove(KEY_INACTIVE_SINCE_WALL_CLOCK)
        }
        Log.d(TAG, "Rest completed! Reset consecutive extensions and initialized new session of $configuredMinutes min")
    }

    fun extendSession(context: Context? = null, extensionMinutes: Int): Long {
        val extensionMillis = extensionMinutes * 60L * 1000L
        val currentExtensions = getConsecutiveExtensions(context)
        val newExtensions = currentExtensions + 1
        lastMonotonicTime = SystemClock.elapsedRealtime()
        resetReminderFlags(context)
        cancelRemindersSafely(context)

        getPrefs(context).edit {
            putLong(KEY_REMAINING_MILLIS, extensionMillis)
            putLong(KEY_TOTAL_SESSION_MILLIS, extensionMillis)
            putInt(KEY_CONSECUTIVE_EXTENSIONS, newExtensions)
            putBoolean(KEY_SESSION_INTENT_CONFIRMED, true)
            putBoolean(KEY_PENDING_INTENT_SNACKBAR, false)
            putString(KEY_STATE, BreakSessionState.ACTIVE.name)
            remove(KEY_INACTIVE_SINCE_ELAPSED)
            remove(KEY_INACTIVE_SINCE_WALL_CLOCK)
        }

        Log.d(TAG, "Session extended by $extensionMinutes minutes ($extensionMillis ms). Extension #$newExtensions")
        return extensionMillis
    }
}
