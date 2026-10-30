package com.example.jedain

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class MonitoredAppsUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val allApps: List<MonitoredAppModel> = emptyList(),
    val filteredApps: List<MonitoredAppModel> = emptyList(),
    val monitoredCount: Int = 0,
    val hasUsageAccessPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val todayTotalDurationText: String = "0m",
    val comparisonChangeText: String = "Memuat perbandingan waktu layar...",
    val isTrendingDown: Boolean = true,
)

internal fun filterAppsByQuery(
    apps: List<MonitoredAppModel>,
    query: String,
): List<MonitoredAppModel> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return apps
    return apps.filter { app ->
        app.appName.contains(trimmed, ignoreCase = true) ||
            app.packageName.contains(trimmed, ignoreCase = true)
    }
}

class MonitoredAppsViewModel(
    private val repository: AppMonitorRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MonitoredAppsUiState(
            hasUsageAccessPermission = repository.hasUsageStatsPermission(),
            hasOverlayPermission = repository.hasOverlayPermission(),
        ),
    )
    val uiState: StateFlow<MonitoredAppsUiState> = _uiState.asStateFlow()

    private var loadOrRefreshJob: Job? = null
    private var cachedTodayUsageMap: Map<String, Long> = emptyMap()
    private var cachedYesterdayUsageMap: Map<String, Long> = emptyMap()

    init {
        loadApps()
    }

    fun loadApps() {
        if (loadOrRefreshJob?.isActive == true) return

        loadOrRefreshJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    hasUsageAccessPermission = repository.hasUsageStatsPermission(),
                    hasOverlayPermission = repository.hasOverlayPermission(),
                )
            }
            val installedApps = repository.getInstalledApps()
            val summary = repository.getMonitoredScreenTimeSummary()
            cachedTodayUsageMap = summary.appUsageMillisMap
            cachedYesterdayUsageMap = summary.yesterdayUsageMillisMap

            val enrichedApps = installedApps.map { app ->
                val usageMillis = summary.appUsageMillisMap[app.packageName] ?: 0L
                app.copy(
                    todayUsageMillis = usageMillis,
                    formattedDuration = formatScreenTimeShort(usageMillis),
                )
            }

            _uiState.update { current ->
                val filtered = filterAppsByQuery(enrichedApps, current.searchQuery)
                current.copy(
                    isLoading = false,
                    allApps = enrichedApps,
                    filteredApps = filtered,
                    monitoredCount = enrichedApps.count { it.isMonitored },
                    hasUsageAccessPermission = summary.hasUsagePermission,
                    hasOverlayPermission = summary.hasOverlayPermission,
                    todayTotalDurationText = summary.todayTotalDurationText,
                    comparisonChangeText = summary.comparisonChangeText,
                    isTrendingDown = summary.isTrendingDown,
                )
            }
        }
    }

    fun refreshPermissionsAndUsage() {
        if (loadOrRefreshJob?.isActive == true) return
        if (_uiState.value.allApps.isEmpty()) {
            loadApps()
            return
        }

        loadOrRefreshJob = viewModelScope.launch {
            val monitoredSet = repository.getMonitoredPackageSet()
            val summary = repository.getMonitoredScreenTimeSummary()
            cachedTodayUsageMap = summary.appUsageMillisMap
            cachedYesterdayUsageMap = summary.yesterdayUsageMillisMap

            _uiState.update { current ->
                val updatedAllApps = current.allApps.map { app ->
                    val usageMillis = summary.appUsageMillisMap[app.packageName] ?: 0L
                    app.copy(
                        isMonitored = app.packageName in monitoredSet,
                        todayUsageMillis = usageMillis,
                        formattedDuration = formatScreenTimeShort(usageMillis),
                    )
                }
                val updatedFiltered = filterAppsByQuery(updatedAllApps, current.searchQuery)
                current.copy(
                    allApps = updatedAllApps,
                    filteredApps = updatedFiltered,
                    monitoredCount = updatedAllApps.count { it.isMonitored },
                    hasUsageAccessPermission = summary.hasUsagePermission,
                    hasOverlayPermission = summary.hasOverlayPermission,
                    todayTotalDurationText = summary.todayTotalDurationText,
                    comparisonChangeText = summary.comparisonChangeText,
                    isTrendingDown = summary.isTrendingDown,
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredApps = filterAppsByQuery(current.allApps, query),
            )
        }
    }

    fun onToggleApp(packageName: String, isMonitored: Boolean) {
        val updatedMonitoredSet = repository.toggleMonitoredApp(packageName, isMonitored)

        // Optimistic update + local recalculation from cached maps without querying UsageStatsManager
        _uiState.update { current ->
            val updatedAllApps = current.allApps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(isMonitored = isMonitored)
                } else {
                    app
                }
            }
            val updatedFilteredApps = filterAppsByQuery(updatedAllApps, current.searchQuery)
            val localSummary = buildScreenTimeSummaryFromMaps(
                hasUsagePermission = current.hasUsageAccessPermission,
                hasOverlayPermission = current.hasOverlayPermission,
                monitoredPackages = updatedMonitoredSet,
                todayUsageMap = cachedTodayUsageMap,
                yesterdayUsageMap = cachedYesterdayUsageMap,
            )

            current.copy(
                allApps = updatedAllApps,
                filteredApps = updatedFilteredApps,
                monitoredCount = updatedAllApps.count { it.isMonitored },
                todayTotalDurationText = localSummary.todayTotalDurationText,
                comparisonChangeText = localSummary.comparisonChangeText,
                isTrendingDown = localSummary.isTrendingDown,
            )
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MonitoredAppsViewModel::class.java)) {
                return MonitoredAppsViewModel(
                    repository = AppMonitorRepository(context.applicationContext),
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

@Composable
fun rememberMonitoredAppsViewModel(): MonitoredAppsViewModel {
    val context = LocalContext.current
    val owner = context as? ViewModelStoreOwner
    return remember(context, owner) {
        val factory = MonitoredAppsViewModel.Factory(context)
        if (owner != null) {
            ViewModelProvider(owner, factory)[MonitoredAppsViewModel::class.java]
        } else {
            factory.create(MonitoredAppsViewModel::class.java)
        }
    }
}
