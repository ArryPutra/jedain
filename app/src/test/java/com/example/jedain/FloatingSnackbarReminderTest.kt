package com.example.jedain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingSnackbarReminderTest {

    @Test
    fun testFormatCountdownText_formatsCorrectly() {
        assertEquals("01:00", FloatingSnackbarManager.formatCountdownText(60))
        assertEquals("00:59", FloatingSnackbarManager.formatCountdownText(59))
        assertEquals("00:45", FloatingSnackbarManager.formatCountdownText(45))
        assertEquals("00:30", FloatingSnackbarManager.formatCountdownText(30))
        assertEquals("00:10", FloatingSnackbarManager.formatCountdownText(10))
        assertEquals("00:05", FloatingSnackbarManager.formatCountdownText(5))
        assertEquals("00:01", FloatingSnackbarManager.formatCountdownText(1))
        assertEquals("00:00", FloatingSnackbarManager.formatCountdownText(0))
        assertEquals("00:00", FloatingSnackbarManager.formatCountdownText(-5))
    }

    @Test
    fun testThresholdConstants() {
        assertEquals(180_000L, BreakSessionManager.THREE_MINUTES_MILLIS)
        assertEquals(60_000L, BreakSessionManager.SIXTY_SECONDS_MILLIS)
        // Memastikan durasi auto-dismiss adalah 7 detik sesuai kebutuhan revisi
        assertEquals(7_000L, FloatingSnackbarManager.AUTO_DISMISS_DURATION_MS)
        assertEquals("reminder_3min_sent", BreakSessionManager.KEY_REMINDER_3MIN_SENT)
        assertEquals("reminder_60sec_sent", BreakSessionManager.KEY_REMINDER_60SEC_SENT)
        assertEquals("reminder_countdown_dismissed", BreakSessionManager.KEY_REMINDER_COUNTDOWN_DISMISSED)
    }

    /**
     * Helper evaluasi trigger 3 menit sesuai implementasi di FloatingBubbleService.kt.
     */
    private fun shouldTrigger3MinThreshold(
        remainingMillis: Long,
        totalSessionMillis: Long,
        is3MinSent: Boolean,
    ): Boolean {
        return remainingMillis in (BreakSessionManager.SIXTY_SECONDS_MILLIS + 1L)..BreakSessionManager.THREE_MINUTES_MILLIS &&
            totalSessionMillis >= BreakSessionManager.THREE_MINUTES_MILLIS &&
            !is3MinSent
    }

    /**
     * Helper evaluasi trigger 60 detik countdown sesuai implementasi di FloatingBubbleService.kt.
     */
    private fun shouldTrigger60SecCountdown(
        remainingMillis: Long,
        is60SecSent: Boolean,
        isDismissed: Boolean,
    ): Boolean {
        return remainingMillis in 1L..BreakSessionManager.SIXTY_SECONDS_MILLIS &&
            !isDismissed &&
            !is60SecSent
    }

    @Test
    fun testScenarioA_NormalCountdownFrom10Minutes() {
        val total10m = 10L * 60L * 1000L // 600,000 ms
        var is3MinSent = false

        // Sisa 10:00 (600,000 ms) -> Tidak muncul
        assertFalse(shouldTrigger3MinThreshold(600_000L, total10m, is3MinSent))

        // Sisa 09:00 (540,000 ms) -> Tidak muncul
        assertFalse(shouldTrigger3MinThreshold(540_000L, total10m, is3MinSent))

        // Sisa 06:00 (360,000 ms) -> Tidak muncul
        assertFalse(shouldTrigger3MinThreshold(360_000L, total10m, is3MinSent))

        // Sisa 04:00 (240,000 ms) -> Tidak muncul
        assertFalse(shouldTrigger3MinThreshold(240_000L, total10m, is3MinSent))

        // Sisa 03:01 (181,000 ms) -> Tidak muncul
        assertFalse(shouldTrigger3MinThreshold(181_000L, total10m, is3MinSent))

        // Tepat 03:00 (180,000 ms) -> Muncul satu kali!
        assertTrue(shouldTrigger3MinThreshold(180_000L, total10m, is3MinSent))
        is3MinSent = true // Status berubah menjadi sudah ditampilkan

        // Sisa 02:59 (179,000 ms) -> Tidak muncul lagi
        assertFalse(shouldTrigger3MinThreshold(179_000L, total10m, is3MinSent))

        // Sisa 02:00 (120,000 ms) -> Tidak muncul lagi dari trigger 3 menit
        assertFalse(shouldTrigger3MinThreshold(120_000L, total10m, is3MinSent))

        // Sisa 01:01 (61,000 ms) -> Tidak muncul lagi dari trigger 3 menit
        assertFalse(shouldTrigger3MinThreshold(61_000L, total10m, is3MinSent))

        // Sisa 01:00 (60,000 ms) -> Berada di bawah threshold 3 menit, tidak muncul dari trigger 3 menit
        assertFalse(shouldTrigger3MinThreshold(60_000L, total10m, is3MinSent))
    }

    @Test
    fun testScenarioB_CountdownJumpsPastExactThreshold() {
        // Simulasi jika terjadi sedikit latency/delay timer sehingga callback melompati 03:00
        val total5m = 5L * 60L * 1000L
        var is3MinSent = false

        // Detik sebelum threshold: 03:01 (181,000 ms)
        assertFalse(shouldTrigger3MinThreshold(181_000L, total5m, is3MinSent))

        // Timer melompat langsung ke 02:59 (179,000 ms) akibat tick latency
        // Harus tetap berhasil terpicu satu kali!
        assertTrue(shouldTrigger3MinThreshold(179_000L, total5m, is3MinSent))
        is3MinSent = true

        // Detik berikutnya 02:58 (178,000 ms) -> Tidak muncul lagi
        assertFalse(shouldTrigger3MinThreshold(178_000L, total5m, is3MinSent))
    }

    @Test
    fun testScenarioC_LifecycleChangeDoesNotDuplicateTrigger() {
        val total10m = 10L * 60L * 1000L
        var is3MinSent = true // Sudah pernah terpicu sebelum aplikasi di-minimize

        // Pengguna minimize app, switch app, atau matikan layar pada sisa 02:30 (150,000 ms)
        // Ketika kembali ke aplikasi, trigger tidak boleh dipanggil lagi
        assertFalse(shouldTrigger3MinThreshold(150_000L, total10m, is3MinSent))

        // Berjalan terus hingga 02:00
        assertFalse(shouldTrigger3MinThreshold(120_000L, total10m, is3MinSent))
    }

    @Test
    fun testScenarioD_NewCountdownCycleResetsTrigger() {
        val total5m = 5L * 60L * 1000L
        var is3MinSent = true // Siklus lama sudah memicu

        // Siklus lama berakhir atau di-reset
        // Simulasi resetReminderFlags() pada siklus baru:
        is3MinSent = false

        // Mulai siklus baru 5 menit pada sisa 03:00 (180,000 ms) -> Harus bisa muncul kembali 1x
        assertTrue(shouldTrigger3MinThreshold(180_000L, total5m, is3MinSent))
        is3MinSent = true

        // Setelah terpicu di siklus baru, tidak boleh muncul lagi
        assertFalse(shouldTrigger3MinThreshold(179_000L, total5m, is3MinSent))
    }

    @Test
    fun testBothTriggersIndependentAndNonOverlapping() {
        val totalSession = 10L * 60L * 1000L
        var is3MinSent = false
        var is60SecSent = false
        val isDismissed = false

        // Pada t = 03:00 (180,000 ms):
        assertTrue(shouldTrigger3MinThreshold(180_000L, totalSession, is3MinSent))
        assertFalse(shouldTrigger60SecCountdown(180_000L, is60SecSent, isDismissed))
        is3MinSent = true

        // Pada t = 02:00 (120,000 ms):
        assertFalse(shouldTrigger3MinThreshold(120_000L, totalSession, is3MinSent))
        assertFalse(shouldTrigger60SecCountdown(120_000L, is60SecSent, isDismissed))

        // Pada t = 01:00 (60,000 ms):
        assertFalse(shouldTrigger3MinThreshold(60_000L, totalSession, is3MinSent))
        assertTrue(shouldTrigger60SecCountdown(60_000L, is60SecSent, isDismissed))
        is60SecSent = true

        // Pada t = 00:50 (50,000 ms) setelah 60s terkirim:
        assertFalse(shouldTrigger3MinThreshold(50_000L, totalSession, is3MinSent))
        assertFalse(shouldTrigger60SecCountdown(50_000L, is60SecSent, isDismissed))

        // Pada t = 00:00 (0 ms) waktu habis:
        assertFalse(shouldTrigger3MinThreshold(0L, totalSession, is3MinSent))
        assertFalse(shouldTrigger60SecCountdown(0L, is60SecSent, isDismissed))
    }

    @Test
    fun testAutoDismissDurationIsSevenSeconds() {
        val autoDismissDuration = FloatingSnackbarManager.AUTO_DISMISS_DURATION_MS
        assertEquals(7000L, autoDismissDuration)

        fun isVisibleAtSecond(elapsedSeconds: Int): Boolean {
            return elapsedSeconds < (autoDismissDuration / 1000L)
        }

        assertTrue("Detik 0 harus terlihat", isVisibleAtSecond(0))
        assertTrue("Detik 1 harus terlihat", isVisibleAtSecond(1))
        assertTrue("Detik 2 harus terlihat", isVisibleAtSecond(2))
        assertTrue("Detik 3 harus terlihat", isVisibleAtSecond(3))
        assertTrue("Detik 4 harus terlihat", isVisibleAtSecond(4))
        assertTrue("Detik 5 harus terlihat", isVisibleAtSecond(5))
        assertTrue("Detik 6 harus terlihat", isVisibleAtSecond(6))
        assertFalse("Detik 7 harus otomatis menghilang", isVisibleAtSecond(7))
        assertFalse("Detik 8 harus tetap menghilang", isVisibleAtSecond(8))
    }

    @Test
    fun testCountdownSecondsCalculation() {
        fun calculateSecondsLeft(remainingMillis: Long): Int {
            return ((remainingMillis + 999L) / 1000L).toInt().coerceIn(1, 60)
        }

        assertEquals(60, calculateSecondsLeft(60_000L))
        assertEquals(60, calculateSecondsLeft(59_500L))
        assertEquals(59, calculateSecondsLeft(59_000L))
        assertEquals(30, calculateSecondsLeft(30_000L))
        assertEquals(1, calculateSecondsLeft(1_000L))
        assertEquals(1, calculateSecondsLeft(500L))
        assertEquals(1, calculateSecondsLeft(1L))
    }

    @Test
    fun testPreventDuplicateRedirectThrottleLogic() {
        var lastLaunchTime = 0L

        fun canLaunchIntervention(currentTime: Long): Boolean {
            if (currentTime - lastLaunchTime < 2000L) {
                return false
            }
            lastLaunchTime = currentTime
            return true
        }

        assertTrue(canLaunchIntervention(10_000L))
        assertFalse(canLaunchIntervention(10_100L))
        assertFalse(canLaunchIntervention(11_500L))
        assertTrue(canLaunchIntervention(12_500L))
    }
}
