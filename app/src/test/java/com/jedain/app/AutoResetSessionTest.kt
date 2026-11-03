package com.jedain.app

import android.app.usage.UsageEvents
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoResetSessionTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        BreakSessionManager.setSharedPreferencesForTesting(fakePrefs)

        // Default setting: Sesi Pantau = 10 menit, Sesi Reset Otomatis = 30 menit
        fakePrefs.edit()
            .putInt("session_duration_minutes", 10)
            .putInt("auto_reset_duration_minutes", 30)
            .apply()
    }

    @After
    fun tearDown() {
        BreakSessionManager.setSharedPreferencesForTesting(null)
    }

    // --- Pengujian 1: Perpindahan antar-aplikasi pantauan ---
    @Test
    fun testScenario1_monitoredAppSwitch_preservesCountdownAndNoReset() {
        BreakSessionManager.confirmSessionIntent(
            selectedIntent = "Cari hiburan sebentar",
            durationMinutes = 10,
        )
        // Konsumsi waktu 5 menit (sisa 5 menit)
        fakePrefs.edit().putLong("remaining_millis", 5L * 60L * 1000L).apply()
        BreakSessionManager.setMonitoredAppActive(active = true)

        // Pengguna berpindah dari TikTok ke Instagram (keduanya dipantau)
        // Foreground detector mendeteksi aplikasi dipantau tetap aktif
        BreakSessionManager.onMonitoredAppEntered()

        assertEquals(5L * 60L * 1000L, BreakSessionManager.getRemainingSessionMillis())
        assertTrue(BreakSessionManager.isMonitoredAppActive())
        assertEquals(0L, BreakSessionManager.getInactiveDurationMillis())
    }

    // --- Pengujian 2: Keluar ke JedaIn ---
    @Test
    fun testScenario2_exitToJedaIn_countdownPausesAndResetCountdownDecreases() {
        BreakSessionManager.confirmSessionIntent(
            selectedIntent = "Cari hiburan sebentar",
            durationMinutes = 10,
        )
        fakePrefs.edit().putLong("remaining_millis", 5L * 60L * 1000L).apply()

        val baseElapsed = 1_000_000L
        val baseWall = 2_000_000L

        // Keluar dari aplikasi pantau ke JedaIn
        BreakSessionManager.onMonitoredAppExited()
        BreakSessionManager.setInactiveSinceForTesting(
            elapsedMillis = baseElapsed,
            wallClockMillis = baseWall,
        )

        // Sisa countdown sesi tetap 5 menit pada saat keluar (baseElapsed)
        assertEquals(5L * 60L * 1000L, BreakSessionManager.getRemainingSessionMillis(currentElapsed = baseElapsed, currentWallClock = baseWall))
        assertEquals(BreakSessionState.PAUSED, BreakSessionManager.getSessionState())

        // 10 menit berlalu di JedaIn
        val tenMinutesLaterElapsed = baseElapsed + 10L * 60L * 1000L
        val tenMinutesLaterWall = baseWall + 10L * 60L * 1000L

        val remainingReset = BreakSessionManager.getAutoResetRemainingMillis(
            currentElapsed = tenMinutesLaterElapsed,
            currentWallClock = tenMinutesLaterWall,
        )
        // Batas reset 30 menit - berlalu 10 menit = sisa 20 menit
        assertEquals(20L * 60L * 1000L, remainingReset)
        // Countdown sesi pantau tetap tidak berubah (5 menit)
        assertEquals(5L * 60L * 1000L, BreakSessionManager.getRemainingSessionMillis(currentElapsed = tenMinutesLaterElapsed, currentWallClock = tenMinutesLaterWall))
    }

    // --- Pengujian 3: Kembali sebelum batas reset ---
    @Test
    fun testScenario3_returnBeforeResetLimit_maintainsRemainingTimeWithoutReset() {
        BreakSessionManager.confirmSessionIntent(
            selectedIntent = "Cari hiburan sebentar",
            durationMinutes = 10,
        )
        fakePrefs.edit().putLong("remaining_millis", 5L * 60L * 1000L).apply()

        val baseElapsed = 1_000_000L
        val baseWall = 2_000_000L

        BreakSessionManager.onMonitoredAppExited()
        BreakSessionManager.setInactiveSinceForTesting(
            elapsedMillis = baseElapsed,
            wallClockMillis = baseWall,
        )

        // 15 menit berlalu (kurang dari batas reset 30 menit)
        val fifteenMinLaterElapsed = baseElapsed + 15L * 60L * 1000L
        val fifteenMinLaterWall = baseWall + 15L * 60L * 1000L

        val resetOccurred = BreakSessionManager.checkAndApplyAutoReset(
            currentElapsed = fifteenMinLaterElapsed,
            currentWallClock = fifteenMinLaterWall,
        )
        assertFalse(resetOccurred)

        // Pengguna membuka TikTok kembali pada menit ke-15
        BreakSessionManager.onMonitoredAppEntered(currentElapsed = fifteenMinLaterElapsed, currentWallClock = fifteenMinLaterWall)

        // Countdown harus melanjutkan dari 5 menit, jangan reset
        assertEquals(5L * 60L * 1000L, BreakSessionManager.getRemainingSessionMillis(currentElapsed = fifteenMinLaterElapsed, currentWallClock = fifteenMinLaterWall))
    }

    // --- Pengujian 4: Kembali setelah batas reset ---
    @Test
    fun testScenario4_returnAfterResetLimit_automaticallyResetsToInitialDuration() {
        BreakSessionManager.confirmSessionIntent(
            selectedIntent = "Cari hiburan sebentar",
            durationMinutes = 10,
        )
        fakePrefs.edit().putLong("remaining_millis", 5L * 60L * 1000L).apply()

        val baseElapsed = 1_000_000L
        val baseWall = 2_000_000L

        BreakSessionManager.onMonitoredAppExited()
        BreakSessionManager.setInactiveSinceForTesting(
            elapsedMillis = baseElapsed,
            wallClockMillis = baseWall,
        )

        // 30 menit berlalu (mencapai batas reset 30 menit)
        val thirtyMinLaterElapsed = baseElapsed + 30L * 60L * 1000L
        val thirtyMinLaterWall = baseWall + 30L * 60L * 1000L

        val resetOccurred = BreakSessionManager.checkAndApplyAutoReset(
            currentElapsed = thirtyMinLaterElapsed,
            currentWallClock = thirtyMinLaterWall,
        )
        assertTrue(resetOccurred)

        // Countdown otomatis kembali menjadi 10 menit (durasi awal)
        assertEquals(10L * 60L * 1000L, BreakSessionManager.getRemainingSessionMillis(currentElapsed = thirtyMinLaterElapsed, currentWallClock = thirtyMinLaterWall))
        assertTrue(BreakSessionManager.wasRecentlyAutoReset(currentWallClock = thirtyMinLaterWall))
    }

    // --- Pengujian 5: Menggunakan aplikasi non-pantauan ---
    @Test
    fun testScenario5_useUnmonitoredApp_countedAsInactiveAndResetsAfterThreshold() {
        BreakSessionManager.confirmSessionIntent(
            selectedIntent = "Cari hiburan sebentar",
            durationMinutes = 10,
        )
        fakePrefs.edit().putLong("remaining_millis", 5L * 60L * 1000L).apply()

        val baseElapsed = 1_000_000L
        val baseWall = 2_000_000L

        // Berpindah ke WhatsApp (tidak dipantau) -> onMonitoredAppExited dipanggil
        BreakSessionManager.onMonitoredAppExited()
        BreakSessionManager.setInactiveSinceForTesting(
            elapsedMillis = baseElapsed,
            wallClockMillis = baseWall,
        )

        // Menunggu 30 menit di WhatsApp
        val thirtyMinLaterElapsed = baseElapsed + 30L * 60L * 1000L
        val thirtyMinLaterWall = baseWall + 30L * 60L * 1000L

        val resetOccurred = BreakSessionManager.checkAndApplyAutoReset(
            currentElapsed = thirtyMinLaterElapsed,
            currentWallClock = thirtyMinLaterWall,
        )
        assertTrue(resetOccurred)
        assertEquals(10L * 60L * 1000L, BreakSessionManager.getRemainingSessionMillis(currentElapsed = thirtyMinLaterElapsed, currentWallClock = thirtyMinLaterWall))
    }

    // --- Pengujian 6: Layar mati ---
    @Test
    fun testScenario6_screenOff_countsAsInactiveAndResetsAfterThreshold() {
        BreakSessionManager.confirmSessionIntent(
            selectedIntent = "Cari hiburan sebentar",
            durationMinutes = 10,
        )
        fakePrefs.edit().putLong("remaining_millis", 5L * 60L * 1000L).apply()

        val baseElapsed = 1_000_000L
        val baseWall = 2_000_000L

        // Layar mati saat TikTok terbuka -> FloatingBubbleService memanggil onMonitoredAppExited
        BreakSessionManager.onMonitoredAppExited()
        BreakSessionManager.setInactiveSinceForTesting(
            elapsedMillis = baseElapsed,
            wallClockMillis = baseWall,
        )

        // Layar mati selama 30 menit
        val thirtyMinLaterElapsed = baseElapsed + 30L * 60L * 1000L
        val thirtyMinLaterWall = baseWall + 30L * 60L * 1000L

        // Layar dinyalakan kembali dan buka TikTok
        val resetOccurred = BreakSessionManager.checkAndApplyAutoReset(
            currentElapsed = thirtyMinLaterElapsed,
            currentWallClock = thirtyMinLaterWall,
        )
        assertTrue(resetOccurred)
        assertEquals(10L * 60L * 1000L, BreakSessionManager.getRemainingSessionMillis(currentElapsed = thirtyMinLaterElapsed, currentWallClock = thirtyMinLaterWall))
    }

    // --- Pengujian 7: Perpindahan cepat ---
    @Test
    fun testScenario7_rapidSwitchBetweenMonitoredApps_noFalseReset() {
        BreakSessionManager.confirmSessionIntent(
            selectedIntent = "Cari hiburan sebentar",
            durationMinutes = 10,
        )
        fakePrefs.edit().putLong("remaining_millis", 5L * 60L * 1000L).apply()

        // TikTok -> Instagram -> YouTube dengan cepat (status foreground terpantau tetap aktif)
        BreakSessionManager.setMonitoredAppActive(active = true)
        val reset1 = BreakSessionManager.checkAndApplyAutoReset()
        assertFalse(reset1)

        BreakSessionManager.setMonitoredAppActive(active = true)
        val reset2 = BreakSessionManager.checkAndApplyAutoReset()
        assertFalse(reset2)

        assertEquals(5L * 60L * 1000L, BreakSessionManager.getRemainingSessionMillis())
    }

    // --- Pengujian 8: Proses aplikasi dihentikan ---
    @Test
    fun testScenario8_processKilled_stateRestoresAndResetsAfterLimit() {
        BreakSessionManager.confirmSessionIntent(
            selectedIntent = "Cari hiburan sebentar",
            durationMinutes = 10,
        )
        fakePrefs.edit().putLong("remaining_millis", 5L * 60L * 1000L).apply()

        val baseElapsed = 1_000_000L
        val baseWall = 2_000_000L

        BreakSessionManager.onMonitoredAppExited()
        BreakSessionManager.setInactiveSinceForTesting(
            elapsedMillis = baseElapsed,
            wallClockMillis = baseWall,
        )

        // Simulasikan proses Android mati: instance in-memory hilang,
        // namun SharedPreferences tersimpan di disk.
        // Saat proses baru start dan membaca SharedPreferences setelah 35 menit:
        val thirtyFiveMinLaterElapsed = baseElapsed + 35L * 60L * 1000L
        val thirtyFiveMinLaterWall = baseWall + 35L * 60L * 1000L

        val resetOccurred = BreakSessionManager.checkAndApplyAutoReset(
            currentElapsed = thirtyFiveMinLaterElapsed,
            currentWallClock = thirtyFiveMinLaterWall,
        )
        assertTrue(resetOccurred)
        assertEquals(10L * 60L * 1000L, BreakSessionManager.getRemainingSessionMillis(currentElapsed = thirtyFiveMinLaterElapsed, currentWallClock = thirtyFiveMinLaterWall))
    }

    // --- Pengujian 9: Reboot perangkat ---
    @Test
    fun testScenario9_deviceReboot_elapsedRealtimeResets_fallsBackToWallClockAndResets() {
        BreakSessionManager.confirmSessionIntent(
            selectedIntent = "Cari hiburan sebentar",
            durationMinutes = 10,
        )
        fakePrefs.edit().putLong("remaining_millis", 5L * 60L * 1000L).apply()

        val beforeRebootElapsed = 50_000_000L // 13.8 jam uptime sebelum reboot
        val beforeRebootWall = 1_700_000_000_000L

        BreakSessionManager.onMonitoredAppExited()
        BreakSessionManager.setInactiveSinceForTesting(
            elapsedMillis = beforeRebootElapsed,
            wallClockMillis = beforeRebootWall,
        )

        // Perangkat restart: elapsedRealtime reset ke 300_000 ms (5 menit setelah boot baru)
        // Padahal sebelum reboot elapsed bernilai 50_000_000 ms (elapsed sekarang < elapsed waktu disimpan)
        val afterRebootElapsed = 300_000L
        // Wall-clock waktu sekarang maju 40 menit dari waktu simpan
        val afterRebootWall = beforeRebootWall + 40L * 60L * 1000L

        val inactiveDuration = BreakSessionManager.getInactiveDurationMillis(
            currentElapsed = afterRebootElapsed,
            currentWallClock = afterRebootWall,
        )
        assertEquals(40L * 60L * 1000L, inactiveDuration)

        val resetOccurred = BreakSessionManager.checkAndApplyAutoReset(
            currentElapsed = afterRebootElapsed,
            currentWallClock = afterRebootWall,
        )
        assertTrue(resetOccurred)
        assertEquals(10L * 60L * 1000L, BreakSessionManager.getRemainingSessionMillis(currentElapsed = afterRebootElapsed, currentWallClock = afterRebootWall))
    }

    // --- Pengujian 10: Statistik tidak terhapus ---
    @Test
    fun testScenario10_statistics_dailyScreenTimeUntouchedWhenAutoResetOccurs() {
        val start = 1_000_000L
        val end = 2_000_000L
        val tiktokPkg = "com.zhiliaoapp.musically"

        // Pengguna menggunakan TikTok 5 menit (300.000 ms)
        val events = listOf(
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, start, "MainActivity"),
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_PAUSED, start + 300_000L, "MainActivity"),
        )

        val usageBeforeReset = computeForegroundMillis(
            events = events,
            startTime = start,
            endTime = end,
            ownPackage = "com.jedain.app",
            supportsScreenEvents = true,
        )
        assertEquals(300_000L, usageBeforeReset[tiktokPkg])

        // Auto reset sesi countdown berlangsung (countdown kembali ke 10 menit)
        BreakSessionManager.resetSession()
        assertEquals(10L * 60L * 1000L, BreakSessionManager.getRemainingSessionMillis())

        // Statistik penggunaan actual di UsageStats tetap mencatat 5 menit (tidak hilang)
        val usageAfterReset = computeForegroundMillis(
            events = events,
            startTime = start,
            endTime = end,
            ownPackage = "com.jedain.app",
            supportsScreenEvents = true,
        )
        assertEquals(300_000L, usageAfterReset[tiktokPkg])
    }

    // --- Pengujian 11: Sesi jeda (Ambil Jeda) ---
    @Test
    fun testScenario11_breakSession_restPeriodNotInterruptedByAutoReset() {
        // Pengguna mengambil jeda 5 menit
        BreakSessionManager.startRest(durationMinutes = 5)
        assertTrue(BreakSessionManager.isResting())
        assertEquals(BreakSessionState.RESTING, BreakSessionManager.getSessionState())

        // Waktu tidak aktif berjalan 35 menit
        val baseElapsed = 1_000_000L
        val baseWall = 2_000_000L
        val thirtyFiveMinLaterElapsed = baseElapsed + 35L * 60L * 1000L
        val thirtyFiveMinLaterWall = baseWall + 35L * 60L * 1000L

        // Auto reset tidak boleh merusak / mengganggu periode resting
        val resetOccurred = BreakSessionManager.checkAndApplyAutoReset(
            currentElapsed = thirtyFiveMinLaterElapsed,
            currentWallClock = thirtyFiveMinLaterWall,
        )
        assertFalse(resetOccurred)
        assertTrue(BreakSessionManager.isResting())
    }

    // --- Pengujian 12: Floating timer synchronization ---
    @Test
    fun testScenario12_floatingTimer_readsConsistentCountdownAfterReset() {
        BreakSessionManager.confirmSessionIntent(
            selectedIntent = "Cari hiburan sebentar",
            durationMinutes = 10,
        )
        fakePrefs.edit().putLong("remaining_millis", 5L * 60L * 1000L).apply()

        val baseElapsed = 1_000_000L
        val baseWall = 2_000_000L

        // Floating timer membaca sisa countdown sebelum batas reset
        val bubbleTimerTextBefore = FloatingBubbleService.formatTimerDigital(
            BreakSessionManager.getRemainingSessionMillis(currentElapsed = baseElapsed, currentWallClock = baseWall),
        )
        assertEquals("05:00", bubbleTimerTextBefore)

        // Batas reset 30 menit tercapai
        BreakSessionManager.onMonitoredAppExited()
        BreakSessionManager.setInactiveSinceForTesting(
            elapsedMillis = baseElapsed,
            wallClockMillis = baseWall,
        )

        val thirtyMinLaterElapsed = baseElapsed + 30L * 60L * 1000L
        val thirtyMinLaterWall = baseWall + 30L * 60L * 1000L

        BreakSessionManager.checkAndApplyAutoReset(
            currentElapsed = thirtyMinLaterElapsed,
            currentWallClock = thirtyMinLaterWall,
        )

        // Floating timer membaca sisa countdown setelah reset
        val bubbleTimerTextAfter = FloatingBubbleService.formatTimerDigital(
            BreakSessionManager.getRemainingSessionMillis(currentElapsed = thirtyMinLaterElapsed, currentWallClock = thirtyMinLaterWall),
        )
        assertEquals("10:00", bubbleTimerTextAfter)
    }

    // --- Pengujian Pengaturan Auto Reset: Presets & Custom ---
    @Test
    fun testAutoResetSettings_presetsAndCustomValidation() {
        assertEquals(listOf(15, 30, 45, 60), PresetAutoResetDurationMinutes)
        assertEquals(30, TargetSettingsPreferences.DEFAULT_AUTO_RESET_MINUTES)
        assertEquals(1, TargetSettingsPreferences.MIN_AUTO_RESET_DURATION_MINUTES)
        assertEquals(120, TargetSettingsPreferences.MAX_AUTO_RESET_DURATION_MINUTES)

        assertEquals(1, validateAutoResetDurationMinutes(0))
        assertEquals(1, validateAutoResetDurationMinutes(-5))
        assertEquals(15, validateAutoResetDurationMinutes(15))
        assertEquals(120, validateAutoResetDurationMinutes(120))
        assertEquals(120, validateAutoResetDurationMinutes(150))

        val prefs = TargetSettingsPreferences(prefsOverride = fakePrefs)
        val defaultSettings = prefs.loadSettings()
        assertEquals(30, defaultSettings.autoResetDurationMinutes)
        assertFalse(defaultSettings.isCustomAutoResetDuration)

        val customState = TargetSettingsState(
            autoResetDurationMinutes = 75,
            isCustomAutoResetDuration = true,
            customAutoResetDurationMinutes = 75,
        )
        prefs.saveSettings(customState)
        val loadedCustom = prefs.loadSettings()
        assertEquals(75, loadedCustom.autoResetDurationMinutes)
        assertTrue(loadedCustom.isCustomAutoResetDuration)
    }
}
