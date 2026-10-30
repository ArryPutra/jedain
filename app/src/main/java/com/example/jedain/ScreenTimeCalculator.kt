package com.example.jedain

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class UsageEventLite(
    val packageName: String,
    val eventType: Int,
    val timeStamp: Long,
)

@Immutable
data class ScreenTimeSummary(
    val hasUsagePermission: Boolean,
    val hasOverlayPermission: Boolean,
    val appUsageMillisMap: Map<String, Long>,
    val yesterdayUsageMillisMap: Map<String, Long>,
    val todayTotalMillis: Long,
    val yesterdaySameTimeMillis: Long,
    val todayTotalDurationText: String,
    val comparisonChangeText: String,
    val isTrendingDown: Boolean,
)

private const val EVENT_SCREEN_NON_INTERACTIVE = 16
private const val EVENT_KEYGUARD_SHOWN = 17

fun computeForegroundMillis(
    events: List<UsageEventLite>,
    startTime: Long,
    endTime: Long,
    ownPackage: String,
    supportsScreenEvents: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
): Map<String, Long> {
    if (endTime <= startTime) return emptyMap()

    val durationMap = HashMap<String, Long>()
    var currentPkg: String? = null
    var currentStart = 0L

    fun closeCurrentSession(rawTimestamp: Long) {
        val activePkg = currentPkg ?: return
        val clampedEnd = rawTimestamp.coerceIn(startTime, endTime)
        val delta = (clampedEnd - currentStart).coerceAtLeast(0L)
        if (delta > 0L) {
            durationMap[activePkg] = (durationMap[activePkg] ?: 0L) + delta
        }
        currentPkg = null
        currentStart = 0L
    }

    for (event in events) {
        val clampedTime = event.timeStamp.coerceIn(startTime, endTime)

        if (supportsScreenEvents &&
            (event.eventType == EVENT_SCREEN_NON_INTERACTIVE || event.eventType == EVENT_KEYGUARD_SHOWN)
        ) {
            closeCurrentSession(clampedTime)
            continue
        }

        val pkg = event.packageName
        if (pkg.isBlank() || pkg == ownPackage) {
            continue
        }

        when (event.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> {
                if (currentPkg == null) {
                    currentPkg = pkg
                    currentStart = clampedTime
                } else if (currentPkg != pkg) {
                    closeCurrentSession(clampedTime)
                    currentPkg = pkg
                    currentStart = clampedTime
                }
            }
            UsageEvents.Event.ACTIVITY_PAUSED,
            UsageEvents.Event.ACTIVITY_STOPPED,
            -> {
                if (currentPkg == pkg) {
                    closeCurrentSession(clampedTime)
                }
            }
        }
    }

    if (currentPkg != null) {
        closeCurrentSession(endTime)
    }

    return durationMap
}

fun formatScreenTimeShort(millis: Long): String {
    if (millis <= 0L) return "0m"
    val totalMinutes = (millis / 60_000L).toInt().let { minutes ->
        if (minutes == 0 && millis >= 30_000L) 1 else minutes
    }
    if (totalMinutes <= 0) return "0m"

    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "${minutes}m"
        minutes == 0 -> "${hours}j"
        else -> "${hours}j ${minutes}m"
    }
}

fun formatScreenTimeComparison(
    hasUsagePermission: Boolean,
    todayMillis: Long,
    yesterdayMillis: Long,
): Pair<String, Boolean> {
    if (!hasUsagePermission) {
        return "Aktifkan izin penggunaan untuk melihat data asli" to true
    }

    val todayMinutes = (todayMillis / 60_000L).toInt()
    val yesterdayMinutes = (yesterdayMillis / 60_000L).toInt()
    val diffMinutes = todayMinutes - yesterdayMinutes

    return when {
        diffMinutes < 0 -> {
            val selisih = formatScreenTimeShort((-diffMinutes) * 60_000L)
            "$selisih lebih sedikit dari kemarin" to true
        }
        diffMinutes > 0 -> {
            val selisih = formatScreenTimeShort(diffMinutes * 60_000L)
            "$selisih lebih banyak dari kemarin" to false
        }
        else -> {
            "Sama seperti kemarin pada waktu ini" to true
        }
    }
}

