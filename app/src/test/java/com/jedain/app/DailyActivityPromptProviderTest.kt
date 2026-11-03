package com.jedain.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyActivityPromptProviderTest {

    // 1. Seluruh enam kategori memiliki aktivitas
    @Test
    fun testAllSixCategoriesHaveActivities() {
        val categoriesPresent = DailyActivityPromptProvider.allPrompts.map { it.category }.toSet()
        assertEquals(ActivityCategory.entries.toSet(), categoriesPresent)
        for (category in ActivityCategory.entries) {
            val count = DailyActivityPromptProvider.allPrompts.count { it.category == category }
            assertTrue("Kategori $category harus memiliki aktivitas", count > 0)
        }
    }

    // 2. Jumlah aktivitas minimal 48
    @Test
    fun testTotalActivitiesAtLeast48() {
        val total = DailyActivityPromptProvider.allPrompts.size
        assertTrue("Jumlah aktivitas minimal 48 (aktual: $total)", total >= 48)
    }

    // 3. Semua ID unik
    @Test
    fun testAllPromptIdsAreUnique() {
        val ids = DailyActivityPromptProvider.allPrompts.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    // 4. Semua headline dan subtitle tidak kosong
    @Test
    fun testAllHeadlinesAndSubtitlesAreNotEmpty() {
        DailyActivityPromptProvider.allPrompts.forEach { prompt ->
            assertTrue("Headline tidak boleh kosong untuk ID ${prompt.id}", prompt.headline.isNotBlank())
            assertTrue("Subtitle tidak boleh kosong untuk ID ${prompt.id}", prompt.subtitle.isNotBlank())
        }
    }

    // 5. Jam 01.00 hanya menghasilkan aktivitas yang sesuai dini hari
    @Test
    fun testHour01ReturnsEarlyMorningActivitiesOnly() {
        repeat(100) {
            val prompt = DailyActivityPromptProvider.getNextPrompt(1)
            assertTrue("Jam 01.00 harus dalam preferredHours", 1 in prompt.preferredHours)
            assertEquals("Jam 01.00 harus dalam rentang 0..4", 0..4, prompt.preferredHours)
            // Tidak boleh olahraga berat atau aktivitas luar ruangan
            assertTrue(prompt.category != ActivityCategory.OUTDOOR)
            assertTrue(prompt.category != ActivityCategory.EXERCISE)
        }
    }

    // 6. Jam 07.00 menghasilkan aktivitas yang sesuai pagi
    @Test
    fun testHour07ReturnsMorningActivities() {
        repeat(100) {
            val prompt = DailyActivityPromptProvider.getNextPrompt(7)
            assertTrue("Jam 07.00 harus dalam preferredHours", 7 in prompt.preferredHours)
            assertEquals("Jam 07.00 harus dalam rentang 5..9", 5..9, prompt.preferredHours)
        }
    }

    // 7. Jam 11.00 menghasilkan aktivitas yang sesuai menjelang siang
    @Test
    fun testHour11ReturnsLateMorningActivities() {
        repeat(100) {
            val prompt = DailyActivityPromptProvider.getNextPrompt(11)
            assertTrue("Jam 11.00 harus dalam preferredHours", 11 in prompt.preferredHours)
            assertEquals("Jam 11.00 harus dalam rentang 10..12", 10..12, prompt.preferredHours)
        }
    }

    // 8. Jam 15.00 menghasilkan aktivitas yang sesuai sore
    @Test
    fun testHour15ReturnsAfternoonActivities() {
        repeat(100) {
            val prompt = DailyActivityPromptProvider.getNextPrompt(15)
            assertTrue("Jam 15.00 harus dalam preferredHours", 15 in prompt.preferredHours)
            assertEquals("Jam 15.00 harus dalam rentang 13..16", 13..16, prompt.preferredHours)
        }
    }

    // 9. Jam 19.00 menghasilkan aktivitas yang sesuai petang
    @Test
    fun testHour19ReturnsEveningActivities() {
        repeat(100) {
            val prompt = DailyActivityPromptProvider.getNextPrompt(19)
            assertTrue("Jam 19.00 harus dalam preferredHours", 19 in prompt.preferredHours)
            assertEquals("Jam 19.00 harus dalam rentang 17..20", 17..20, prompt.preferredHours)
        }
    }

    // 10. Jam 23.00 menghasilkan aktivitas yang sesuai malam
    @Test
    fun testHour23ReturnsLateNightActivities() {
        repeat(100) {
            val prompt = DailyActivityPromptProvider.getNextPrompt(23)
            assertTrue("Jam 23.00 harus dalam preferredHours", 23 in prompt.preferredHours)
            assertEquals("Jam 23.00 harus dalam rentang 21..23", 21..23, prompt.preferredHours)
            // Tidak boleh olahraga berat atau aktivitas luar ruangan saat larut malam
            assertTrue(prompt.category != ActivityCategory.OUTDOOR)
            assertTrue(prompt.category != ActivityCategory.EXERCISE)
        }
    }

    // 11. excludeId tidak dikembalikan ketika alternatif tersedia
    @Test
    fun testExcludeIdIsNotReturnedWhenAlternativesExist() {
        DailyActivityPromptProvider.allPrompts.forEach { prompt ->
            val hour = prompt.preferredHours.first
            val result = DailyActivityPromptProvider.getNextPrompt(hour, excludeId = prompt.id)
            assertNotEquals("excludeId tidak boleh dikembalikan saat alternatif ada", prompt.id, result.id)
        }
    }

    // 12. Rotasi memungkinkan pengguna mendapatkan kategori berbeda
    @Test
    fun testRotationAllowsDifferentCategories() {
        // Cek pada periode yang memiliki multi-kategori (misal jam 7, 11, 15, 19)
        val testHours = listOf(7, 11, 15, 19)
        for (hour in testHours) {
            val firstPrompt = DailyActivityPromptProvider.getNextPrompt(hour)
            val secondPrompt = DailyActivityPromptProvider.getNextPrompt(hour, excludeId = firstPrompt.id)
            assertNotEquals(
                "Rotasi harus memprioritaskan kategori yang berbeda pada jam $hour",
                firstPrompt.category,
                secondPrompt.category
            )
        }
    }

    // 13. Batas pergantian periode dan input ekstrim tidak menyebabkan error
    @Test
    fun testBoundaryAndEdgeHourTransitionsDoNotCauseError() {
        // Uji seluruh 24 jam
        for (hour in 0..23) {
            val prompt = DailyActivityPromptProvider.getNextPrompt(hour)
            assertNotNull(prompt)
            assertTrue(prompt.id.isNotBlank())
            assertTrue(hour in prompt.preferredHours)
        }

        // Uji jam di luar rentang standar (negatif atau >= 24)
        val negativeHourPrompt = DailyActivityPromptProvider.getNextPrompt(-5)
        assertNotNull(negativeHourPrompt)
        assertTrue(0 in negativeHourPrompt.preferredHours)

        val overflowHourPrompt = DailyActivityPromptProvider.getNextPrompt(99)
        assertNotNull(overflowHourPrompt)
        assertTrue(23 in overflowHourPrompt.preferredHours)
    }
}
