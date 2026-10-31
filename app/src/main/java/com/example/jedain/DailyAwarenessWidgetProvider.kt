package com.example.jedain

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.RemoteViews

class DailyAwarenessWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH_WIDGET,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            -> {
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        private const val TAG = "DailyAwarenessWidget"
        const val ACTION_REFRESH_WIDGET = "com.example.jedain.action.REFRESH_DAILY_WIDGET"
        private const val REQUEST_CODE_OPEN_APP = 1001
        private const val REQUEST_CODE_OPEN_SETTINGS = 1002

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            runCatching {
                val views = RemoteViews(context.packageName, R.layout.widget_daily_awareness)
                val state = DailyAwarenessWidgetDataHelper.getWidgetData(context)

                // PendingIntent untuk membuka halaman utama JedaIn (Statistik / HomeScreen)
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openAppPendingIntent = PendingIntent.getActivity(
                    context,
                    REQUEST_CODE_OPEN_APP,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                if (!state.hasUsagePermission) {
                    // Kondisi: Izin Akses Penggunaan belum diberikan
                    views.setViewVisibility(R.id.widget_content_container, View.GONE)
                    views.setViewVisibility(R.id.widget_permission_container, View.VISIBLE)

                    // Mengetuk saat belum ada izin langsung mengarahkan pengguna ke Pengaturan Akses Penggunaan
                    val packageUri = Uri.fromParts("package", context.packageName, null)
                    val settingsIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    val settingsPendingIntent = PendingIntent.getActivity(
                        context,
                        REQUEST_CODE_OPEN_SETTINGS,
                        settingsIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )

                    views.setOnClickPendingIntent(R.id.widget_root, settingsPendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_permission_container, settingsPendingIntent)
                } else {
                    // Kondisi: Normal / Data tersedia
                    views.setViewVisibility(R.id.widget_content_container, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_permission_container, View.GONE)

                    // 1. Total waktu layar hari ini (paling menonjol)
                    views.setTextViewText(R.id.widget_tv_total_time, state.totalScreenTimeText)

                    // 2. Target penggunaan harian (contoh: 4j 12m / 5j atau +2j 34m / 3j)
                    views.setTextViewText(R.id.widget_tv_target, state.targetRatioText)

                    // Warna dinamis sesuai tier kondisi penggunaan terhadap target
                    val textPrimaryColor = context.getColor(R.color.widget_text_primary)
                    val textSecondaryColor = context.getColor(R.color.widget_text_secondary)
                    val warningColor = context.getColor(R.color.widget_status_warning)
                    val exceededColor = context.getColor(R.color.widget_status_exceeded)

                    when (state.targetTier) {
                        WidgetTargetTier.NORMAL -> {
                            // Normal (< 90%): Angka utama normal, progress bar hijau
                            views.setTextColor(R.id.widget_tv_total_time, textPrimaryColor)
                            views.setTextColor(R.id.widget_tv_target, textSecondaryColor)
                            views.setViewVisibility(R.id.widget_tv_exceeded_badge, View.GONE)

                            views.setViewVisibility(R.id.widget_progress_bar_normal, View.VISIBLE)
                            views.setViewVisibility(R.id.widget_progress_bar_warning, View.GONE)
                            views.setViewVisibility(R.id.widget_progress_bar_exceeded, View.GONE)
                            views.setProgressBar(
                                R.id.widget_progress_bar_normal,
                                100,
                                state.visualProgressPercent,
                                false,
                            )
                        }
                        WidgetTargetTier.WARNING -> {
                            // Warning (90% - 100%): Progress bar kuning/amber, teks target amber
                            views.setTextColor(R.id.widget_tv_total_time, textPrimaryColor)
                            views.setTextColor(R.id.widget_tv_target, warningColor)
                            views.setViewVisibility(R.id.widget_tv_exceeded_badge, View.GONE)

                            views.setViewVisibility(R.id.widget_progress_bar_normal, View.GONE)
                            views.setViewVisibility(R.id.widget_progress_bar_warning, View.VISIBLE)
                            views.setViewVisibility(R.id.widget_progress_bar_exceeded, View.GONE)
                            views.setProgressBar(
                                R.id.widget_progress_bar_warning,
                                100,
                                state.visualProgressPercent,
                                false,
                            )
                        }
                        WidgetTargetTier.EXCEEDED -> {
                            // Exceeded (> 100%): Angka utama merah, selisih waktu merah, label "Target terlampaui" merah, progress bar merah
                            views.setTextColor(R.id.widget_tv_total_time, exceededColor)
                            views.setTextColor(R.id.widget_tv_target, exceededColor)

                            views.setViewVisibility(R.id.widget_tv_exceeded_badge, View.VISIBLE)
                            views.setTextColor(R.id.widget_tv_exceeded_badge, exceededColor)

                            views.setViewVisibility(R.id.widget_progress_bar_normal, View.GONE)
                            views.setViewVisibility(R.id.widget_progress_bar_warning, View.GONE)
                            views.setViewVisibility(R.id.widget_progress_bar_exceeded, View.VISIBLE)
                            views.setProgressBar(
                                R.id.widget_progress_bar_exceeded,
                                100,
                                100,
                                false,
                            )
                        }
                    }

                    // 3. Aplikasi teratas (ikon, nama, durasi penggunaan)
                    views.setTextViewText(R.id.widget_tv_top_app_info, state.topAppInfoText)
                    if (state.topAppIconBitmap != null) {
                        views.setViewVisibility(R.id.widget_iv_top_app_icon, View.VISIBLE)
                        views.setImageViewBitmap(R.id.widget_iv_top_app_icon, state.topAppIconBitmap)
                    } else {
                        views.setViewVisibility(R.id.widget_iv_top_app_icon, View.GONE)
                    }

                    // 4. Interaksi klik area utama: Membuka halaman statistik JedaIn
                    views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }.onFailure { throwable ->
                Log.e(TAG, "Gagal memperbarui widget id=$appWidgetId", throwable)
            }
        }

        fun updateAllWidgets(context: Context) {
            runCatching {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val componentName = ComponentName(context, DailyAwarenessWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                }
            }.onFailure { throwable ->
                Log.e(TAG, "Gagal memicu pembaruan seluruh widget", throwable)
            }
        }
    }
}
