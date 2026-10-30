package com.example.jedain

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jedain.ui.theme.JedaInTheme

@Composable
fun ManageMonitoredAppsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonitoredAppsViewModel = rememberMonitoredAppsViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(onBack = onBackClick)

    ManageMonitoredAppsScreenContent(
        uiState = uiState,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onToggleApp = viewModel::onToggleApp,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

@Composable
fun ManageMonitoredAppsScreenContent(
    uiState: MonitoredAppsUiState,
    onSearchQueryChange: (String) -> Unit,
    onToggleApp: (packageName: String, isMonitored: Boolean) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Header dengan ringkasan jumlah aplikasi yang dipantau
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.background,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.divider),
                        modifier = Modifier.size(40.dp),
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                    ) {
                        Text(
                            text = "Aplikasi yang Dipantau",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            letterSpacing = (-0.3).sp,
                        )
                        Text(
                            text = "Pilih aplikasi yang ingin dibatasi penggunaannya.",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.targetCard,
                        border = BorderStroke(1.dp, colors.border),
                    ) {
                        Text(
                            text = "${uiState.monitoredCount} dipilih",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                // Search Bar (OutlinedTextField) dengan gaya senada JedaIn
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "Cari aplikasi...",
                            fontSize = 14.sp,
                            color = colors.textSecondary,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Hapus pencarian",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                        .testTag("search_monitored_apps_input"),
                )

                HorizontalDivider(
                    color = colors.divider,
                    thickness = 1.dp,
                )
            }
        }

        // Body: Loading indicator atau LazyColumn daftar aplikasi terinstal
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("monitored_apps_loading"),
                )
            }
        } else if (uiState.filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (uiState.searchQuery.isNotBlank()) {
                        "Tidak ada aplikasi yang cocok dengan \"${uiState.searchQuery}\"."
                    } else {
                        "Tidak ada aplikasi terinstal yang ditemukan."
                    },
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("monitored_apps_lazy_column"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = uiState.filteredApps,
                    key = { it.packageName },
                ) { app ->
                    SelectableMonitoredAppItem(
                        app = app,
                        onToggle = { isChecked ->
                            onToggleApp(app.packageName, isChecked)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableMonitoredAppItem(
    app: MonitoredAppModel,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Switch) {
                onToggle(!app.isMonitored)
            },
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(
            width = if (app.isMonitored) 1.5.dp else 1.dp,
            color = if (app.isMonitored) colors.accent else colors.border,
        ),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.targetCard,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                    ) {
                        Image(
                            bitmap = app.iconBitmap,
                            contentDescription = app.appName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = app.appName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )

                        if (app.isRecommended) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.targetCard,
                            ) {
                                Text(
                                    text = "Disarankan",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Text(
                        text = if (app.isMonitored) "Dipantau aktif" else app.packageName,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = app.isMonitored,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.surface,
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedTrackColor = colors.background,
                    uncheckedBorderColor = colors.border,
                ),
            )
        }
    }
}
