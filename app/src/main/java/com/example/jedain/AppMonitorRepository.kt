package com.example.jedain

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val RecommendedPackageKeywords = listOf(
    "tiktok",
    "musically",
    "trill",
    "instagram",
    "youtube",
    "facebook",
    "twitter",
    "threads",
    "barcelona",
    "shopee",
    "netflix",
    "reddit",
    "snackvideo",
    "kwai",
    "x.com",
)

internal fun isRecommendedPackageOrCategory(
    packageName: String,
    category: Int,
): Boolean {
    val isRecommendedCategory = category == ApplicationInfo.CATEGORY_SOCIAL ||
        category == ApplicationInfo.CATEGORY_VIDEO ||
        category == ApplicationInfo.CATEGORY_GAME
    if (isRecommendedCategory) return true

    val lowerPackage = packageName.lowercase()
    return RecommendedPackageKeywords.any { keyword -> lowerPackage.contains(keyword) }
}

internal fun sortMonitoredApps(apps: List<MonitoredAppModel>): List<MonitoredAppModel> {
    return apps.sortedWith(
        compareByDescending<MonitoredAppModel> { it.isMonitored }
            .thenByDescending { it.isRecommended }
            .thenBy { it.appName.lowercase() },
    )
}

class AppMonitorRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasUsageStatsPermission(): Boolean = PermissionHelper.hasUsageStatsPermission(context)

    fun hasOverlayPermission(): Boolean = PermissionHelper.hasOverlayPermission(context)

    fun getMonitoredPackageSet(): Set<String> {
        val stored = prefs.getStringSet(KEY_MONITORED_PACKAGES, emptySet()).orEmpty()
        return HashSet(stored)
    }

    fun toggleMonitoredApp(packageName: String, isMonitored: Boolean): Set<String> {
        val currentSet = prefs.getStringSet(KEY_MONITORED_PACKAGES, emptySet()).orEmpty()
        val newSet = HashSet(currentSet)
        if (isMonitored) {
            newSet.add(packageName)
        } else {
            newSet.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_MONITORED_PACKAGES, newSet).apply()
        return HashSet(newSet)
    }

    suspend fun getMonitoredScreenTimeSummary(): ScreenTimeSummary = withContext(Dispatchers.IO) {
        val monitoredPackages = getMonitoredPackageSet()
        ScreenTimeCalculator.calculateScreenTimeSummary(
            context = context,
            monitoredPackages = monitoredPackages,
        )
    }

    suspend fun getTotalDeviceScreenTimeMillis(nowMillis: Long = System.currentTimeMillis()): Long = withContext(Dispatchers.IO) {
        ScreenTimeCalculator.calculateTotalDeviceScreenTimeMillis(context, nowMillis)
    }

    suspend fun getInstalledApps(): List<MonitoredAppModel> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val monitoredPackages = getMonitoredPackageSet()
        val ownPackageName = context.packageName

        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolveInfoList = packageManager.queryIntentActivities(mainIntent, 0)

        val baseApps = resolveInfoList
            .asSequence()
            .filter { it.activityInfo?.packageName != null && it.activityInfo.packageName != ownPackageName }
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val pkgName = activityInfo.packageName
                val appInfo = activityInfo.applicationInfo

                val appLabel = resolveInfo.loadLabel(packageManager).toString()
                    .takeIf { it.isNotBlank() }
                    ?: pkgName

                val iconBitmap: ImageBitmap = runCatching {
                    resolveInfo.loadIcon(packageManager)
                        .toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX)
                        .asImageBitmap()
                }.getOrElse {
                    ImageBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
                }

                val category = appInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED
                val isRecommended = isRecommendedPackageOrCategory(
                    packageName = pkgName,
                    category = category,
                )

                MonitoredAppModel(
                    appName = appLabel,
                    packageName = pkgName,
                    iconBitmap = iconBitmap,
                    isRecommended = isRecommended,
                    isMonitored = pkgName in monitoredPackages,
                )
            }
            .toList()

        sortMonitoredApps(baseApps)
    }

    companion object {
        const val PREFS_NAME = "jedain_prefs"
        const val KEY_MONITORED_PACKAGES = "monitored_packages"
        private const val ICON_SIZE_PX = 96
    }
}
