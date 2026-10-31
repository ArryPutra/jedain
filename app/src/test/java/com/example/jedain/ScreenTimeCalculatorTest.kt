package com.example.jedain

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeCalculatorTest {

    private val ownPkg = "com.example.jedain"
    private val tiktokPkg = "com.zhiliaoapp.musically"
    private val chromePkg = "com.android.chrome"

    // 1. Satu aplikasi dibuka dan ditutup secara normal
    @Test
    fun scenario1_singleAppOpenedAndClosedNormally() {
        val start = 1_000_000L
        val end = 2_000_000L
        val events = listOf(
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, start + 10_000L, "MainActivity"),
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_PAUSED, start + 70_000L, "MainActivity"),
        )

        val result = computeForegroundMillis(
            events = events,
            startTime = start,
            endTime = end,
            ownPackage = ownPkg,
            supportsScreenEvents = true,
        )

        assertEquals(60_000L, result[tiktokPkg])
    }

    // 2. Pengguna berpindah dari TikTok ke Chrome
    @Test
    fun scenario2_userSwitchesFromTikTokToChrome() {
        val start = 1_000_000L
        val end = 5_000_000L
        val events = listOf(
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, start + 100_000L, "MainActivity"),
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_PAUSED, start + 400_000L, "MainActivity"),
            UsageEventLite(chromePkg, UsageEvents.Event.ACTIVITY_RESUMED, start + 400_000L, "ChromeTabbedActivity"),
            UsageEventLite(chromePkg, UsageEvents.Event.ACTIVITY_PAUSED, start + 900_000L, "ChromeTabbedActivity"),
        )

        val result = computeForegroundMillis(
            events = events,
            startTime = start,
            endTime = end,
            ownPackage = ownPkg,
            supportsScreenEvents = true,
        )

        assertEquals(300_000L, result[tiktokPkg])
        assertEquals(500_000L, result[chromePkg])
    }

    // 3. Aplikasi aktif melewati tengah malam
    @Test
    fun scenario3_appActiveAcrossMidnight() {
        val yesterdayMidnight = 10_000_000L
        val todayMidnight = yesterdayMidnight + 86_400_000L
        val now = todayMidnight + 3_600_000L // 01:00 pagi

        // TikTok dibuka 15 menit sebelum tengah malam dan ditutup 20 menit setelah tengah malam
        val resumeTime = todayMidnight - 15L * 60_000L
        val pauseTime = todayMidnight + 20L * 60_000L

        val events = listOf(
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, resumeTime, "MainActivity"),
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_PAUSED, pauseTime, "MainActivity"),
        )

        // Hitung untuk hari ini [todayMidnight, now] -> hanya 20 menit yang dihitung
        val todayResult = computeForegroundMillis(
            events = events,
            startTime = todayMidnight,
            endTime = now,
            ownPackage = ownPkg,
            supportsScreenEvents = true,
        )
        assertEquals(20L * 60_000L, todayResult[tiktokPkg])

        // Hitung untuk kemarin [yesterdayMidnight, todayMidnight] -> hanya 15 menit yang dihitung
        val yesterdayResult = computeForegroundMillis(
            events = events,
            startTime = yesterdayMidnight,
            endTime = todayMidnight,
            ownPackage = ownPkg,
            supportsScreenEvents = true,
        )
        assertEquals(15L * 60_000L, yesterdayResult[tiktokPkg])
    }

    // 4. Layar mati saat aplikasi sedang aktif
    @Test
    fun scenario4_screenTurnedOffWhileAppIsActive() {
        val start = 1_000_000L
        val end = 2_000_000L
        val events = listOf(
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, start + 10_000L, "MainActivity"),
            UsageEventLite("android", EVENT_SCREEN_NON_INTERACTIVE, start + 40_000L), // Layar mati setelah 30 detik
            // Event PAUSED baru dikirim OS 5 detik kemudian
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_PAUSED, start + 45_000L, "MainActivity"),
        )

        val result = computeForegroundMillis(
            events = events,
            startTime = start,
            endTime = end,
            ownPackage = ownPkg,
            supportsScreenEvents = true,
        )

        // Sesi harus ditutup tepat saat layar mati (30 detik), bukan 35 detik
        assertEquals(30_000L, result[tiktokPkg])
    }

    // 5. Layar dibuka kembali setelah lockscreen (tidak otomatis membuka sesi tanpa event Activity)
    @Test
    fun scenario5_screenUnlockedAfterLockscreenWaitsForActivityResume() {
        val start = 1_000_000L
        val end = 2_000_000L
        val events = listOf(
            // Sesi 1: 20 detik sebelum keyguard muncul
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, start + 10_000L, "MainActivity"),
            UsageEventLite("android", EVENT_KEYGUARD_SHOWN, start + 30_000L),
            // Layar menyala di +50_000L tapi pengguna masih di lockscreen selama 10 detik
            UsageEventLite("android", EVENT_SCREEN_INTERACTIVE, start + 50_000L),
            UsageEventLite("android", EVENT_KEYGUARD_HIDDEN, start + 60_000L),
            // Baru pada +65_000L TikTok benar-benar resume kembali selama 25 detik
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, start + 65_000L, "MainActivity"),
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_PAUSED, start + 90_000L, "MainActivity"),
        )

        val result = computeForegroundMillis(
            events = events,
            startTime = start,
            endTime = end,
            ownPackage = ownPkg,
            supportsScreenEvents = true,
        )

        // Total = 20_000L (sesi 1) + 25_000L (sesi 2) = 45_000L
        // Waktu di lockscreen (50_000..65_000) TIDAK ikut terhitung
        assertEquals(45_000L, result[tiktokPkg])
    }

    // 6. Satu aplikasi memiliki beberapa Activity (transisi internal tidak menduplikasi atau memutus sesi)
    @Test
    fun scenario6_singleAppWithMultipleActivitiesTransition() {
        val start = 1_000_000L
        val end = 2_000_000L
        // Urutan umum di Android ketika berpindah Activity dalam 1 aplikasi:
        // Activity B RESUMED pada t=30s sebelum Activity A PAUSED pada t=31s (overlapping lifecycle)
        val events = listOf(
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, start + 10_000L, "MainActivity"),
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, start + 30_000L, "ProfileActivity"),
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_PAUSED, start + 31_000L, "MainActivity"),
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_PAUSED, start + 70_000L, "ProfileActivity"),
        )

        val result = computeForegroundMillis(
            events = events,
            startTime = start,
            endTime = end,
            ownPackage = ownPkg,
            supportsScreenEvents = true,
        )

        // Total pemakaian TikTok dari t=10s s.d. t=70s harus tepat 60 detik (tidak terputus di t=31s dan tidak double count)
        assertEquals(60_000L, result[tiktokPkg])
    }

    // 7. Event dengan timestamp sama & deduplikasi event identik
    @Test
    fun scenario7_eventsWithIdenticalTimestampsAndDuplicates() {
        val start = 1_000_000L
        val end = 2_000_000L
        val events = listOf(
            // Event duplikat identik pada timestamp yang sama
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, start + 10_000L, "MainActivity"),
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, start + 10_000L, "MainActivity"),
            // Pada t=50s, TikTok PAUSED dan Chrome RESUMED pada milidetik yang sama persis
            UsageEventLite(chromePkg, UsageEvents.Event.ACTIVITY_RESUMED, start + 50_000L, "ChromeActivity"),
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_PAUSED, start + 50_000L, "MainActivity"),
            UsageEventLite(chromePkg, UsageEvents.Event.ACTIVITY_PAUSED, start + 80_000L, "ChromeActivity"),
        )

        val result = computeForegroundMillis(
            events = events,
            startTime = start,
            endTime = end,
            ownPackage = ownPkg,
            supportsScreenEvents = true,
        )

        assertEquals(40_000L, result[tiktokPkg])
        assertEquals(30_000L, result[chromePkg])
    }

    // 8. Aplikasi masih aktif ketika query berakhir
    @Test
    fun scenario8_appStillActiveWhenQueryEnds() {
        val start = 1_000_000L
        val end = 1_500_000L
        val events = listOf(
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, start + 200_000L, "MainActivity"),
        )

        val result = computeForegroundMillis(
            events = events,
            startTime = start,
            endTime = end,
            ownPackage = ownPkg,
            supportsScreenEvents = true,
        )

        // Ditutup tepat pada endTime (1_500_000 - 1_200_000 = 300_000)
        assertEquals(300_000L, result[tiktokPkg])
    }

    // 9. Tidak ada event penggunaan
    @Test
    fun scenario9_noUsageEventsReturnsEmptyMap() {
        val result = computeForegroundMillis(
            events = emptyList(),
            startTime = 1_000_000L,
            endTime = 2_000_000L,
            ownPackage = ownPkg,
            supportsScreenEvents = true,
        )

        assertTrue(result.isEmpty())
    }

    // 10. Query untuk kemarin dan hari ini tidak saling mencampur
    @Test
    fun scenario10_yesterdayAndTodayQueriesDoNotMix() {
        val yesterdayStart = 10_000_000L
        val todayStart = yesterdayStart + 86_400_000L
        val todayNow = todayStart + 10_000_000L

        val allEvents = listOf(
            // Sesi kemarin: 2 jam TikTok
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, yesterdayStart + 1_000_000L, "MainActivity"),
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_PAUSED, yesterdayStart + 1_000_000L + 120L * 60_000L, "MainActivity"),
            // Sesi hari ini: 30 menit TikTok, 15 menit Chrome
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, todayStart + 500_000L, "MainActivity"),
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_PAUSED, todayStart + 500_000L + 30L * 60_000L, "MainActivity"),
            UsageEventLite(chromePkg, UsageEvents.Event.ACTIVITY_RESUMED, todayStart + 3_000_000L, "ChromeActivity"),
            UsageEventLite(chromePkg, UsageEvents.Event.ACTIVITY_PAUSED, todayStart + 3_000_000L + 15L * 60_000L, "ChromeActivity"),
        )

        val todayMap = computeForegroundMillis(
            events = allEvents,
            startTime = todayStart,
            endTime = todayNow,
            ownPackage = ownPkg,
            supportsScreenEvents = true,
        )

        val yesterdayMap = computeForegroundMillis(
            events = allEvents,
            startTime = yesterdayStart,
            endTime = todayStart,
            ownPackage = ownPkg,
            supportsScreenEvents = true,
        )

        // Hari ini hanya berisi 30m TikTok & 15m Chrome (sama sekali tidak membawa 2 jam kemarin)
        assertEquals(30L * 60_000L, todayMap[tiktokPkg])
        assertEquals(15L * 60_000L, todayMap[chromePkg])

        // Kemarin hanya berisi 2 jam TikTok & tidak ada Chrome hari ini
        assertEquals(120L * 60_000L, yesterdayMap[tiktokPkg])
        assertFalse(yesterdayMap.containsKey(chromePkg))
    }

    // 11. Skenario ketika JedaIn dibuka saat aplikasi lain sedang aktif (menutup sesi aplikasi sebelumnya)
    @Test
    fun scenario11_openingJedaInClosesPreviousAppAndExcludesJedaInDuration() {
        val start = 1_000_000L
        val end = 2_000_000L
        val events = listOf(
            // TikTok dibuka pada t=10s
            UsageEventLite(tiktokPkg, UsageEvents.Event.ACTIVITY_RESUMED, start + 10_000L, "MainActivity"),
            // Pada t=40s pengguna berpindah ke JedaIn (tanpa event PAUSED eksplisit dari TikTok)
            UsageEventLite(ownPkg, UsageEvents.Event.ACTIVITY_RESUMED, start + 40_000L, "MainActivity"),
            UsageEventLite(ownPkg, UsageEvents.Event.ACTIVITY_PAUSED, start + 90_000L, "MainActivity"),
        )

        val result = computeForegroundMillis(
            events = events,
            startTime = start,
            endTime = end,
            ownPackage = ownPkg,
            supportsScreenEvents = true,
        )

        // TikTok harus tertutup pada t=40s (durasi 30 detik), dan JedaIn tidak masuk ke map
        assertEquals(30_000L, result[tiktokPkg])
        assertFalse(result.containsKey(ownPkg))
    }
}
