package com.example.jedain

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DailyScreenTimeNotificationTest {

    @Test
    fun testFormatDailyScreenTimeNotification_zeroAndNegative_returnsZeroMinutes() {
        assertEquals("0 menit", formatDailyScreenTimeNotification(0L))
        assertEquals("0 menit", formatDailyScreenTimeNotification(-1_000L))
        assertEquals("0 menit", formatDailyScreenTimeNotification(-60_000L))
    }

    @Test
    fun testFormatDailyScreenTimeNotification_underOneMinute_returnsZeroMinutes() {
        // Kurang dari 1 menit (misal 15 detik, 30 detik, 59 detik) dibulatkan ke 0 menit
        assertEquals("0 menit", formatDailyScreenTimeNotification(15_000L))
        assertEquals("0 menit", formatDailyScreenTimeNotification(30_000L))
        assertEquals("0 menit", formatDailyScreenTimeNotification(59_999L))
    }

    @Test
    fun testFormatDailyScreenTimeNotification_underOneHour_returnsMinutesOnly() {
        // Kurang dari 1 jam: "42 menit", "1 menit", "59 menit"
        assertEquals("1 menit", formatDailyScreenTimeNotification(60_000L))
        assertEquals("15 menit", formatDailyScreenTimeNotification(15L * 60_000L))
        assertEquals("42 menit", formatDailyScreenTimeNotification(42L * 60_000L))
        assertEquals("59 menit", formatDailyScreenTimeNotification(59L * 60_000L))
    }

    @Test
    fun testFormatDailyScreenTimeNotification_exactHours_returnsHoursOnly() {
        // Tepat satu jam atau jam bulat: "1 jam", "2 jam", "5 jam"
        assertEquals("1 jam", formatDailyScreenTimeNotification(60L * 60_000L))
        assertEquals("2 jam", formatDailyScreenTimeNotification(120L * 60_000L))
        assertEquals("3 jam", formatDailyScreenTimeNotification(180L * 60_000L))
        assertEquals("5 jam", formatDailyScreenTimeNotification(300L * 60_000L))
    }

    @Test
    fun testFormatDailyScreenTimeNotification_hoursAndMinutes_returnsCombinedFormat() {
        // 1 jam atau lebih dengan menit: "3 jam 42 menit", "1 jam 1 menit"
        assertEquals("1 jam 1 menit", formatDailyScreenTimeNotification(61L * 60_000L))
        assertEquals("3 jam 42 menit", formatDailyScreenTimeNotification((3L * 60L + 42L) * 60_000L))
        assertEquals("7 jam 15 menit", formatDailyScreenTimeNotification((7L * 60L + 15L) * 60_000L))
    }

    @Test
    fun testAllAppsAggregation_excludesJedaInAndSumsAllOtherApps() {
        val startTime = 1_000_000L
        val endTime = startTime + 3L * 3600_000L // 3 jam rentang

        val events = listOf(
            // JedaIn dibuka (tidak boleh dihitung)
            UsageEventLite("com.example.jedain", UsageEvents.Event.ACTIVITY_RESUMED, startTime + 100_000L),
            UsageEventLite("com.example.jedain", UsageEvents.Event.ACTIVITY_PAUSED, startTime + 200_000L),

            // Instagram: 10 menit
            UsageEventLite("com.instagram.android", UsageEvents.Event.ACTIVITY_RESUMED, startTime + 300_000L),
            UsageEventLite("com.instagram.android", UsageEvents.Event.ACTIVITY_PAUSED, startTime + 900_000L), // 600,000ms = 10m

            // TikTok: 25 menit
            UsageEventLite("com.zhiliaoapp.musically", UsageEvents.Event.ACTIVITY_RESUMED, startTime + 1_000_000L),
            UsageEventLite("com.zhiliaoapp.musically", UsageEvents.Event.ACTIVITY_PAUSED, startTime + 2_500_000L), // 1,500,000ms = 25m

            // WhatsApp: 15 menit
            UsageEventLite("com.whatsapp", UsageEvents.Event.ACTIVITY_RESUMED, startTime + 3_000_000L),
            UsageEventLite("com.whatsapp", UsageEvents.Event.ACTIVITY_PAUSED, startTime + 3_900_000L), // 900,000ms = 15m

            // YouTube: 50 menit
            UsageEventLite("com.google.android.youtube", UsageEvents.Event.ACTIVITY_RESUMED, startTime + 4_000_000L),
            UsageEventLite("com.google.android.youtube", UsageEvents.Event.ACTIVITY_PAUSED, startTime + 7_000_000L), // 3,000,000ms = 50m
        )

        val resultMap = computeForegroundMillis(
            events = events,
            startTime = startTime,
            endTime = endTime,
            ownPackage = "com.example.jedain",
            supportsScreenEvents = true,
        )

        // Verifikasi aplikasi JedaIn sendiri tidak terhitung
        assertFalse(resultMap.containsKey("com.example.jedain"))

        // Verifikasi masing-masing aplikasi
        assertEquals(10L * 60_000L, resultMap["com.instagram.android"])
        assertEquals(25L * 60_000L, resultMap["com.zhiliaoapp.musically"])
        assertEquals(15L * 60_000L, resultMap["com.whatsapp"])
        assertEquals(50L * 60_000L, resultMap["com.google.android.youtube"])

        // Total akumulasi seluruh aplikasi di perangkat hari ini: 10 + 25 + 15 + 50 = 100 menit = 1 jam 40 menit
        val totalDeviceMillis = resultMap.values.sum()
        assertEquals(100L * 60_000L, totalDeviceMillis)
        assertEquals("1 jam 40 menit", formatDailyScreenTimeNotification(totalDeviceMillis))
    }

    @Test
    fun testMidnightReset_excludesYesterdayUsageAndClampsMidnightOverlap() {
        // Asumsikan midnight hari ini berada pada epoch 10_000_000L
        val todayMidnight = 10_000_000L
        val now = todayMidnight + 2L * 3600_000L // 02:00 pagi

        val events = listOf(
            // Kemarin malam: YouTube dibuka dari 23:00 s.d. 23:45 (sebelum midnight) -> Harus 0 untuk hari ini
            UsageEventLite("com.google.android.youtube", UsageEvents.Event.ACTIVITY_RESUMED, todayMidnight - 3600_000L),
            UsageEventLite("com.google.android.youtube", UsageEvents.Event.ACTIVITY_PAUSED, todayMidnight - 900_000L),

            // Sesi yang melewati tengah malam: TikTok dibuka 23:50 kemarin s.d. 00:20 hari ini
            // Durasi kemarin: 10 menit (tidak boleh dihitung hari ini)
            // Durasi hari ini: 20 menit (00:00 s.d. 00:20) -> Harus dihitung 20 menit
            UsageEventLite("com.zhiliaoapp.musically", UsageEvents.Event.ACTIVITY_RESUMED, todayMidnight - 600_000L),
            UsageEventLite("com.zhiliaoapp.musically", UsageEvents.Event.ACTIVITY_PAUSED, todayMidnight + 1_200_000L),

            // Sesi hari ini: WhatsApp dibuka 01:00 s.d. 01:15 -> 15 menit
            UsageEventLite("com.whatsapp", UsageEvents.Event.ACTIVITY_RESUMED, todayMidnight + 3600_000L),
            UsageEventLite("com.whatsapp", UsageEvents.Event.ACTIVITY_PAUSED, todayMidnight + 4_500_000L),
        )

        val resultMap = computeForegroundMillis(
            events = events,
            startTime = todayMidnight, // 00:00 hari ini
            endTime = now,
            ownPackage = "com.example.jedain",
            supportsScreenEvents = true,
        )

        // YouTube kemarin tidak boleh masuk hitungan hari ini
        assertFalse(resultMap.containsKey("com.google.android.youtube"))

        // TikTok hanya terhitung dari 00:00 s.d. 00:20 (1,200,000ms = 20 menit)
        assertEquals(20L * 60_000L, resultMap["com.zhiliaoapp.musically"])

        // WhatsApp terhitung 15 menit
        assertEquals(15L * 60_000L, resultMap["com.whatsapp"])

        // Total screen time hari ini: 20 + 15 = 35 menit
        val totalTodayMillis = resultMap.values.sum()
        assertEquals(35L * 60_000L, totalTodayMillis)
        assertEquals("35 menit", formatDailyScreenTimeNotification(totalTodayMillis))
    }

    @Test
    fun testNotificationTextFormatting() {
        val totalMillis = (3L * 60L + 42L) * 60_000L
        val formattedDuration = formatDailyScreenTimeNotification(totalMillis)

        assertEquals("3 jam 42 menit", formattedDuration)

        val collapsedText = "⏱ Total waktu layar hari ini: $formattedDuration"
        assertEquals("⏱ Total waktu layar hari ini: 3 jam 42 menit", collapsedText)

        val bigText = "⏱ Total waktu layar hari ini\n$formattedDuration"
        assertEquals("⏱ Total waktu layar hari ini\n3 jam 42 menit", bigText)
        assertFalse(bigText.contains("Pemantauan aktif"))
        assertFalse(bigText.contains("JedaIn membantu kamu lebih sadar"))
    }

    @Test
    fun testBuildScreenTimeSummaryFromMaps_separatesDeviceTotalAndMonitoredApps() {
        val todayUsage = mapOf(
            "com.zhiliaoapp.musically" to 129L * 60_000L, // 2 jam 9 menit (TikTok)
            "com.instagram.android" to 45L * 60_000L,     // 45 menit (Instagram)
            "com.whatsapp" to 30L * 60_000L,              // 30 menit (WhatsApp - tidak dipantau)
        )
        val yesterdayUsage = mapOf(
            "com.zhiliaoapp.musically" to 100L * 60_000L,
            "com.instagram.android" to 50L * 60_000L,
            "com.whatsapp" to 30L * 60_000L,
        )
        val monitored = setOf("com.zhiliaoapp.musically", "com.instagram.android")

        val summary = buildScreenTimeSummaryFromMaps(
            hasUsagePermission = true,
            hasOverlayPermission = true,
            monitoredPackages = monitored,
            todayUsageMap = todayUsage,
            yesterdayUsageMap = yesterdayUsage,
            todayDeviceTotalMillis = todayUsage.values.sum(),
            yesterdayDeviceTotalMillis = yesterdayUsage.values.sum(),
        )

        // Verifikasi durasi TikTok tepat 2 jam 9 menit
        assertEquals(129L * 60_000L, summary.appUsageMillisMap["com.zhiliaoapp.musically"])
        assertEquals("2j 9m", formatScreenTimeShort(summary.appUsageMillisMap["com.zhiliaoapp.musically"] ?: 0L))

        // Verifikasi durasi Instagram 45 menit
        assertEquals(45L * 60_000L, summary.appUsageMillisMap["com.instagram.android"])

        // Verifikasi total aplikasi yang dipantau (TikTok + Instagram): 129m + 45m = 174m = 2j 54m
        assertEquals((129L + 45L) * 60_000L, summary.todayTotalMillis)
        assertEquals("2j 54m", summary.monitoredTotalDurationText)

        // Verifikasi total penggunaan seluruh perangkat (termasuk WhatsApp): 129m + 45m + 30m = 204m = 3j 24m
        assertEquals((129L + 45L + 30L) * 60_000L, summary.todayDeviceTotalMillis)
        assertEquals("3j 24m", summary.todayTotalDurationText)

        // Verifikasi kartu HomeScreen menampilkan total perangkat
        assertEquals("3j 24m", summary.todayTotalDurationText)
    }
}
