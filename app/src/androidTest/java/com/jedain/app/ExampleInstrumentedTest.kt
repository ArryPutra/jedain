package com.jedain.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jedain.app.ui.theme.JedaInTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dailyTargetGrid_showsSixOptionsAndDynamicExplanation() {
        composeTestRule.setContent {
            JedaInTheme {
                SetTargetScreen(
                    initialSettings = TargetSettingsState(
                        dailyTargetMinutes = 60,
                        isCustomDailyTarget = false,
                    ),
                    onBackClick = {},
                    onSaveSettings = {},
                )
            }
        }

        // Verify 5 cards exist (30m, 1j, 2j, 3j, Kustom) and 4/5/6 jam preset cards do not exist
        composeTestRule.onNodeWithText("Sangat terkontrol").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 jam").assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithText("2 jam").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 jam").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kustom").assertIsDisplayed()
        composeTestRule.onNodeWithText("4 jam").assertDoesNotExist()
        composeTestRule.onNodeWithText("5 jam").assertDoesNotExist()
        composeTestRule.onNodeWithText("6 jam").assertDoesNotExist()

        // Dynamic explanation for 1 jam
        composeTestRule.onNodeWithText(
            "Nikmati hiburan tanpa terlalu mengurangi waktu untuk kegiatan lain.",
        ).assertIsDisplayed()

        // Select 3 jam -> explanation updates immediately
        composeTestRule.onNodeWithText("3 jam").performClick()
        composeTestRule.onNodeWithText(
            "Durasi ini cukup tinggi. Pertimbangkan menguranginya secara bertahap.",
        ).assertIsDisplayed()
    }

    @Test
    fun customTargetModal_supportsStepLimitsCancelAndApply() {
        var savedState = TargetSettingsState(
            dailyTargetMinutes = 120,
            isCustomDailyTarget = false,
            customDailyTargetMinutes = 90,
        )

        composeTestRule.setContent {
            JedaInTheme {
                SetTargetScreen(
                    initialSettings = savedState,
                    onBackClick = {},
                    onTargetSettingsChanged = { savedState = it },
                    onSaveSettings = { savedState = it },
                )
            }
        }

        // Open Kustom popup and cancel -> does not change active target (2 jam)
        composeTestRule.onNodeWithTag("custom_target_card").performClick()
        composeTestRule.onNodeWithText("Atur Target Kustom").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tentukan batas waktu penggunaan harianmu.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Tambah menit").performClick()
        composeTestRule.onNodeWithText("Batal").performClick()

        assertEquals(120, savedState.dailyTargetMinutes)
        assertFalse(savedState.isCustomDailyTarget)

        // Open Kustom popup again -> still starts at 1 jam 30 menit (90m) and apply
        composeTestRule.onNodeWithTag("custom_target_card").performClick()
        composeTestRule.onNodeWithText("Ringkasan: 1 jam 30 menit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Terapkan Target").performClick()

        assertEquals(90, savedState.dailyTargetMinutes)
        assertTrue(savedState.isCustomDailyTarget)
        composeTestRule.onNodeWithText("1 jam 30 menit").assertIsDisplayed()
        composeTestRule.onNodeWithTag("custom_target_card").assertIsSelected()

        // Reopen Kustom popup and verify min (15m) and max (4h) bounds
        composeTestRule.onNodeWithTag("custom_target_card").performClick()
        composeTestRule.onNodeWithContentDescription("Kurangi jam").performClick() // 30m
        composeTestRule.onNodeWithContentDescription("Kurangi menit").performClick() // 15m
        composeTestRule.onNodeWithText("Ringkasan: 15 menit").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Kurangi menit").assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Kurangi jam").assertIsNotEnabled()

        // Increase to 4 hours (240m)
        repeat(4) {
            composeTestRule.onNodeWithContentDescription("Tambah jam").performClick()
        }
        composeTestRule.onNodeWithText("Ringkasan: 4 jam").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Tambah jam").assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Tambah menit").assertIsNotEnabled()
    }

    @Test
    fun targetSettingsPreferences_persistsAndRestoresCustomAndPresetTargets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("jedain_target_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val prefs = TargetSettingsPreferences(context)
        val customSettings = TargetSettingsState(
            dailyTargetMinutes = 105,
            isCustomDailyTarget = true,
            customDailyTargetMinutes = 105,
            sessionDurationMinutes = 15,
            reminderOnSessionEnd = false,
        )
        prefs.saveSettings(customSettings)

        val loaded = prefs.loadSettings()
        assertEquals(105, loaded.dailyTargetMinutes)
        assertTrue(loaded.isCustomDailyTarget)
        assertEquals(105, loaded.customDailyTargetMinutes)
        assertEquals(15, loaded.sessionDurationMinutes)
        assertFalse(loaded.reminderOnSessionEnd)
    }
}
