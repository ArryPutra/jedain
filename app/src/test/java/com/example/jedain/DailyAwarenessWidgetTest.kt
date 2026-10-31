package com.example.jedain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyAwarenessWidgetTest {

    @Test
    fun testFormatScreenTimeShort_standardCases() {
        assertEquals("0m", formatScreenTimeShort(0L))
        assertEquals("0m", formatScreenTimeShort(-5000L))
        assertEquals("12m", formatScreenTimeShort(12 * 60_000L))
        assertEquals("1j", formatScreenTimeShort(60 * 60_000L))
        assertEquals("1j 24m", formatScreenTimeShort(84 * 60_000L))
        assertEquals("2j 10m", formatScreenTimeShort(130 * 60_000L))
        assertEquals("2j 50m", formatScreenTimeShort(170 * 60_000L))
        assertEquals("3j", formatScreenTimeShort(180 * 60_000L))
        assertEquals("4j 12m", formatScreenTimeShort(252 * 60_000L))
        assertEquals("5j", formatScreenTimeShort(300 * 60_000L))
        assertEquals("5j 34m", formatScreenTimeShort(334 * 60_000L))
    }

    // Uji Kondisi 1: 2j 10m dengan target 3j (Normal: < 90% target, Hijau)
    @Test
    fun testScenario1_normalTier_below90Percent() {
        val totalMillis = 130 * 60_000L // 2j 10m
        val targetMinutes = 180 // 3j
        val targetMillis = targetMinutes * 60_000L

        val totalScreenTimeText = formatScreenTimeShort(totalMillis)
        val targetDurationText = formatScreenTimeShort(targetMillis)

        val actualPercentage = (totalMillis.toDouble() / targetMillis.toDouble()) * 100.0
        val visualProgressPercent = actualPercentage.toInt().coerceIn(0, 100)

        val targetTier = when {
            actualPercentage < 90.0 -> WidgetTargetTier.NORMAL
            actualPercentage <= 100.0 -> WidgetTargetTier.WARNING
            else -> WidgetTargetTier.EXCEEDED
        }

        val diffMillis = totalMillis - targetMillis
        val exceededDiffText = if (diffMillis > 0L) "+${formatScreenTimeShort(diffMillis)}" else ""
        val ratioText = "$totalScreenTimeText / $targetDurationText"

        assertEquals("2j 10m", totalScreenTimeText)
        assertEquals("3j", targetDurationText)
        assertEquals("2j 10m / 3j", ratioText)
        assertEquals(72.222, actualPercentage, 0.001)
        assertEquals(72, visualProgressPercent)
        assertEquals(WidgetTargetTier.NORMAL, targetTier)
        assertEquals("", exceededDiffText)
    }

    // Uji Kondisi 2: 2j 50m dengan target 3j (Warning: 90% - 100% target, Kuning/Amber)
    @Test
    fun testScenario2_warningTier_between90And100Percent() {
        val totalMillis = 170 * 60_000L // 2j 50m
        val targetMinutes = 180 // 3j
        val targetMillis = targetMinutes * 60_000L

        val totalScreenTimeText = formatScreenTimeShort(totalMillis)
        val targetDurationText = formatScreenTimeShort(targetMillis)

        val actualPercentage = (totalMillis.toDouble() / targetMillis.toDouble()) * 100.0
        val visualProgressPercent = actualPercentage.toInt().coerceIn(0, 100)

        val targetTier = when {
            actualPercentage < 90.0 -> WidgetTargetTier.NORMAL
            actualPercentage <= 100.0 -> WidgetTargetTier.WARNING
            else -> WidgetTargetTier.EXCEEDED
        }

        val diffMillis = totalMillis - targetMillis
        val exceededDiffText = if (diffMillis > 0L) "+${formatScreenTimeShort(diffMillis)}" else ""
        val ratioText = "$totalScreenTimeText / $targetDurationText"

        assertEquals("2j 50m", totalScreenTimeText)
        assertEquals("3j", targetDurationText)
        assertEquals("2j 50m / 3j", ratioText)
        assertEquals(94.444, actualPercentage, 0.001)
        assertEquals(94, visualProgressPercent)
        assertEquals(WidgetTargetTier.WARNING, targetTier)
        assertEquals("", exceededDiffText)
    }

    // Uji Kondisi 3: 5j 34m dengan target 3j (Exceeded: > 100% target, Merah)
    @Test
    fun testScenario3_exceededTier_above100Percent() {
        val totalMillis = 334 * 60_000L // 5j 34m
        val targetMinutes = 180 // 3j
        val targetMillis = targetMinutes * 60_000L

        val totalScreenTimeText = formatScreenTimeShort(totalMillis)
        val targetDurationText = formatScreenTimeShort(targetMillis)

        val actualPercentage = (totalMillis.toDouble() / targetMillis.toDouble()) * 100.0
        // Progress bar maksimal 100% secara visual
        val visualProgressPercent = actualPercentage.toInt().coerceIn(0, 100)

        val targetTier = when {
            actualPercentage < 90.0 -> WidgetTargetTier.NORMAL
            actualPercentage <= 100.0 -> WidgetTargetTier.WARNING
            else -> WidgetTargetTier.EXCEEDED
        }

        // Hitung selisih penggunaan terhadap target secara akurat
        val diffMillis = totalMillis - targetMillis
        val exceededDiffText = if (diffMillis > 0L) "+${formatScreenTimeShort(diffMillis)}" else ""
        val ratioText = "$exceededDiffText / $targetDurationText"

        assertEquals("5j 34m", totalScreenTimeText)
        assertEquals("3j", targetDurationText)
        assertEquals("+2j 34m", exceededDiffText)
        assertEquals("+2j 34m / 3j", ratioText)
        assertEquals(185.555, actualPercentage, 0.001)
        assertEquals(100, visualProgressPercent)
        assertEquals(WidgetTargetTier.EXCEEDED, targetTier)
    }

    // Uji kondisi target belum diatur atau 0
    @Test
    fun testTargetZeroOrNotSet_handledGracefully() {
        val totalMillis = 60 * 60_000L // 1j
        val targetMinutes = 0
        val isTargetSet = targetMinutes > 0

        val actualPercentage = if (isTargetSet) (totalMillis.toDouble() / (targetMinutes * 60_000L)) * 100.0 else 0.0
        val visualProgressPercent = actualPercentage.toInt().coerceIn(0, 100)
        val targetTier = if (!isTargetSet) WidgetTargetTier.NORMAL else WidgetTargetTier.EXCEEDED
        val ratioText = if (isTargetSet) "1j / 0m" else "Target belum diatur"

        assertEquals(0.0, actualPercentage, 0.0)
        assertEquals(0, visualProgressPercent)
        assertEquals(WidgetTargetTier.NORMAL, targetTier)
        assertEquals("Target belum diatur", ratioText)
    }

    @Test
    fun testTopAppSelection_excludesOwnPackageAndSystemUi() {
        val ownPkg = "com.example.jedain"
        val systemUiPkg = "com.android.systemui"
        val instagramPkg = "com.instagram.android"
        val youtubePkg = "com.google.android.youtube"

        val usageMap = mapOf(
            ownPkg to 120 * 60_000L,
            systemUiPkg to 200 * 60_000L,
            youtubePkg to 45 * 60_000L,
            instagramPkg to 84 * 60_000L, // 1j 24m
        )

        val topEntry = usageMap
            .asSequence()
            .filter { (pkg, millis) ->
                pkg != ownPkg &&
                    pkg != systemUiPkg &&
                    millis > 0L
            }
            .maxByOrNull { it.value }

        assertEquals(instagramPkg, topEntry?.key)
        assertEquals(84 * 60_000L, topEntry?.value)

        val durationText = formatScreenTimeShort(topEntry!!.value)
        assertEquals("1j 24m", durationText)
    }

    @Test
    fun testTopAppSelection_emptyUsageReturnsNull() {
        val ownPkg = "com.example.jedain"
        val usageMap = mapOf(
            ownPkg to 60_000L,
            "com.android.systemui" to 120_000L,
        )

        val topEntry = usageMap
            .asSequence()
            .filter { (pkg, millis) ->
                pkg != ownPkg &&
                    pkg != "com.android.systemui" &&
                    millis > 0L
            }
            .maxByOrNull { it.value }

        assertEquals(null, topEntry)
    }

    @Test
    fun testWidgetStateDataStructure_exceededState() {
        val state = DailyAwarenessWidgetState(
            hasUsagePermission = true,
            totalScreenTimeText = "5j 34m",
            targetRatioText = "+2j 34m / 3j",
            visualProgressPercent = 100,
            actualProgressPercent = 185.55,
            targetTier = WidgetTargetTier.EXCEEDED,
            exceededDiffText = "+2j 34m",
            topAppInfoText = "Instagram · 1j 24m",
            topAppIconBitmap = null,
            isTargetSet = true,
        )

        assertTrue(state.hasUsagePermission)
        assertEquals("5j 34m", state.totalScreenTimeText)
        assertEquals("+2j 34m / 3j", state.targetRatioText)
        assertEquals(100, state.visualProgressPercent)
        assertEquals(185.55, state.actualProgressPercent, 0.01)
        assertEquals(WidgetTargetTier.EXCEEDED, state.targetTier)
        assertEquals("+2j 34m", state.exceededDiffText)
        assertEquals("Instagram · 1j 24m", state.topAppInfoText)
        assertTrue(state.isTargetSet)
    }

    @Test
    fun testWidgetState_permissionDenied() {
        val state = DailyAwarenessWidgetState(
            hasUsagePermission = false,
            totalScreenTimeText = "0m",
            targetRatioText = "0m / 3j",
            visualProgressPercent = 0,
            actualProgressPercent = 0.0,
            targetTier = WidgetTargetTier.NORMAL,
            exceededDiffText = "",
            topAppInfoText = "Belum ada aktivitas",
            topAppIconBitmap = null,
            isTargetSet = true,
        )

        assertFalse(state.hasUsagePermission)
        assertEquals("0m", state.totalScreenTimeText)
        assertEquals(0, state.visualProgressPercent)
        assertEquals(WidgetTargetTier.NORMAL, state.targetTier)
    }
}
