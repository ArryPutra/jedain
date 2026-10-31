package com.example.jedain

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
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
    val className: String = "",
)

@Immutable
data class ScreenTimeSummary(
    val hasUsagePermission: Boolean,
    val hasOverlayPermission: Boolean,
    val appUsageMillisMap: Map<String, Long>,
    val yesterdayUsageMillisMap: Map<String, Long>,
    val todayTotalMillis: Long, // Total durasi aplikasi yang dipantau hari ini
    val todayDeviceTotalMillis: Long = todayTotalMillis, // Total waktu layar seluruh perangkat hari ini
    val yesterdaySameTimeMillis: Long,
    val todayTotalDurationText: String, // Teks total waktu layar seluruh perangkat pada kartu utama
    val monitoredTotalDurationText: String = todayTotalDurationText, // Teks total aplikasi yang dipantau
    val comparisonChangeText: String,
    val isTrendingDown: Boolean,
)

internal const val EVENT_MOVE_TO_FOREGROUND = 1
internal const val EVENT_MOVE_TO_BACKGROUND = 2
internal const val EVENT_SCREEN_INTERACTIVE = 15
internal const val EVENT_SCREEN_NON_INTERACTIVE = 16
internal const val EVENT_KEYGUARD_SHOWN = 17
internal const val EVENT_KEYGUARD_HIDDEN = 18
internal const val EVENT_ACTIVITY_STOPPED = 23

/**
 * State machine kronologis untuk menghitung durasi penggunaan foreground per package
 * berdasarkan UsageEvents Android.
 *
 * Ketentuan utama:
 * 1. Melacak status aktivitas per package dan per className (Activity) agar transisi antar-Activity
 *    di dalam satu aplikasi tidak menyebabkan double counting atau kehilangan sesi.
 * 2. Menangani event sesuai versi Android (ACTIVITY_RESUMED/PAUSED/STOPPED, MOVE_TO_FOREGROUND/BACKGROUND,
 *    SCREEN_INTERACTIVE/NON_INTERACTIVE, KEYGUARD_SHOWN/HIDDEN).
 * 3. Memotong (clip) setiap sesi secara ketat ke dalam rentang [startTime, endTime]:
 *    sessionStart = maxOf(actualSessionStart, startTime)
 *    sessionEnd   = minOf(actualSessionEnd, endTime)
 * 4. Menutup seluruh sesi aktif ketika layar mati (SCREEN_NON_INTERACTIVE) atau terkunci (KEYGUARD_SHOWN),
 *    dan TIDAK membuka kembali sesi secara otomatis hanya karena layar menyala (menunggu event aktivitas nyata).
 * 5. Menutup sesi yang masih terbuka pada akhir periode di titik endTime.
 */
