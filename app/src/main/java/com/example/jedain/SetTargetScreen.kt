package com.example.jedain

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jedain.ui.theme.JedaInTheme

// --- Models & Static Options ---

@Immutable
data class DailyTargetOption(
    val hours: Int,
    val title: String,
    val subtitle: String,
)

private val DailyTargetOptions = listOf(
    DailyTargetOption(hours = 1, title = "1 jam", subtitle = "Rendah"),
    DailyTargetOption(hours = 2, title = "2 jam", subtitle = "Baik untuk dicoba"),
    DailyTargetOption(hours = 3, title = "3 jam", subtitle = "Target moderat"),
    DailyTargetOption(hours = 4, title = "4 jam", subtitle = "Mulai tinggi"),
    DailyTargetOption(hours = 5, title = "5 jam", subtitle = "Tinggi"),
    DailyTargetOption(hours = 6, title = "6 jam", subtitle = "Sangat tinggi"),
)

private val SessionDurationOptions = listOf(5, 10, 15, 30)

// --- Stateful SetTargetScreen ---

@Composable
fun SetTargetScreen(
    initialSettings: TargetSettingsState,
    onBackClick: () -> Unit,
    onSaveSettings: (TargetSettingsState) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTargetHours by rememberSaveable(initialSettings.dailyTargetHours) {
        mutableIntStateOf(initialSettings.dailyTargetHours)
    }
    var selectedSessionMinutes by rememberSaveable(initialSettings.sessionDurationMinutes) {
        mutableIntStateOf(initialSettings.sessionDurationMinutes)
    }
    var reminderEnabled by rememberSaveable(initialSettings.reminderOnSessionEnd) {
        mutableStateOf(initialSettings.reminderOnSessionEnd)
    }

    BackHandler(onBack = onBackClick)

    SetTargetScreenContent(
        selectedTargetHours = selectedTargetHours,
        onTargetHoursSelected = { selectedTargetHours = it },
        selectedSessionMinutes = selectedSessionMinutes,
        onSessionMinutesSelected = { selectedSessionMinutes = it },
        reminderEnabled = reminderEnabled,
        onReminderToggleChanged = { reminderEnabled = it },
        onBackClick = onBackClick,
        onSaveClick = {
            onSaveSettings(
                TargetSettingsState(
                    dailyTargetHours = selectedTargetHours,
                    sessionDurationMinutes = selectedSessionMinutes,
                    reminderOnSessionEnd = reminderEnabled,
                ),
            )
        },
        modifier = modifier,
    )
}

// --- Stateless SetTargetScreenContent ---

@Composable
fun SetTargetScreenContent(
    selectedTargetHours: Int,
    onTargetHoursSelected: (Int) -> Unit,
    selectedSessionMinutes: Int,
    onSessionMinutesSelected: (Int) -> Unit,
    reminderEnabled: Boolean,
    onReminderToggleChanged: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors
    val scrollState = rememberScrollState()
    val isScrolled = scrollState.canScrollBackward

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // 3. Header Nyangkut (Fixed) di atas
        SetTargetHeader(
            onBackClick = onBackClick,
            showDivider = isScrolled,
        )

        // Konten Scrollable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 4. Bagian Target Harian
            DailyTargetSettingCard(
                selectedHours = selectedTargetHours,
                onSelectHours = onTargetHoursSelected,
            )

            // 5. Bagian Sesi Jeda Waktu
            SessionBreakSettingCard(
                selectedMinutes = selectedSessionMinutes,
                onSelectMinutes = onSessionMinutesSelected,
                reminderEnabled = reminderEnabled,
                onReminderToggleChanged = onReminderToggleChanged,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 6. Tombol Simpan
            Button(
                onClick = onSaveClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.surface,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = "Simpan pengaturan",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.surface,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// --- Section 3: Header ---

@Composable
private fun SetTargetHeader(
    onBackClick: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
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
                        text = "Atur Target",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        letterSpacing = (-0.3).sp,
                    )
                    Text(
                        text = "Bangun kebiasaan digital yang lebih sehat.",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                    )
                }
            }

            if (showDivider) {
                HorizontalDivider(
                    color = colors.divider,
                    thickness = 1.dp,
                )
            }
        }
    }
}

