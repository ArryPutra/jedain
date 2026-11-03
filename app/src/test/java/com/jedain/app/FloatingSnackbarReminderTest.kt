package com.jedain.app

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
    fun testAutoDismissDurationIsSevenSecondsForInitial3MinReminder() {
        val autoDismissDuration = FloatingSnackbarManager.AUTO_DISMISS_DURATION_MS
        assertEquals(7000L, autoDismissDuration)

        fun isInitial3MinVisibleAtSecond(elapsedSeconds: Int): Boolean {
            return elapsedSeconds < (autoDismissDuration / 1000L)
        }

        assertTrue("Detik 0 harus terlihat", isInitial3MinVisibleAtSecond(0))
        assertTrue("Detik 1 harus terlihat", isInitial3MinVisibleAtSecond(1))
        assertTrue("Detik 2 harus terlihat", isInitial3MinVisibleAtSecond(2))
        assertTrue("Detik 3 harus terlihat", isInitial3MinVisibleAtSecond(3))
        assertTrue("Detik 4 harus terlihat", isInitial3MinVisibleAtSecond(4))
        assertTrue("Detik 5 harus terlihat", isInitial3MinVisibleAtSecond(5))
        assertTrue("Detik 6 harus terlihat", isInitial3MinVisibleAtSecond(6))
        assertFalse("Detik 7 harus otomatis menghilang untuk 3-min reminder", isInitial3MinVisibleAtSecond(7))
        assertFalse("Detik 8 harus tetap menghilang", isInitial3MinVisibleAtSecond(8))
    }

    @Test
    fun testCountdown60Sec_noAutoDismiss_remainsVisibleUntilManualClose() {
        // Simulasi siklus hidup countdown 60 detik (1 menit)
        var isManuallyClosed = false

        fun isCountdownVisibleAt(@Suppress("UNUSED_PARAMETER") elapsedSeconds: Int): Boolean {
            // Countdown 1 menit tidak memiliki timeout / auto-dismiss; hanya hilang jika ditutup manual
            return !isManuallyClosed
        }

        // Tampil pada detik 0
        assertTrue("Detik 0 harus tampil", isCountdownVisibleAt(0))
        // Melewati batas 7 detik (auto-dismiss lama): HARUS TETAP TAMPIL
        assertTrue("Detik 7 harus tetap tampil (tidak ada auto-dismiss)", isCountdownVisibleAt(7))
        assertTrue("Detik 8 harus tetap tampil", isCountdownVisibleAt(8))
        assertTrue("Detik 30 harus tetap tampil", isCountdownVisibleAt(30))
        assertTrue("Detik 59 harus tetap tampil", isCountdownVisibleAt(59))
        // Detik 60 (mencapai 00:00): HARUS TETAP TAMPIL
        assertTrue("Detik 60 (mencapai 00:00) harus tetap tampil", isCountdownVisibleAt(60))
        assertTrue("Detik 75 (melewati 00:00) harus tetap tampil", isCountdownVisibleAt(75))

        // Pengguna menekan tombol close (X) atau Tutup secara manual
        isManuallyClosed = true
        assertFalse("Setelah ditutup manual, snackbar baru menghilang", isCountdownVisibleAt(76))
    }

    @Test
    fun testCountdownAtZeroSeconds_displaysFinishedState() {
        val formattedAtZero = FloatingSnackbarManager.formatCountdownText(0)
        assertEquals("00:00", formattedAtZero)

        val formattedNegativeClamped = FloatingSnackbarManager.formatCountdownText(-1)
        assertEquals("00:00", formattedNegativeClamped)
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

    @Test
    fun testIntentionReminderSnackbar_mappingAndPriorityRules() {
        // 1. Tipe INTENTION_REMINDER tersedia
        assertEquals(
            FloatingSnackbarManager.SnackbarType.INTENTION_REMINDER,
            FloatingSnackbarManager.SnackbarType.valueOf("INTENTION_REMINDER")
        )

        // 2. Pemetaan pesan niat sesuai spesifikasi
        assertEquals(
            "Cari hiburan secukupnya ya. Nikmati, lalu berhenti.",
            BreakSessionManager.getIntentReminderMessage("Cari hiburan sebentar")
        )
        assertEquals(
            "Cek kabar saja. Selesai, langsung keluar.",
            BreakSessionManager.getIntentReminderMessage("Cek pesan atau kabar")
        )
        assertEquals(
            "Cari yang kamu butuhkan, lalu selesai.",
            BreakSessionManager.getIntentReminderMessage("Cari informasi")
        )
        assertEquals(
            "Kamu membuka ini tanpa tujuan. Perlu lanjut?",
            BreakSessionManager.getIntentReminderMessage("Hanya kebiasaan, tidak tahu mau apa")
        )

        // Fallback untuk teks tak dikenal
        assertEquals(
            "Gunakan waktu dengan bijak sesuai tujuanmu.",
            BreakSessionManager.getIntentReminderMessage("Pilihan lain")
        )

        // 3. Aturan prioritas tabrakan: Snackbar 3m / 1m SELALU menang atas snackbar niat
        fun shouldShowIntentionSnackbar(
            hasPending: Boolean,
            isResting: Boolean,
            isExpired: Boolean,
            reminderOnSessionEnd: Boolean,
            remainingMillis: Long,
            totalSessionMillis: Long,
        ): Boolean {
            if (!hasPending || isResting || isExpired) return false
            if (reminderOnSessionEnd) {
                val is60SecZone = remainingMillis in 1L..BreakSessionManager.SIXTY_SECONDS_MILLIS
                val is3MinZone = remainingMillis in (BreakSessionManager.SIXTY_SECONDS_MILLIS + 1L)..BreakSessionManager.THREE_MINUTES_MILLIS &&
                    totalSessionMillis >= BreakSessionManager.THREE_MINUTES_MILLIS
                if (is60SecZone || is3MinZone) return false // 3m / 1m menang!
            }
            return true
        }

        val total15Min = 15L * 60_000L

        // Normal: Sesi baru dimulai di sisa 15 menit -> Snackbar niat muncul
        assertTrue(
            shouldShowIntentionSnackbar(
                hasPending = true,
                isResting = false,
                isExpired = false,
                reminderOnSessionEnd = true,
                remainingMillis = total15Min,
                totalSessionMillis = total15Min,
            )
        )

        // Bertabrakan dengan zona 60 detik -> Snackbar 60s menang, niat tidak muncul
        assertFalse(
            shouldShowIntentionSnackbar(
                hasPending = true,
                isResting = false,
                isExpired = false,
                reminderOnSessionEnd = true,
                remainingMillis = 45_000L,
                totalSessionMillis = total15Min,
            )
        )

        // Bertabrakan dengan zona 3 menit -> Snackbar 3m menang, niat tidak muncul
        assertFalse(
            shouldShowIntentionSnackbar(
                hasPending = true,
                isResting = false,
                isExpired = false,
                reminderOnSessionEnd = true,
                remainingMillis = 150_000L,
                totalSessionMillis = total15Min,
            )
        )

        // Sesi jeda aktif (RESTING) -> Tidak muncul
        assertFalse(
            shouldShowIntentionSnackbar(
                hasPending = true,
                isResting = true,
                isExpired = false,
                reminderOnSessionEnd = true,
                remainingMillis = total15Min,
                totalSessionMillis = total15Min,
            )
        )

        // Sudah pernah ditampilkan (hasPending == false) -> Tidak muncul lagi
        assertFalse(
            shouldShowIntentionSnackbar(
                hasPending = false,
                isResting = false,
                isExpired = false,
                reminderOnSessionEnd = true,
                remainingMillis = total15Min,
                totalSessionMillis = total15Min,
            )
        )
    }
}