fun computeForegroundMillis(
    events: List<UsageEventLite>,
    startTime: Long,
    endTime: Long,
    ownPackage: String,
    supportsScreenEvents: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
    diagnosticLog: ((String) -> Unit)? = null,
): Map<String, Long> {
    if (endTime <= startTime || events.isEmpty()) return emptyMap()

    // Urutkan secara stabil berdasarkan timestamp, dan jika timestamp sama,
    // dahulukan event penutupan (PAUSE/STOP/SCREEN_OFF) sebelum pembukaan (RESUME) agar transisi bersih
    val sortedEvents = events.sortedWith(
        compareBy<UsageEventLite> { it.timeStamp }
            .thenBy { eventPriorityForSameTimestamp(it.eventType) },
    )

    val durationMap = HashMap<String, Long>()

    // Menyimpan daftar Activity yang sedang aktif untuk setiap package: packageName -> MutableSet<activityKey>
    val activeActivitiesPerPkg = HashMap<String, MutableSet<String>>()
    // Menyimpan waktu mulai sesi foreground saat ini untuk setiap package (ketika transisi dari 0 -> >=1 activity aktif)
    val packageSessionStartMap = HashMap<String, Long>()

    // Deduplikasi event identik berturut-turut pada timestamp yang sama
    var lastProcessedPkg = ""
    var lastProcessedClass = ""
    var lastProcessedType = -1
    var lastProcessedTime = Long.MIN_VALUE

    fun recordPackageSession(pkg: String, rawStart: Long, rawEnd: Long, reason: String) {
        if (pkg == ownPackage) return
        val sessionStart = maxOf(rawStart, startTime)
        val sessionEnd = minOf(rawEnd, endTime)
        val delta = (sessionEnd - sessionStart).coerceAtLeast(0L)
        if (delta > 0L) {
            val updated = (durationMap[pkg] ?: 0L) + delta
            durationMap[pkg] = updated
            diagnosticLog?.invoke(
                "SESSION_CLOSED pkg=$pkg start=$sessionStart end=$sessionEnd delta=${delta}ms total=${updated}ms reason=$reason",
            )
        }
    }

    fun closePackageSession(pkg: String, timestamp: Long, reason: String) {
        activeActivitiesPerPkg.remove(pkg)
        val start = packageSessionStartMap.remove(pkg)
        if (start != null) {
            recordPackageSession(pkg, start, timestamp, reason)
        }
    }

    fun closeAllActiveSessions(timestamp: Long, reason: String) {
        val activePackages = packageSessionStartMap.keys.toList()
        for (pkg in activePackages) {
            closePackageSession(pkg, timestamp, reason)
        }
        activeActivitiesPerPkg.clear()
        packageSessionStartMap.clear()
    }

    for (event in sortedEvents) {
        val rawTime = event.timeStamp
        if (rawTime > endTime) {
            // Event melewati batas akhir periode yang diminta
            break
        }

        val pkg = event.packageName
        val cls = event.className.ifBlank { "__default_activity__" }
        val type = event.eventType

        // Cegah pemrosesan event identik dua kali
        if (rawTime == lastProcessedTime &&
            type == lastProcessedType &&
            pkg == lastProcessedPkg &&
            cls == lastProcessedClass
        ) {
            continue
        }
        lastProcessedTime = rawTime
        lastProcessedType = type
        lastProcessedPkg = pkg
        lastProcessedClass = cls

        // 1. Penanganan event layar & lockscreen
        if (supportsScreenEvents) {
            when (type) {
                EVENT_SCREEN_NON_INTERACTIVE -> {
                    diagnosticLog?.invoke("EVENT_SCREEN_NON_INTERACTIVE at $rawTime")
                    closeAllActiveSessions(rawTime, "SCREEN_NON_INTERACTIVE")
                    continue
                }
                EVENT_KEYGUARD_SHOWN -> {
                    diagnosticLog?.invoke("EVENT_KEYGUARD_SHOWN at $rawTime")
                    closeAllActiveSessions(rawTime, "KEYGUARD_SHOWN")
                    continue
                }
                EVENT_SCREEN_INTERACTIVE, EVENT_KEYGUARD_HIDDEN -> {
                    // Jangan membuka kembali sesi aplikasi hanya karena layar menyala atau unlock.
                    // Tunggu event aktivitas aplikasi yang benar-benar aktif.
                    diagnosticLog?.invoke("EVENT_SCREEN_WAKE/UNLOCK (type=$type) at $rawTime")
                    continue
                }
            }
        }

        if (pkg.isBlank()) {
            continue
        }

        // 2. Penanganan event siklus hidup Activity / Package
        when (type) {
            UsageEvents.Event.ACTIVITY_RESUMED -> {
                // Jika JedaIn sendiri masuk ke foreground, tutup sesi aplikasi lain yang sebelumnya aktif
                // (kecuali aplikasi lain menutup dirinya sendiri lewat PAUSED).
                // Untuk transisi antar-aplikasi berbeda ketika event PAUSED dari aplikasi lama terlambat/hilang:
                val otherActivePackages = packageSessionStartMap.keys.filter { it != pkg }
                for (otherPkg in otherActivePackages) {
                    closePackageSession(otherPkg, rawTime, "SWITCH_TO_$pkg")
                }

                if (pkg == ownPackage) {
                    continue
                }

                val activities = activeActivitiesPerPkg.getOrPut(pkg) { HashSet() }
                val wasEmpty = !packageSessionStartMap.containsKey(pkg)
                activities.add(cls)
                if (wasEmpty) {
                    packageSessionStartMap[pkg] = rawTime
                    diagnosticLog?.invoke("SESSION_OPENED pkg=$pkg class=$cls at $rawTime")
                } else {
                    diagnosticLog?.invoke("ACTIVITY_TRANSITION_IN_PKG pkg=$pkg class=$cls at $rawTime")
                }
            }

            UsageEvents.Event.ACTIVITY_PAUSED,
            EVENT_ACTIVITY_STOPPED,
            -> {
                if (pkg == ownPackage) {
                    continue
                }
                val activities = activeActivitiesPerPkg[pkg]
                if (activities != null) {
                    if (cls == "__default_activity__") {
                        // Jika className tidak tersedia pada UsageEventLite, tutup seluruh sesi package
                        closePackageSession(pkg, rawTime, "PAUSED_NO_CLASS(type=$type)")
                    } else {
                        activities.remove(cls)
                        if (activities.isEmpty()) {
                            closePackageSession(pkg, rawTime, "ALL_ACTIVITIES_PAUSED(type=$type)")
                        }
                    }
                }
            }
        }
    }

    // 3. Tutup sesi yang masih aktif saat batas akhir query (endTime) tercapai
    val remainingPackages = packageSessionStartMap.keys.toList()
    for (pkg in remainingPackages) {
        closePackageSession(pkg, endTime, "QUERY_END_TIME_REACHED")
    }

    return durationMap
}