// --- Section 4: Bagian Target Harian ---

@Composable
private fun DailyTargetSettingCard(
    selectedHours: Int,
    onSelectHours: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors
    val activeOption = DailyTargetOptions.find { option -> option.hours == selectedHours }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.divider),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            // Card Header
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.targetCard,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.WbSunny,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Target harian",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Batas total waktu penggunaan aplikasi yang dipantau.",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        lineHeight = 18.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Active Target Value Display
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = colors.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "$selectedHours jam",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = (-0.6).sp,
                    )
                    if (activeOption != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = activeOption.subtitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accent,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2-Column Grid of 6 Target Options
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DailyTargetOptions.chunked(2).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowOptions.forEach { option ->
                            val isSelected = option.hours == selectedHours
                            TargetOptionGridItem(
                                title = option.title,
                                subtitle = option.subtitle,
                                isSelected = isSelected,
                                onClick = { onSelectHours(option.hours) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Informative Gentle Notice for 4, 5, or 6 hours
            AnimatedVisibility(
                visible = selectedHours >= 4,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = colors.warningBackground,
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(18.dp),
                            )
                            Text(
                                text = "Target ini tergolong tinggi. Jika ingin mengurangi waktu scrolling, kamu bisa mencoba target yang lebih rendah secara bertahap.",
                                fontSize = 13.sp,
                                color = colors.textPrimary,
                                lineHeight = 19.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetOptionGridItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent else colors.background,
        label = "TargetOptionBg",
    )
    val titleColor by animateColorAsState(
        targetValue = if (isSelected) colors.surface else colors.textPrimary,
        label = "TargetOptionTitle",
    )
    val subtitleColor by animateColorAsState(
        targetValue = if (isSelected) colors.surface.copy(alpha = 0.85f) else colors.textSecondary,
        label = "TargetOptionSubtitle",
    )
    val borderColor = if (isSelected) colors.accent else colors.divider

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = subtitleColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// --- Section 5: Bagian Sesi Jeda Waktu ---

@Composable
private fun SessionBreakSettingCard(
    selectedMinutes: Int,
    onSelectMinutes: (Int) -> Unit,
    reminderEnabled: Boolean,
    onReminderToggleChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.divider),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            // Card Header
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.targetCard,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sesi jeda waktu",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tentukan kapan JedaIn mengingatkanmu untuk berhenti sejenak saat menggunakan aplikasi yang dipantau.",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        lineHeight = 18.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Active Session Duration Display
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = colors.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "$selectedMinutes menit",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = (-0.6).sp,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Durasi per sesi penggunaan",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.accent,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2x2 Grid for 4 Session Duration Options (5, 10, 15, 30 menit)
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SessionDurationOptions.chunked(2).forEach { rowMinutes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowMinutes.forEach { minutes ->
                            val isSelected = minutes == selectedMinutes
                            SessionOptionChipItem(
                                label = "$minutes menit",
                                isSelected = isSelected,
                                onClick = { onSelectMinutes(minutes) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = colors.divider, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Reminder Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Switch) {
                        onReminderToggleChanged(!reminderEnabled)
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pengingat saat sesi berakhir",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tampilkan layar jeda saat batas waktu tercapai.",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        lineHeight = 17.sp,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = reminderEnabled,
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
}

@Composable
private fun SessionOptionChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent else colors.background,
        label = "SessionOptionBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) colors.surface else colors.textPrimary,
        label = "SessionOptionText",
    )
    val borderColor = if (isSelected) colors.accent else colors.divider

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SetTargetScreenPreview() {
    JedaInTheme {
        SetTargetScreen(
            initialSettings = TargetSettingsState(
                dailyTargetHours = 3,
                sessionDurationMinutes = 10,
                reminderOnSessionEnd = true,
            ),
            onBackClick = {},
            onSaveSettings = {},
        )
    }
}
