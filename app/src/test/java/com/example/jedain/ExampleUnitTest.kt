package com.example.jedain

import androidx.compose.ui.graphics.toArgb
import com.example.jedain.ui.theme.BorderDarkGray
import com.example.jedain.ui.theme.BorderSubtleGray
import com.example.jedain.ui.theme.DurationRedDark
import com.example.jedain.ui.theme.DurationRedLight
import com.example.jedain.ui.theme.DurationYellowDark
import com.example.jedain.ui.theme.DurationYellowLight
import com.example.jedain.ui.theme.LightJedaInColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun validateDailyTargetMinutes_enforcesMin15AndMax240With15MinStep() {
        assertEquals(15, validateDailyTargetMinutes(0))
        assertEquals(15, validateDailyTargetMinutes(5))
        assertEquals(15, validateDailyTargetMinutes(15))
        assertEquals(30, validateDailyTargetMinutes(30))
        assertEquals(90, validateDailyTargetMinutes(90))
        assertEquals(240, validateDailyTargetMinutes(240))
        assertEquals(240, validateDailyTargetMinutes(300)) // 5 jam -> clamped to 4 jam
        assertEquals(240, validateDailyTargetMinutes(360)) // 6 jam -> clamped to 4 jam
    }

    @Test
    fun formatDailyTargetDuration_formatsHoursAndMinutesInIndonesian() {
        assertEquals("15 menit", formatDailyTargetDuration(15))
        assertEquals("30 menit", formatDailyTargetDuration(30))
        assertEquals("45 menit", formatDailyTargetDuration(45))
        assertEquals("1 jam", formatDailyTargetDuration(60))
        assertEquals("1 jam 30 menit", formatDailyTargetDuration(90))
        assertEquals("2 jam", formatDailyTargetDuration(120))
        assertEquals("3 jam", formatDailyTargetDuration(180))
        assertEquals("4 jam", formatDailyTargetDuration(240))
    }

    @Test
    fun dailyTargetPresetOptions_containsExactFourPresetsUpTo3Hours() {
        assertEquals(listOf(30, 60, 120, 180), DailyTargetPresetOptions.map { it.minutes })
        assertEquals(
            listOf("30 menit", "1 jam", "2 jam", "3 jam"),
            DailyTargetPresetOptions.map { it.title },
        )
        assertEquals(
            listOf(
                "Sangat terkontrol",
                "Terkendali",
                "Perlu diperhatikan",
                "Cukup tinggi",
            ),
            DailyTargetPresetOptions.map { it.category },
        )
    }

    @Test
    fun resolveDailyTargetFeedback_returnsExpectedColorsCategoriesAndDescriptions() {
        val colors = LightJedaInColors

        val feedback30m = resolveDailyTargetFeedback(30, isCustom = false, colors = colors)
        assertEquals("Sangat terkontrol", feedback30m.category)
        assertEquals("Target singkat untuk menjaga waktu hiburan tetap terbatas.", feedback30m.description)
        assertEquals(colors.durationGreen, feedback30m.accentColor)

        val feedback1h = resolveDailyTargetFeedback(60, isCustom = false, colors = colors)
        assertEquals("Terkendali", feedback1h.category)
        assertEquals("Nikmati hiburan tanpa terlalu mengurangi waktu untuk kegiatan lain.", feedback1h.description)
        assertEquals(colors.durationGreen, feedback1h.accentColor)

        val feedback2h = resolveDailyTargetFeedback(120, isCustom = false, colors = colors)
        assertEquals("Perlu diperhatikan", feedback2h.category)
        assertEquals("Jaga agar scrolling tidak mengambil alih waktu produktif.", feedback2h.description)
        assertEquals(colors.durationYellow, feedback2h.accentColor)

        val feedback3h = resolveDailyTargetFeedback(180, isCustom = false, colors = colors)
        assertEquals("Cukup tinggi", feedback3h.category)
        assertEquals("Durasi ini cukup tinggi. Pertimbangkan menguranginya secara bertahap.", feedback3h.description)
        assertEquals(colors.durationOrange, feedback3h.accentColor)

        val feedback4h = resolveDailyTargetFeedback(240, isCustom = false, colors = colors)
        assertEquals("Target tinggi", feedback4h.category)
        assertEquals("Empat jam sehari merupakan target yang tinggi. Pertimbangkan durasi lebih rendah.", feedback4h.description)
        assertEquals(colors.durationRed, feedback4h.accentColor)

        val feedbackCustom45m = resolveDailyTargetFeedback(45, isCustom = true, colors = colors)
        assertEquals("45 menit", feedbackCustom45m.durationText)
        assertEquals("Terkendali", feedbackCustom45m.category)
        assertEquals(colors.durationGreen, feedbackCustom45m.accentColor)
        assertTrue(feedbackCustom45m.isCustom)

        val feedbackCustom90m = resolveDailyTargetFeedback(90, isCustom = true, colors = colors)
        assertEquals("1 jam 30 menit", feedbackCustom90m.durationText)
        assertEquals("Perlu diperhatikan", feedbackCustom90m.category)
        assertEquals(colors.durationYellow, feedbackCustom90m.accentColor)
        assertTrue(feedbackCustom90m.isCustom)

        val feedbackCustom150m = resolveDailyTargetFeedback(150, isCustom = true, colors = colors)
        assertEquals("2 jam 30 menit", feedbackCustom150m.durationText)
        assertEquals("Cukup tinggi", feedbackCustom150m.category)
        assertEquals(colors.durationOrange, feedbackCustom150m.accentColor)
        assertTrue(feedbackCustom150m.isCustom)

        val feedbackCustom240m = resolveDailyTargetFeedback(240, isCustom = true, colors = colors)
        assertEquals("4 jam", feedbackCustom240m.durationText)
        assertEquals("Target tinggi", feedbackCustom240m.category)
        assertEquals(colors.durationRed, feedbackCustom240m.accentColor)
        assertTrue(feedbackCustom240m.isCustom)
    }

    @Test
    fun targetSettingsState_preservesCustomTargetAndClampsMaxDuration() {
        val customState = TargetSettingsState(
            dailyTargetMinutes = 90,
            isCustomDailyTarget = true,
            customDailyTargetMinutes = 90,
        )
        assertTrue(customState.isCustomDailyTarget)
        assertEquals(90, customState.validatedDailyTargetMinutes)
        assertEquals(90, customState.validatedCustomDailyTargetMinutes)

        val overLimitState = TargetSettingsState(
            dailyTargetMinutes = 360,
            isCustomDailyTarget = false,
        )
        assertFalse(overLimitState.isCustomDailyTarget)
        assertEquals(240, overLimitState.validatedDailyTargetMinutes)
        assertEquals(4, overLimitState.dailyTargetHours)
    }

    @Test
    fun isRecommendedPackageOrCategory_detectsCategoriesAndPopularKeywords() {
        assertTrue(isRecommendedPackageOrCategory("com.zhiliaoapp.musically", -1))
        assertTrue(isRecommendedPackageOrCategory("com.instagram.android", -1))
        assertTrue(isRecommendedPackageOrCategory("com.google.android.youtube", -1))
        assertTrue(isRecommendedPackageOrCategory("com.shopee.id", -1))
        assertTrue(isRecommendedPackageOrCategory("com.example.socialapp", android.content.pm.ApplicationInfo.CATEGORY_SOCIAL))
        assertTrue(isRecommendedPackageOrCategory("com.example.videoapp", android.content.pm.ApplicationInfo.CATEGORY_VIDEO))
        assertTrue(isRecommendedPackageOrCategory("com.example.gameapp", android.content.pm.ApplicationInfo.CATEGORY_GAME))
        assertFalse(isRecommendedPackageOrCategory("com.android.calculator2", -1))
    }

    @Test
    fun formatScreenTimeShort_andComparison_formatCorrectlyInIndonesian() {
        assertEquals("0m", formatScreenTimeShort(0L))
        assertEquals("42m", formatScreenTimeShort(42L * 60_000L))
        assertEquals("1j 15m", formatScreenTimeShort(75L * 60_000L))
        assertEquals("2j", formatScreenTimeShort(120L * 60_000L))

        val noPermission = formatScreenTimeComparison(
            hasUsagePermission = false,
            todayMillis = 60L * 60_000L,
            yesterdayMillis = 30L * 60_000L,
        )
        assertEquals("Aktifkan izin penggunaan untuk melihat data asli", noPermission.first)
        assertTrue(noPermission.second)

        val lessToday = formatScreenTimeComparison(
            hasUsagePermission = true,
            todayMillis = 30L * 60_000L,
            yesterdayMillis = 75L * 60_000L,
        )
        assertEquals("45m lebih sedikit dari kemarin", lessToday.first)
        assertTrue(lessToday.second)

        val moreToday = formatScreenTimeComparison(
            hasUsagePermission = true,
            todayMillis = 120L * 60_000L,
            yesterdayMillis = 45L * 60_000L,
        )
        assertEquals("1j 15m lebih banyak dari kemarin", moreToday.first)
        assertFalse(moreToday.second)

        val sameToday = formatScreenTimeComparison(
            hasUsagePermission = true,
            todayMillis = 60L * 60_000L,
            yesterdayMillis = 60L * 60_000L,
        )
        assertEquals("Sama seperti kemarin pada waktu ini", sameToday.first)
        assertTrue(sameToday.second)

        val emptyMonitoredSummary = buildScreenTimeSummaryFromMaps(
            hasUsagePermission = true,
            hasOverlayPermission = true,
            monitoredPackages = emptySet(),
            todayUsageMap = mapOf("com.instagram.android" to 60L * 60_000L),
            yesterdayUsageMap = mapOf("com.instagram.android" to 30L * 60_000L),
        )
        assertEquals("0m", emptyMonitoredSummary.todayTotalDurationText)
        assertEquals("Pilih aplikasi yang ingin dipantau", emptyMonitoredSummary.comparisonChangeText)
        assertTrue(emptyMonitoredSummary.isTrendingDown)
    }

    @Test
    fun computeForegroundMillis_singleNormalSession() {
        val events = listOf(
            UsageEventLite("com.instagram.android", android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED, 1_000L),
            UsageEventLite("com.instagram.android", android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED, 4_000L),
        )
        val result = computeForegroundMillis(
            events = events,
            startTime = 0L,
            endTime = 10_000L,
            ownPackage = "com.example.jedain",
            supportsScreenEvents = true,
        )
        assertEquals(3_000L, result["com.instagram.android"])
    }

    @Test
    fun computeForegroundMillis_twoAppsAlternating() {
        val events = listOf(
            UsageEventLite("com.instagram.android", android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED, 1_000L),
            UsageEventLite("com.instagram.android", android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED, 3_000L),
            UsageEventLite("com.zhiliaoapp.musically", android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED, 4_000L),
            UsageEventLite("com.zhiliaoapp.musically", android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED, 7_000L),
        )
        val result = computeForegroundMillis(
            events = events,
            startTime = 0L,
            endTime = 10_000L,
            ownPackage = "com.example.jedain",
            supportsScreenEvents = true,
        )
        assertEquals(2_000L, result["com.instagram.android"])
        assertEquals(3_000L, result["com.zhiliaoapp.musically"])
    }

    @Test
    fun computeForegroundMillis_missingPausedThenAnotherAppResumed() {
        val events = listOf(
            UsageEventLite("com.instagram.android", android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED, 1_000L),
            UsageEventLite("com.zhiliaoapp.musically", android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED, 4_000L),
            UsageEventLite("com.zhiliaoapp.musically", android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED, 6_000L),
        )
        val result = computeForegroundMillis(
            events = events,
            startTime = 0L,
            endTime = 10_000L,
            ownPackage = "com.example.jedain",
            supportsScreenEvents = true,
        )
        assertEquals(3_000L, result["com.instagram.android"])
        assertEquals(2_000L, result["com.zhiliaoapp.musically"])
    }

    @Test
    fun computeForegroundMillis_screenOffOrKeyguardClosesActiveSession() {
        val events = listOf(
            UsageEventLite("com.instagram.android", android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED, 1_000L),
            UsageEventLite("android", 16, 3_500L), // SCREEN_NON_INTERACTIVE = 16
            UsageEventLite("com.instagram.android", android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED, 5_000L),
            UsageEventLite("com.zhiliaoapp.musically", android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED, 6_000L),
            UsageEventLite("android", 17, 7_500L), // KEYGUARD_SHOWN = 17
        )
        val result = computeForegroundMillis(
            events = events,
            startTime = 0L,
            endTime = 10_000L,
            ownPackage = "com.example.jedain",
            supportsScreenEvents = true,
        )
        assertEquals(2_500L, result["com.instagram.android"])
        assertEquals(1_500L, result["com.zhiliaoapp.musically"])
    }

    @Test
    fun computeForegroundMillis_openSessionAtEndTimeIsClosedAtEndTime() {
        val events = listOf(
            UsageEventLite("com.instagram.android", android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED, 2_000L),
        )
        val result = computeForegroundMillis(
            events = events,
            startTime = 0L,
            endTime = 5_000L,
            ownPackage = "com.example.jedain",
            supportsScreenEvents = true,
        )
        assertEquals(3_000L, result["com.instagram.android"])
    }

    @Test
    fun computeForegroundMillis_ignoresOwnJedaInPackage() {
        val events = listOf(
            UsageEventLite("com.example.jedain", android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED, 1_000L),
            UsageEventLite("com.example.jedain", android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED, 5_000L),
            UsageEventLite("com.instagram.android", android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED, 6_000L),
            UsageEventLite("com.instagram.android", android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED, 8_000L),
        )
        val result = computeForegroundMillis(
            events = events,
            startTime = 0L,
            endTime = 10_000L,
            ownPackage = "com.example.jedain",
            supportsScreenEvents = true,
        )
        assertFalse(result.containsKey("com.example.jedain"))
        assertEquals(2_000L, result["com.instagram.android"])
    }

    @Test
    fun computeForegroundMillis_crossMidnightSessionClampedToStartTime() {
        val startTime = 100_000_000L
        val endTime = startTime + 60L * 60_000L
        val tenMinutesMillis = 10L * 60_000L
        val fiveMinutesMillis = 5L * 60_000L

        val events = listOf(
            UsageEventLite(
                packageName = "com.instagram.android",
                eventType = android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                timeStamp = startTime - tenMinutesMillis,
            ),
            UsageEventLite(
                packageName = "com.instagram.android",
                eventType = android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                timeStamp = startTime + fiveMinutesMillis,
            ),
        )
        val result = computeForegroundMillis(
            events = events,
            startTime = startTime,
            endTime = endTime,
            ownPackage = "com.example.jedain",
            supportsScreenEvents = true,
        )
        assertEquals(fiveMinutesMillis, result["com.instagram.android"])
    }

    @Test
    fun resolveBubbleBorderColor_followsDailyTargetTiers() {
        // Di bawah 80% target: warna netral tegas (0xFFBDBDBD terang, 0xFF4A4A4A gelap)
        val neutralLight = FloatingBubbleService.resolveBubbleBorderColor(0.0, isDark = false)
        val neutralLight79 = FloatingBubbleService.resolveBubbleBorderColor(0.79, isDark = false)
        assertEquals(0xFFBDBDBD.toInt(), neutralLight)
        assertEquals(0xFFBDBDBD.toInt(), neutralLight79)

        val neutralDark = FloatingBubbleService.resolveBubbleBorderColor(0.5, isDark = true)
        assertEquals(0xFF4A4A4A.toInt(), neutralDark)

        // 80% sampai di bawah 100%: kuning/oranye
        val warningLight80 = FloatingBubbleService.resolveBubbleBorderColor(0.80, isDark = false)
        val warningLight99 = FloatingBubbleService.resolveBubbleBorderColor(0.99, isDark = false)
        assertEquals(DurationYellowLight.toArgb(), warningLight80)
        assertEquals(DurationYellowLight.toArgb(), warningLight99)

        val warningDark85 = FloatingBubbleService.resolveBubbleBorderColor(0.85, isDark = true)
        assertEquals(DurationYellowDark.toArgb(), warningDark85)

        // 100% atau lebih: merah
        val alertLight100 = FloatingBubbleService.resolveBubbleBorderColor(1.0, isDark = false)
        val alertLight150 = FloatingBubbleService.resolveBubbleBorderColor(1.5, isDark = false)
        assertEquals(DurationRedLight.toArgb(), alertLight100)
        assertEquals(DurationRedLight.toArgb(), alertLight150)

        val alertDark100 = FloatingBubbleService.resolveBubbleBorderColor(1.0, isDark = true)
        assertEquals(DurationRedDark.toArgb(), alertDark100)
    }

    @Test
    fun floatingBubbleController_constantsAndKeysMatchExpectation() {
        assertEquals("jedain_prefs", FloatingBubbleController.PREFS_NAME)
        assertEquals("floating_bubble_enabled", FloatingBubbleController.KEY_FLOATING_BUBBLE_ENABLED)
        assertEquals("bubble_y", FloatingBubbleController.KEY_BUBBLE_Y)
    }

    @Test
    fun formatTimerDigital_formatsDigitalTimerCorrectly() {
        // 8 detik -> 08s
        assertEquals("08s", FloatingBubbleService.formatTimerDigital(8_000L))
        // 45 detik -> 45s
        assertEquals("45s", FloatingBubbleService.formatTimerDigital(45_000L))
        // 1 menit 5 detik -> 01:05
        assertEquals("01:05", FloatingBubbleService.formatTimerDigital(65_000L))
        // 5 menit 30 detik -> 05:30
        assertEquals("05:30", FloatingBubbleService.formatTimerDigital(330_000L))
        // 24 menit 8 detik -> 24:08
        assertEquals("24:08", FloatingBubbleService.formatTimerDigital(1_448_000L))
        // 1 jam 5 menit -> 1:05:00
        assertEquals("1:05:00", FloatingBubbleService.formatTimerDigital(3_900_000L))
        // 0 detik -> 00s
        assertEquals("00s", FloatingBubbleService.formatTimerDigital(0L))
        // Negatif -> 00s
        assertEquals("00s", FloatingBubbleService.formatTimerDigital(-500L))
    }

    @Test
    fun breakSessionManager_extensionMinutesTierMapping() {
        // 1 min custom session (for quick emulator testing) -> +1 min
        assertEquals(1, BreakSessionManager.getExtensionMinutes(1))
        // 2 min custom session -> +1 min
        assertEquals(1, BreakSessionManager.getExtensionMinutes(2))
        // 5 min session -> +2 min
        assertEquals(2, BreakSessionManager.getExtensionMinutes(5))
        // 10 min session -> +3 min
        assertEquals(3, BreakSessionManager.getExtensionMinutes(10))
        // 15 min session -> +5 min
        assertEquals(5, BreakSessionManager.getExtensionMinutes(15))
        // 20 min session -> +5 min
        assertEquals(5, BreakSessionManager.getExtensionMinutes(20))
        // 30 min session -> +5 min
        assertEquals(5, BreakSessionManager.getExtensionMinutes(30))
    }

    @Test
    fun breakSessionManager_confirmationTemplateFormatting() {
        assertEquals(
            "Saya sadar dan memilih melanjutkan selama 2 menit.",
            BreakSessionManager.getConfirmationTemplate(2),
        )
        assertEquals(
            "Saya sadar dan memilih melanjutkan selama 3 menit.",
            BreakSessionManager.getConfirmationTemplate(3),
        )
        assertEquals(
            "Saya sadar dan memilih melanjutkan selama 5 menit.",
            BreakSessionManager.getConfirmationTemplate(5),
        )
    }

    @Test
    fun breakSessionManager_validateConfirmationInput_flexibleSpacingAndCasing() {
        val template = BreakSessionManager.getConfirmationTemplate(3)

        // Exact match
        assertTrue(BreakSessionManager.validateConfirmationInput(
            "Saya sadar dan memilih melanjutkan selama 3 menit.",
            template,
        ))

        // Case insensitivity
        assertTrue(BreakSessionManager.validateConfirmationInput(
            "SAYA SADAR DAN MEMILIH MELANJUTKAN SELAMA 3 MENIT.",
            template,
        ))
        assertTrue(BreakSessionManager.validateConfirmationInput(
            "saya sadar dan memilih melanjutkan selama 3 menit.",
            template,
        ))

        // Flexible spacing: leading, trailing, and multiple spaces between words
        assertTrue(BreakSessionManager.validateConfirmationInput(
            "   Saya    sadar   dan   memilih   melanjutkan   selama   3   menit.   ",
            template,
        ))

        // Typo / wrong word
        assertFalse(BreakSessionManager.validateConfirmationInput(
            "Saya sadar memilih melanjutkan selama 3 menit.",
            template,
        ))
        assertFalse(BreakSessionManager.validateConfirmationInput(
            "Saya sadar dan ingin melanjutkan selama 3 menit.",
            template,
        ))

        // Wrong extension duration number
        assertFalse(BreakSessionManager.validateConfirmationInput(
            "Saya sadar dan memilih melanjutkan selama 5 menit.",
            template,
        ))

        // Missing punctuation or extra word
        assertFalse(BreakSessionManager.validateConfirmationInput(
            "Saya sadar dan memilih melanjutkan selama 3 menit lagi.",
            template,
        ))
        assertFalse(BreakSessionManager.validateConfirmationInput(
            "",
            template,
        ))
    }

    @Test
    fun breakSessionManager_maxConsecutiveExtensionsAndRestDuration() {
        assertEquals(2, BreakSessionManager.MAX_CONSECUTIVE_EXTENSIONS)
        assertEquals(5L * 60L * 1000L, BreakSessionManager.REST_DURATION_MILLIS)
    }

    @Test
    fun sessionReminderNotifications_channelAndIdsMatchRequirements() {
        assertEquals("jedain_session_reminder_channel_v2", SessionReminderNotificationHelper.CHANNEL_ID)
        assertEquals("jedain_session_reminder_channel", SessionReminderNotificationHelper.LEGACY_CHANNEL_ID)
        assertEquals("Peringatan Sesi Jeda", SessionReminderNotificationHelper.CHANNEL_NAME)
        assertEquals(
            "Pengingat ketika waktu sesi penggunaan aplikasi hampir berakhir.",
            SessionReminderNotificationHelper.CHANNEL_DESCRIPTION,
        )
        assertEquals(2001, SessionReminderNotificationHelper.NOTIF_ID_2MIN)
        assertEquals(2002, SessionReminderNotificationHelper.NOTIF_ID_30SEC)
        assertTrue(SessionReminderNotificationHelper.NOTIF_ID_2MIN != SessionReminderNotificationHelper.NOTIF_ID_30SEC)
    }

    @Test
    fun sessionReminderNotifications_thresholdConstantsAndKeysMatchExpectation() {
        assertEquals(120_000L, BreakSessionManager.TWO_MINUTES_MILLIS)
        assertEquals(30_000L, BreakSessionManager.THIRTY_SECONDS_MILLIS)
        assertEquals("notif_2min_sent", BreakSessionManager.KEY_NOTIF_2MIN_SENT)
        assertEquals("notif_30sec_sent", BreakSessionManager.KEY_NOTIF_30SEC_SENT)
    }

    @Test
    fun customSessionDuration_presetsAndValidationLimits() {
        assertEquals(listOf(5, 10, 15, 20, 30), PresetSessionDurationMinutes)
        assertEquals(1, TargetSettingsPreferences.MIN_SESSION_DURATION_MINUTES)
        assertEquals(60, TargetSettingsPreferences.MAX_SESSION_DURATION_MINUTES)
        assertEquals(25, TargetSettingsPreferences.DEFAULT_CUSTOM_SESSION_MINUTES)

        // Validation clamping
        assertEquals(1, validateSessionDurationMinutes(0))
        assertEquals(1, validateSessionDurationMinutes(-5))
        assertEquals(1, validateSessionDurationMinutes(1))
        assertEquals(7, validateSessionDurationMinutes(7))
        assertEquals(12, validateSessionDurationMinutes(12))
        assertEquals(25, validateSessionDurationMinutes(25))
        assertEquals(60, validateSessionDurationMinutes(60))
        assertEquals(60, validateSessionDurationMinutes(61))
        assertEquals(60, validateSessionDurationMinutes(120))
    }

    @Test
    fun customSessionDuration_countdownFormattingMatchesFloatingBubble() {
        // Skenario pengujian format awal countdown Floating Bubble
        val sevenMinMillis = 7L * 60L * 1000L
        val twelveMinMillis = 12L * 60L * 1000L
        val twentyFiveMinMillis = 25L * 60L * 1000L
        val fortyFiveMinMillis = 45L * 60L * 1000L
        val sixtyMinMillis = 60L * 60L * 1000L

        assertEquals("07:00", FloatingBubbleService.formatTimerDigital(sevenMinMillis))
        assertEquals("12:00", FloatingBubbleService.formatTimerDigital(twelveMinMillis))
        assertEquals("25:00", FloatingBubbleService.formatTimerDigital(twentyFiveMinMillis))
        assertEquals("45:00", FloatingBubbleService.formatTimerDigital(fortyFiveMinMillis))
        assertEquals("1:00:00", FloatingBubbleService.formatTimerDigital(sixtyMinMillis))
    }

    @Test
    fun customSessionDuration_extensionMinutesMappingForCustomDurations() {
        // Presets
        assertEquals(2, BreakSessionManager.getExtensionMinutes(5))
        assertEquals(3, BreakSessionManager.getExtensionMinutes(10))
        assertEquals(5, BreakSessionManager.getExtensionMinutes(15))
        assertEquals(5, BreakSessionManager.getExtensionMinutes(20))
        assertEquals(5, BreakSessionManager.getExtensionMinutes(30))

        // Custom durations
        assertEquals(1, BreakSessionManager.getExtensionMinutes(1))
        assertEquals(1, BreakSessionManager.getExtensionMinutes(2))
        assertEquals(2, BreakSessionManager.getExtensionMinutes(3))
        assertEquals(2, BreakSessionManager.getExtensionMinutes(4))
        assertEquals(3, BreakSessionManager.getExtensionMinutes(7))
        assertEquals(5, BreakSessionManager.getExtensionMinutes(12))
        assertEquals(5, BreakSessionManager.getExtensionMinutes(25))
        assertEquals(5, BreakSessionManager.getExtensionMinutes(45))
        assertEquals(5, BreakSessionManager.getExtensionMinutes(60))
    }

    @Test
    fun customSessionDuration_notificationThresholdLogic() {
        fun shouldTrigger2Min(remainingMillis: Long, totalMillis: Long): Boolean {
            return remainingMillis in (BreakSessionManager.THIRTY_SECONDS_MILLIS + 1L)..BreakSessionManager.TWO_MINUTES_MILLIS &&
                totalMillis > BreakSessionManager.TWO_MINUTES_MILLIS
        }

        fun shouldTrigger30Sec(remainingMillis: Long, totalMillis: Long): Boolean {
            return remainingMillis in 1L..BreakSessionManager.THIRTY_SECONDS_MILLIS &&
                totalMillis > BreakSessionManager.THIRTY_SECONDS_MILLIS
        }

        // Custom 25 min (1_500_000 ms)
        val total25m = 25L * 60L * 1000L
        assertFalse(shouldTrigger2Min(total25m, total25m))
        assertTrue(shouldTrigger2Min(120_000L, total25m))
        assertTrue(shouldTrigger30Sec(30_000L, total25m))
        assertFalse(shouldTrigger30Sec(0L, total25m))

        // Custom 7 min (420_000 ms)
        val total7m = 7L * 60L * 1000L
        assertFalse(shouldTrigger2Min(total7m, total7m))
        assertTrue(shouldTrigger2Min(119_000L, total7m))
        assertTrue(shouldTrigger30Sec(30_000L, total7m))

        // Custom 2 min (120_000 ms) - tidak memicu notifikasi 2 menit di awal
        val total2m = 2L * 60L * 1000L
        assertFalse(shouldTrigger2Min(total2m, total2m))
        assertTrue(shouldTrigger30Sec(30_000L, total2m))

        // Custom 1 min (60_000 ms) - tidak memicu notifikasi 2 menit, memicu 30 detik
        val total1m = 1L * 60L * 1000L
        assertFalse(shouldTrigger2Min(total1m, total1m))
        assertTrue(shouldTrigger30Sec(30_000L, total1m))

        // Durasi 30 detik atau kurang - tidak memicu 30 detik di awal
        val total30s = 30_000L
        assertFalse(shouldTrigger30Sec(total30s, total30s))
    }

    @Test
    fun customSessionDuration_statePersistenceModel() {
        val defaultState = TargetSettingsState()
        assertEquals(10, defaultState.sessionDurationMinutes)
        assertFalse(defaultState.isCustomSessionDuration)
        assertEquals(25, defaultState.customSessionDurationMinutes)

        // Custom 7 minutes
        val customState = TargetSettingsState(
            sessionDurationMinutes = 7,
            isCustomSessionDuration = true,
            customSessionDurationMinutes = 7,
        )
        assertEquals(7, customState.sessionDurationMinutes)
        assertTrue(customState.isCustomSessionDuration)
        assertEquals(7, customState.customSessionDurationMinutes)
    }

    @Test
    fun breakSessionDuration_constantsAndDefaultValues() {
        assertEquals(5, BreakSessionManager.DEFAULT_REST_DURATION_MINUTES)
        assertEquals("last_rest_duration_minutes", BreakSessionManager.KEY_LAST_REST_DURATION_MINUTES)
        assertEquals("last_rest_is_custom", BreakSessionManager.KEY_LAST_REST_IS_CUSTOM)
        assertEquals("total_rest_millis", BreakSessionManager.KEY_TOTAL_REST_MILLIS)
        assertEquals(5L * 60L * 1000L, BreakSessionManager.REST_DURATION_MILLIS)

        // Preset options matching requirements: 1, 3, 5, 10
        val presets = listOf(1, 3, 5, 10)
        assertEquals(4, presets.size)
        assertTrue(presets.contains(1))
        assertTrue(presets.contains(3))
        assertTrue(presets.contains(5))
        assertTrue(presets.contains(10))
        assertEquals(5, BreakSessionManager.DEFAULT_REST_DURATION_MINUTES)
    }

    @Test
    fun testTargetExceededTier_thresholdsAndCustomTargets() {
        val target3Hours = 180 // 3 jam

        // 1. Total <= target: kartu normal, tanpa indikator
        assertEquals(TargetExceededTier.NONE, resolveTargetExceededTier(totalMinutes = 160, targetMinutes = target3Hours)) // 2j 40m
        assertEquals(TargetExceededTier.NONE, resolveTargetExceededTier(totalMinutes = 180, targetMinutes = target3Hours)) // tepat 3 jam
        assertEquals(TargetExceededTier.NONE, resolveTargetExceededTier(totalMinutes = 0, targetMinutes = target3Hours))

        // 2. Total > target sampai +50% dari target (181 s/d 270 menit): warna oranye
        assertEquals(TargetExceededTier.WARNING_ORANGE, resolveTargetExceededTier(totalMinutes = 181, targetMinutes = target3Hours))
        assertEquals(TargetExceededTier.WARNING_ORANGE, resolveTargetExceededTier(totalMinutes = 226, targetMinutes = target3Hours)) // 3j 46m
        assertEquals(TargetExceededTier.WARNING_ORANGE, resolveTargetExceededTier(totalMinutes = 270, targetMinutes = target3Hours)) // 4j 30m (+50%)

        // 3. Total > target +50% (271+ menit): merah lembut
        assertEquals(TargetExceededTier.EXCEEDED_RED, resolveTargetExceededTier(totalMinutes = 271, targetMinutes = target3Hours)) // 4j 31m
        assertEquals(TargetExceededTier.EXCEEDED_RED, resolveTargetExceededTier(totalMinutes = 300, targetMinutes = target3Hours)) // 5j

        // 4. Target Kustom (contoh: 90 menit = 1 jam 30 menit, ambang +50% = 135 menit)
        val customTarget = 90
        assertEquals(TargetExceededTier.NONE, resolveTargetExceededTier(totalMinutes = 90, targetMinutes = customTarget))
        assertEquals(TargetExceededTier.WARNING_ORANGE, resolveTargetExceededTier(totalMinutes = 95, targetMinutes = customTarget))
        assertEquals(TargetExceededTier.WARNING_ORANGE, resolveTargetExceededTier(totalMinutes = 135, targetMinutes = customTarget))
        assertEquals(TargetExceededTier.EXCEEDED_RED, resolveTargetExceededTier(totalMinutes = 136, targetMinutes = customTarget))

        // 5. Target tidak diset (0 atau negatif): tidak ada indikator
        assertEquals(TargetExceededTier.NONE, resolveTargetExceededTier(totalMinutes = 100, targetMinutes = 0))
        assertEquals(TargetExceededTier.NONE, resolveTargetExceededTier(totalMinutes = 100, targetMinutes = -1))
    }
}