private fun eventPriorityForSameTimestamp(eventType: Int): Int = when (eventType) {
    EVENT_SCREEN_NON_INTERACTIVE, EVENT_KEYGUARD_SHOWN -> 0
    UsageEvents.Event.ACTIVITY_PAUSED, EVENT_ACTIVITY_STOPPED -> 1
    EVENT_SCREEN_INTERACTIVE, EVENT_KEYGUARD_HIDDEN -> 2
    UsageEvents.Event.ACTIVITY_RESUMED -> 3
    else -> 4
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

/**
 * Format total waktu penggunaan layar hari ini untuk persistent silent notification JedaIn.
 * Aturan format:
 * - Kurang dari 1 jam: "42 menit"
 * - 1 jam atau lebih: "3 jam 42 menit"
 * - Jika tepat satu jam: "1 jam"
 * - Jika belum ada penggunaan: "0 menit"
 */
fun formatDailyScreenTimeNotification(millis: Long): String {
    if (millis <= 0L) return "0 menit"
    val totalMinutes = millis / 60_000L
    if (totalMinutes <= 0L) return "0 menit"

    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L

    return when {
        hours == 0L -> "$minutes menit"
        minutes == 0L -> "$hours jam"
        else -> "$hours jam $minutes menit"
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
    todayDeviceTotalMillis: Long = todayUsageMap.values.sum(),
    yesterdayDeviceTotalMillis: Long = yesterdayUsageMap.values.sum(),
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
            todayDeviceTotalMillis = 0L,
            yesterdaySameTimeMillis = 0L,
            todayTotalDurationText = "0m",
            monitoredTotalDurationText = "0m",
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
            todayDeviceTotalMillis = todayDeviceTotalMillis,
            yesterdaySameTimeMillis = 0L,
            todayTotalDurationText = "0m",
            monitoredTotalDurationText = "0m",
            comparisonChangeText = "Pilih aplikasi yang ingin dipantau",
            isTrendingDown = true,
        )
    }

    val monitoredTodayTotalMillis = monitoredPackages.sumOf { pkg -> todayUsageMap[pkg] ?: 0L }
    val monitoredYesterdayTotalMillis = monitoredPackages.sumOf { pkg -> yesterdayUsageMap[pkg] ?: 0L }

    // Sesuai keputusan produk (Master Document 7.2 & 6.4):
    // Total waktu layar pada kartu utama HANYA menjumlahkan aplikasi yang dipilih pengguna untuk dipantau.
    // Aplikasi yang tidak dipantau tidak boleh ikut dijumlahkan.
    // Perbandingan "dari kemarin" menggunakan basis yang sama (hanya aplikasi terpantau).
    val (changeText, isTrendingDown) = formatScreenTimeComparison(
        hasUsagePermission = true,
        todayMillis = monitoredTodayTotalMillis,
        yesterdayMillis = monitoredYesterdayTotalMillis,
    )

    val monitoredDurationText = formatScreenTimeShort(monitoredTodayTotalMillis)

    return ScreenTimeSummary(
        hasUsagePermission = true,
        hasOverlayPermission = hasOverlayPermission,
        appUsageMillisMap = todayUsageMap,
        yesterdayUsageMillisMap = yesterdayUsageMap,
        todayTotalMillis = monitoredTodayTotalMillis,
        todayDeviceTotalMillis = todayDeviceTotalMillis,
        yesterdaySameTimeMillis = monitoredYesterdayTotalMillis,
        todayTotalDurationText = monitoredDurationText,
        monitoredTotalDurationText = monitoredDurationText,
        comparisonChangeText = changeText,
        isTrendingDown = isTrendingDown,
    )
}

