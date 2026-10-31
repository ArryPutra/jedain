package com.example.jedain

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.drawable.toBitmap

enum class WidgetTargetTier {
    NORMAL,   // Penggunaan < 90% target (Hijau)
    WARNING,  // Penggunaan 90% - 100% target (Kuning/Amber)
    EXCEEDED, // Penggunaan > 100% target (Merah)
}

data class DailyAwarenessWidgetState(
    val hasUsagePermission: Boolean,
    val totalScreenTimeText: String,
    val targetRatioText: String,
    val visualProgressPercent: Int, // Maksimal 100% secara visual
    val actualProgressPercent: Double, // Persentase aktual tanpa batas
    val targetTier: WidgetTargetTier,
    val exceededDiffText: String, // Selisih waktu e.g. "+2j 34m"
    val topAppInfoText: String,
    val topAppIconBitmap: Bitmap?,
    val isTargetSet: Boolean,
)

object DailyAwarenessWidgetDataHelper {

    private const val TAG = "JedaInWidgetData"
    private const val TOP_APP_ICON_SIZE_PX = 48

    // Cache valid data terakhir untuk mencegah widget berkedip atau menampilkan state rusak jika terjadi kegagalan baca
    @Volatile
    private var lastValidState: DailyAwarenessWidgetState? = null

    fun getWidgetData(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): DailyAwarenessWidgetState {
        return runCatching {
            val hasUsagePermission = PermissionHelper.hasUsageStatsPermission(context)
            if (!hasUsagePermission) {
                return DailyAwarenessWidgetState(
                    hasUsagePermission = false,
                    totalScreenTimeText = "0m",
                    targetRatioText = context.getString(R.string.widget_default_target),
                    visualProgressPercent = 0,
                    actualProgressPercent = 0.0,
                    targetTier = WidgetTargetTier.NORMAL,
                    exceededDiffText = "",
                    topAppInfoText = context.getString(R.string.widget_no_activity),
                    topAppIconBitmap = null,
                    isTargetSet = true,
                )
            }

            // 1. Ambil target harian dari preferensi
            val targetPreferences = TargetSettingsPreferences(context)
            val settings = targetPreferences.loadSettings()
            val targetMinutes = settings.validatedDailyTargetMinutes
            val isTargetSet = targetMinutes > 0
            val targetMillis = targetMinutes * 60_000L

            // 2. Ambil ringkasan waktu layar hari ini dari ScreenTimeCalculator (sumber tunggal JedaIn)
            val summary = ScreenTimeCalculator.calculateScreenTimeSummary(
                context = context,
                monitoredPackages = emptySet(),
                nowMillis = nowMillis,
            )

            val todayTotalMillis = summary.todayDeviceTotalMillis
            val totalScreenTimeText = formatScreenTimeShort(todayTotalMillis)

            // 3. Hitung persentase penggunaan aktual dan visual
            val actualProgressPercent = if (isTargetSet) {
                (todayTotalMillis.toDouble() / targetMillis.toDouble()) * 100.0
            } else {
                0.0
            }

            // Progress bar maksimal 100% secara visual
            val visualProgressPercent = actualProgressPercent.toInt().coerceIn(0, 100)

            // Tentukan tier kondisi:
            // Normal: < 90% target
            // Warning: 90% - 100% target
            // Exceeded: > 100% target
            val targetTier = when {
                !isTargetSet -> WidgetTargetTier.NORMAL
                actualProgressPercent < 90.0 -> WidgetTargetTier.NORMAL
                actualProgressPercent <= 100.0 -> WidgetTargetTier.WARNING
                else -> WidgetTargetTier.EXCEEDED
            }

            // Hitung selisih durasi terhadap target
            val diffMillis = todayTotalMillis - targetMillis
            val exceededDiffText = if (isTargetSet && diffMillis > 0L) {
                "+${formatScreenTimeShort(diffMillis)}"
            } else {
                ""
            }

            // 4. Format teks target dan informasi selisih
            val targetRatioText = if (isTargetSet) {
                val targetDurationText = formatScreenTimeShort(targetMillis)
                if (targetTier == WidgetTargetTier.EXCEEDED) {
                    "$exceededDiffText / $targetDurationText"
                } else {
                    "$totalScreenTimeText / $targetDurationText"
                }
            } else {
                context.getString(R.string.widget_target_not_set)
            }

            // 5. Hitung aplikasi teratas hari ini berdasarkan durasi aktual
            val ownPackage = context.packageName
            val topEntry = summary.appUsageMillisMap
                .asSequence()
                .filter { (pkg, millis) ->
                    pkg != ownPackage &&
                        pkg != "com.android.systemui" &&
                        millis > 0L
                }
                .maxByOrNull { it.value }

            val pm = context.packageManager
            val (topAppInfoText, topAppIconBitmap) = if (topEntry != null) {
                val pkg = topEntry.key
                val durationText = formatScreenTimeShort(topEntry.value)
                val label = runCatching {
                    @Suppress("DEPRECATION")
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString().ifBlank { pkg }
                }.getOrDefault(pkg)

                val icon = runCatching {
                    @Suppress("DEPRECATION")
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val drawable = pm.getApplicationIcon(appInfo)
                    // Downsample icon ke ukuran 48x48 px untuk menjaga memori RemoteViews di bawah batas sistem Android
                    drawable.toBitmap(
                        width = TOP_APP_ICON_SIZE_PX,
                        height = TOP_APP_ICON_SIZE_PX,
                        config = Bitmap.Config.ARGB_8888,
                    )
                }.getOrNull()

                "$label · $durationText" to icon
            } else {
                context.getString(R.string.widget_no_activity) to null
            }

            val newState = DailyAwarenessWidgetState(
                hasUsagePermission = true,
                totalScreenTimeText = totalScreenTimeText,
                targetRatioText = targetRatioText,
                visualProgressPercent = visualProgressPercent,
                actualProgressPercent = actualProgressPercent,
                targetTier = targetTier,
                exceededDiffText = exceededDiffText,
                topAppInfoText = topAppInfoText,
                topAppIconBitmap = topAppIconBitmap,
                isTargetSet = isTargetSet,
            )

            lastValidState = newState
            newState
        }.getOrElse { throwable ->
            Log.e(TAG, "Error generating widget data, falling back to last valid state", throwable)
            lastValidState ?: DailyAwarenessWidgetState(
                hasUsagePermission = PermissionHelper.hasUsageStatsPermission(context),
                totalScreenTimeText = "0m",
                targetRatioText = context.getString(R.string.widget_default_target),
                visualProgressPercent = 0,
                actualProgressPercent = 0.0,
                targetTier = WidgetTargetTier.NORMAL,
                exceededDiffText = "",
                topAppInfoText = context.getString(R.string.widget_no_activity),
                topAppIconBitmap = null,
                isTargetSet = true,
            )
        }
    }
}
