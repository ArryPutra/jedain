package com.example.jedain

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

        val feedbackCustom = resolveDailyTargetFeedback(90, isCustom = true, colors = colors)
        assertEquals("1 jam 30 menit", feedbackCustom.durationText)
        assertEquals("Perlu diperhatikan", feedbackCustom.category)
        assertEquals(colors.durationCustomBlue, feedbackCustom.accentColor)
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
}