object ScreenTimeCalculator {

    private const val LOOKBACK_BUFFER_MILLIS = 6L * 60L * 60L * 1000L // 6 jam sebelum startTime untuk menangkap sesi lintas tengah malam
    const val DIAG_TAG = "JedaInScreenTime"

    @Volatile
    private var lastDiagnosticLogMillis: Long = 0L

    /**
     * Mengidentifikasi package Home Launcher bawaan perangkat agar waktu diam di Home screen
     * tidak ikut dihitung sebagai penggunaan aplikasi pada total screen time perangkat
     * (konsisten dengan perilaku Android Settings -> App Activity / Digital Wellbeing).
     */
    private fun resolveHomeLauncherPackages(context: Context): Set<String> {
        return runCatching {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            resolveInfos.mapNotNull { it.activityInfo?.packageName }.toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * Menghitung durasi penggunaan per package dalam rentang [startTime, endTime]
     * menggunakan UsageStatsManager.queryEvents() dan state machine kronologis.
     */
    fun queryPackageUsageMillis(
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long,
        ownPackageName: String,
        excludedPackages: Set<String> = emptySet(),
        enableDiagLog: Boolean = false,
        periodLabel: String = "TODAY",
    ): Map<String, Long> {
        if (endTime <= startTime) return emptyMap()

        // Ambil event mulai dari 6 jam sebelum startTime agar sesi yang sudah dimulai
        // tepat sebelum pukul 00:00 (lintas tengah malam) dapat direkonstruksi,
        // namun durasinya tetap di-clip pada [startTime, endTime].
        val queryStart = (startTime - LOOKBACK_BUFFER_MILLIS).coerceAtLeast(0L)
        val usageEvents = runCatching {
            usageStatsManager.queryEvents(queryStart, endTime)
        }.getOrNull() ?: return emptyMap()

        val liteEvents = ArrayList<UsageEventLite>()
        val event = UsageEvents.Event()
        val eventTypeCounts = if (enableDiagLog) HashMap<Int, Int>() else null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val pkg = event.packageName.orEmpty()
            if (pkg.isBlank()) continue

            val type = event.eventType
            // Hanya ambil event relevan untuk state machine agar efisien
            if (type == UsageEvents.Event.ACTIVITY_RESUMED ||
                type == UsageEvents.Event.ACTIVITY_PAUSED ||
                type == EVENT_ACTIVITY_STOPPED ||
                type == EVENT_SCREEN_INTERACTIVE ||
                type == EVENT_SCREEN_NON_INTERACTIVE ||
                type == EVENT_KEYGUARD_SHOWN ||
                type == EVENT_KEYGUARD_HIDDEN
            ) {
                liteEvents.add(
                    UsageEventLite(
                        packageName = pkg,
                        eventType = type,
                        timeStamp = event.timeStamp,
                        className = event.className.orEmpty(),
                    ),
                )
                if (eventTypeCounts != null) {
                    eventTypeCounts[type] = (eventTypeCounts[type] ?: 0) + 1
                }
            }
        }

        val sessionLogs = if (enableDiagLog) ArrayList<String>() else null
        val rawMap = computeForegroundMillis(
            events = liteEvents,
            startTime = startTime,
            endTime = endTime,
            ownPackage = ownPackageName,
            supportsScreenEvents = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
            diagnosticLog = if (enableDiagLog) { msg -> sessionLogs?.add(msg) } else null,
        )

        // Filter keluar launcher dan system UI dari peta penggunaan aplikasi
        val filteredMap = if (excludedPackages.isEmpty()) {
            rawMap
        } else {
            rawMap.filterKeys { pkg -> pkg !in excludedPackages && pkg != "com.android.systemui" }
        }

        if (enableDiagLog && BuildConfig.DEBUG) {
            val zoneId = ZoneId.systemDefault()
            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zoneId)
            Log.d(
                DIAG_TAG,
                "[$periodLabel] Query range: ${fmt.format(Instant.ofEpochMilli(startTime))} .. ${fmt.format(Instant.ofEpochMilli(endTime))} | " +
                    "Events read: ${liteEvents.size} | Types: $eventTypeCounts",
            )
            sessionLogs?.takeLast(40)?.forEach { logLine ->
                Log.d(DIAG_TAG, "[$periodLabel] $logLine")
            }
            val totalMillis = filteredMap.values.sum()
            val topPackages = filteredMap.entries
                .sortedByDescending { it.value }
                .take(10)
                .joinToString(", ") { "${it.key}=${formatMinutesAndSeconds(it.value)} (${it.value}ms)" }
            Log.d(
                DIAG_TAG,
                "[$periodLabel] Total calculated: ${formatMinutesAndSeconds(totalMillis)} (${totalMillis}ms) | Top packages: $topPackages",
            )
        }

        return filteredMap
    }

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
        val todayDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val todayStartMillis = todayDate
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val yesterdayStartMillis = todayDate
            .minusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        val shouldLogDiag = BuildConfig.DEBUG && (nowMillis - lastDiagnosticLogMillis >= 10_000L)
        if (shouldLogDiag) {
            lastDiagnosticLogMillis = nowMillis
        }