internal fun buildScreenTimeSummaryFromMaps(
    hasUsagePermission: Boolean,
    hasOverlayPermission: Boolean,
    monitoredPackages: Set<String>,
    todayUsageMap: Map<String, Long>,
    yesterdayUsageMap: Map<String, Long>,
): ScreenTimeSummary {
    if (!hasUsagePermission) {
        val (changeText, isTrendingDown) = formatScreenTimeComparison(
            hasUsagePermission = false,
            todayMillis = 0L,
            yesterdayMillis = 0L,
        )
        return ScreenTimeSummary(
            hasUsagePermission = false,
            hasOverlayPermission = hasOverlayPermission,
            appUsageMillisMap = emptyMap(),
            yesterdayUsageMillisMap = emptyMap(),
            todayTotalMillis = 0L,
            yesterdaySameTimeMillis = 0L,
            todayTotalDurationText = "0m",
            comparisonChangeText = changeText,
            isTrendingDown = isTrendingDown,
        )
    }

    if (monitoredPackages.isEmpty()) {
        return ScreenTimeSummary(
            hasUsagePermission = true,
            hasOverlayPermission = hasOverlayPermission,
            appUsageMillisMap = todayUsageMap,
            yesterdayUsageMillisMap = yesterdayUsageMap,
            todayTotalMillis = 0L,
            yesterdaySameTimeMillis = 0L,
            todayTotalDurationText = "0m",
            comparisonChangeText = "Pilih aplikasi yang ingin dipantau",
            isTrendingDown = true,
        )
    }

    val todayTotalMillis = monitoredPackages.sumOf { pkg -> todayUsageMap[pkg] ?: 0L }
    val yesterdayTotalMillis = monitoredPackages.sumOf { pkg -> yesterdayUsageMap[pkg] ?: 0L }

    val (changeText, isTrendingDown) = formatScreenTimeComparison(
        hasUsagePermission = true,
        todayMillis = todayTotalMillis,
        yesterdayMillis = yesterdayTotalMillis,
    )

    return ScreenTimeSummary(
        hasUsagePermission = true,
        hasOverlayPermission = hasOverlayPermission,
        appUsageMillisMap = todayUsageMap,
        yesterdayUsageMillisMap = yesterdayUsageMap,
        todayTotalMillis = todayTotalMillis,
        yesterdaySameTimeMillis = yesterdayTotalMillis,
        todayTotalDurationText = formatScreenTimeShort(todayTotalMillis),
        comparisonChangeText = changeText,
        isTrendingDown = isTrendingDown,
    )
}

object ScreenTimeCalculator {

    private const val TWENTY_FOUR_HOURS_MILLIS = 24L * 60L * 60L * 1000L
    private const val THIRTY_MINUTES_MILLIS = 30L * 60L * 1000L
    private const val FIVE_MINUTES_MILLIS = 5L * 60L * 1000L
    private const val DIAG_TAG = "JedaInDiag"
    private const val DIAG_PACKAGE = "com.instagram.android"
    @Volatile
    private var lastDiagnosticLogMillis: Long = 0L

