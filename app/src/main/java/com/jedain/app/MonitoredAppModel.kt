package com.jedain.app

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

@Immutable
data class MonitoredAppModel(
    val appName: String,
    val packageName: String,
    val iconBitmap: ImageBitmap,
    val isRecommended: Boolean,
    val isMonitored: Boolean,
    val todayUsageMillis: Long = 0L,
    val formattedDuration: String = "0m",
)