        val launcherPackages = resolveHomeLauncherPackages(context)
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

        val todayUsageMap = if (usageStatsManager != null) {
            queryPackageUsageMillis(
                usageStatsManager = usageStatsManager,
                startTime = todayStartMillis,
                endTime = nowMillis,
                ownPackageName = context.packageName,
                excludedPackages = launcherPackages,
                enableDiagLog = shouldLogDiag,
                periodLabel = "TODAY",
            )
        } else {
            emptyMap()
        }

        val yesterdayUsageMap = if (usageStatsManager != null) {
            queryPackageUsageMillis(
                usageStatsManager = usageStatsManager,
                startTime = yesterdayStartMillis,
                endTime = todayStartMillis,
                ownPackageName = context.packageName,
                excludedPackages = launcherPackages,
                enableDiagLog = shouldLogDiag,
                periodLabel = "YESTERDAY",
            )
        } else {
            emptyMap()
        }

        val todayDeviceTotalMillis = todayUsageMap.values.sum()
        val yesterdayDeviceTotalMillis = yesterdayUsageMap.values.sum()

        return buildScreenTimeSummaryFromMaps(
            hasUsagePermission = true,
            hasOverlayPermission = hasOverlayPermission,
            monitoredPackages = monitoredPackages,
            todayUsageMap = todayUsageMap,
            yesterdayUsageMap = yesterdayUsageMap,
            todayDeviceTotalMillis = todayDeviceTotalMillis,
            yesterdayDeviceTotalMillis = yesterdayDeviceTotalMillis,
        )
    }

    /**
     * Menghitung total durasi screen time seluruh aplikasi di perangkat hari ini (sejak 00:00 waktu lokal).
     * Menggunakan sumber kalkulasi tunggal queryPackageUsageMillis berbasis UsageEvents.
     */
    fun calculateTotalDeviceScreenTimeMillis(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val hasUsagePermission = PermissionHelper.hasUsageStatsPermission(context)
        if (!hasUsagePermission) {
            return 0L
        }

        val zoneId = ZoneId.systemDefault()
        val todayDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val todayStartMillis = todayDate
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        if (nowMillis <= todayStartMillis) {
            return 0L
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return 0L

        val launcherPackages = resolveHomeLauncherPackages(context)
        val todayUsageMap = queryPackageUsageMillis(
            usageStatsManager = usageStatsManager,
            startTime = todayStartMillis,
            endTime = nowMillis,
            ownPackageName = context.packageName,
            excludedPackages = launcherPackages,
            enableDiagLog = false,
            periodLabel = "NOTIF_TODAY",
        )

        return todayUsageMap.values.sum()
    }

    private fun formatMinutesAndSeconds(millis: Long): String {
        val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "${minutes}m ${seconds}s"
    }
}