    fun calculateScreenTimeSummary(
        context: Context,
        monitoredPackages: Set<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ): ScreenTimeSummary {
        val hasUsagePermission = PermissionHelper.hasUsageStatsPermission(context)
        val hasOverlayPermission = PermissionHelper.hasOverlayPermission(context)

        if (!hasUsagePermission) {
            return buildScreenTimeSummaryFromMaps(
                hasUsagePermission = false,
                hasOverlayPermission = hasOverlayPermission,
                monitoredPackages = monitoredPackages,
                todayUsageMap = emptyMap(),
                yesterdayUsageMap = emptyMap(),
            )
        }

        val zoneId = ZoneId.systemDefault()
        val todayDate = LocalDate.now(zoneId)
        val todayStartMillis = todayDate
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val yesterdayStartMillis = todayDate
            .minusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val yesterdaySameTimeMillis = (nowMillis - TWENTY_FOUR_HOURS_MILLIS)
            .coerceIn(yesterdayStartMillis, todayStartMillis)

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val todayUsageMap = if (usageStatsManager != null) {
            queryPackageUsageMillis(
                usageStatsManager = usageStatsManager,
                startTime = todayStartMillis,
                endTime = nowMillis,
                ownPackageName = context.packageName,
            )
        } else {
            emptyMap()
        }

        if (BuildConfig.DEBUG && usageStatsManager != null) {
            if (nowMillis - lastDiagnosticLogMillis >= FIVE_MINUTES_MILLIS) {
                lastDiagnosticLogMillis = nowMillis
                logDiagnosticScreenTime(
                    usageStatsManager = usageStatsManager,
                    todayUsageMap = todayUsageMap,
                    todayStartMillis = todayStartMillis,
                    nowMillis = nowMillis,
                    zoneId = zoneId,
                )
            }
        }

        val yesterdayUsageMap = if (usageStatsManager != null && yesterdaySameTimeMillis > yesterdayStartMillis) {
            queryPackageUsageMillis(
                usageStatsManager = usageStatsManager,
                startTime = yesterdayStartMillis,
                endTime = yesterdaySameTimeMillis,
                ownPackageName = context.packageName,
            )
        } else {
            emptyMap()
        }

        return buildScreenTimeSummaryFromMaps(
            hasUsagePermission = true,
            hasOverlayPermission = hasOverlayPermission,
            monitoredPackages = monitoredPackages,
            todayUsageMap = todayUsageMap,
            yesterdayUsageMap = yesterdayUsageMap,
        )
    }

    private fun queryPackageUsageMillis(
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long,
        ownPackageName: String,
    ): Map<String, Long> {
        if (endTime <= startTime) return emptyMap()

        val usageEvents = runCatching {
            usageStatsManager.queryEvents(startTime - 6 * 60 * 60 * 1000L, endTime)
        }.getOrNull() ?: return emptyMap()

        val liteEvents = ArrayList<UsageEventLite>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            liteEvents.add(
                UsageEventLite(
                    packageName = event.packageName.orEmpty(),
                    eventType = event.eventType,
                    timeStamp = event.timeStamp,
                ),
            )
        }

        return computeForegroundMillis(
            events = liteEvents,
            startTime = startTime,
            endTime = endTime,
            ownPackage = ownPackageName,
            supportsScreenEvents = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
        )
    }

    private fun logDiagnosticScreenTime(
        usageStatsManager: UsageStatsManager,
        todayUsageMap: Map<String, Long>,
        todayStartMillis: Long,
        nowMillis: Long,
        zoneId: ZoneId,
    ) {
        val computedMillis = todayUsageMap[DIAG_PACKAGE] ?: 0L
        Log.d(
            DIAG_TAG,
            "1. computeForegroundMillis($DIAG_PACKAGE) = ${formatMinutesAndSeconds(computedMillis)} (${computedMillis}ms)",
        )

        val buckets = runCatching {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                todayStartMillis,
                nowMillis,
            )
        }.getOrNull().orEmpty()
        val bucketSumMillis = buckets
            .filter { it.packageName == DIAG_PACKAGE && it.lastTimeUsed >= todayStartMillis }
            .sumOf { it.totalTimeInForeground }
        Log.d(
            DIAG_TAG,
            "2. queryUsageStats.totalTimeInForeground($DIAG_PACKAGE) = ${formatMinutesAndSeconds(bucketSumMillis)} (${bucketSumMillis}ms)",
        )

        val rawStartMillis = (nowMillis - THIRTY_MINUTES_MILLIS).coerceAtLeast(0L)
        val rawEvents = runCatching {
            usageStatsManager.queryEvents(rawStartMillis, nowMillis)
        }.getOrNull()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zoneId)
        Log.d(DIAG_TAG, "3. Raw UsageEvents (last 30m):")
        if (rawEvents != null) {
            val event = UsageEvents.Event()
            while (rawEvents.hasNextEvent()) {
                rawEvents.getNextEvent(event)
                val timeStr = timeFormatter.format(Instant.ofEpochMilli(event.timeStamp))
                Log.d(DIAG_TAG, "$timeStr | ${event.packageName.orEmpty()} | ${event.eventType}")
            }
        }
    }

    private fun formatMinutesAndSeconds(millis: Long): String {
        val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "${minutes}m ${seconds}s"
    }
}
