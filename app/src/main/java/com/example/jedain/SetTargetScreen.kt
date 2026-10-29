package com.example.jedain

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jedain.ui.theme.JedaInColorPalette
import com.example.jedain.ui.theme.JedaInTheme

// --- Models & Static Options ---

enum class DailyTargetColorTier {
    GREEN,
    YELLOW,
    ORANGE,
    RED,
    CUSTOM_BLUE,
}

@Immutable
data class DailyTargetPresetOption(
    val minutes: Int,
    val title: String,
    val category: String,
    val description: String,
    val colorTier: DailyTargetColorTier,
)

@Immutable
data class DailyTargetFeedback(
    val durationText: String,
    val category: String,
    val description: String,
    val accentColor: Color,
    val softBgColor: Color,
)

internal val DailyTargetPresetOptions = listOf(
    DailyTargetPresetOption(
        minutes = 30,
        title = "30 menit",
        category = "Sangat terkontrol",
        description = "Target singkat untuk menjaga waktu hiburan tetap terbatas.",
        colorTier = DailyTargetColorTier.GREEN,
    ),
    DailyTargetPresetOption(
        minutes = 60,
        title = "1 jam",
        category = "Terkendali",
        description = "Nikmati hiburan tanpa terlalu mengurangi waktu untuk kegiatan lain.",
        colorTier = DailyTargetColorTier.GREEN,
    ),
    DailyTargetPresetOption(
        minutes = 120,
        title = "2 jam",
        category = "Perlu diperhatikan",
        description = "Jaga agar scrolling tidak mengambil alih waktu produktif.",
        colorTier = DailyTargetColorTier.YELLOW,
    ),
    DailyTargetPresetOption(
        minutes = 180,
        title = "3 jam",
        category = "Cukup tinggi",
        description = "Durasi ini cukup tinggi. Pertimbangkan menguranginya secara bertahap.",
        colorTier = DailyTargetColorTier.ORANGE,
    ),
)

private val HighDailyTargetTierOption = DailyTargetPresetOption(
    minutes = 240,
    title = "4 jam",
    category = "Target tinggi",
    description = "Empat jam sehari merupakan target yang tinggi. Pertimbangkan durasi lebih rendah.",
    colorTier = DailyTargetColorTier.RED,
)

private val SessionDurationOptions = listOf(5, 10, 15, 30)

internal fun resolveTierOption(minutes: Int): DailyTargetPresetOption {
    val validated = validateDailyTargetMinutes(minutes)
    return when {
        validated <= 30 -> DailyTargetPresetOptions[0]
        validated <= 60 -> DailyTargetPresetOptions[1]
        validated <= 120 -> DailyTargetPresetOptions[2]
        validated <= 180 -> DailyTargetPresetOptions[3]
        else -> HighDailyTargetTierOption
    }
}

internal fun resolveColorPair(
    tier: DailyTargetColorTier,
    colors: JedaInColorPalette,
): Pair<Color, Color> {
    return when (tier) {
        DailyTargetColorTier.GREEN -> colors.durationGreen to colors.durationGreenBg
        DailyTargetColorTier.YELLOW -> colors.durationYellow to colors.durationYellowBg
        DailyTargetColorTier.ORANGE -> colors.durationOrange to colors.durationOrangeBg
        DailyTargetColorTier.RED -> colors.durationRed to colors.durationRedBg
        DailyTargetColorTier.CUSTOM_BLUE -> colors.durationCustomBlue to colors.durationCustomBlueBg
    }
}

internal fun resolveDailyTargetFeedback(
    minutes: Int,
    isCustom: Boolean,
    colors: JedaInColorPalette,
): DailyTargetFeedback {
    val validated = validateDailyTargetMinutes(minutes)
    val tierOption = resolveTierOption(validated)
    val (accentColor, softBgColor) = if (isCustom) {
        resolveColorPair(DailyTargetColorTier.CUSTOM_BLUE, colors)
    } else {
        resolveColorPair(tierOption.colorTier, colors)
    }
    return DailyTargetFeedback(
        durationText = formatDailyTargetDuration(validated),
        category = tierOption.category,
        description = tierOption.description,
        accentColor = accentColor,
        softBgColor = softBgColor,
    )
}

// --- Stateful SetTargetScreen ---

@Composable
fun SetTargetScreen(
    initialSettings: TargetSettingsState,
    onBackClick: () -> Unit,
    onSaveSettings: (TargetSettingsState) -> Unit,
    modifier: Modifier = Modifier,
    onTargetSettingsChanged: (TargetSettingsState) -> Unit = {},
) {
    var selectedTargetMinutes by rememberSaveable(
        initialSettings.dailyTargetMinutes,
        initialSettings.isCustomDailyTarget,
    ) {
        mutableIntStateOf(initialSettings.validatedDailyTargetMinutes)
    }
    var isCustomTarget by rememberSaveable(
        initialSettings.dailyTargetMinutes,
        initialSettings.isCustomDailyTarget,
    ) {
        mutableStateOf(initialSettings.isCustomDailyTarget)
    }
    var customTargetMinutes by rememberSaveable(
        initialSettings.customDailyTargetMinutes,
        initialSettings.dailyTargetMinutes,
    ) {
        mutableIntStateOf(initialSettings.validatedCustomDailyTargetMinutes)
    }
    var selectedSessionMinutes by rememberSaveable(initialSettings.sessionDurationMinutes) {
        mutableIntStateOf(initialSettings.sessionDurationMinutes)
    }
    var reminderEnabled by rememberSaveable(initialSettings.reminderOnSessionEnd) {
        mutableStateOf(initialSettings.reminderOnSessionEnd)
    }
    var showCustomTargetSheet by rememberSaveable {
        mutableStateOf(false)
    }

    BackHandler(onBack = onBackClick)

    fun buildCurrentSettings(
        targetMinutes: Int = selectedTargetMinutes,
        isCustom: Boolean = isCustomTarget,
        customMinutes: Int = customTargetMinutes,
        sessionMinutes: Int = selectedSessionMinutes,
        reminder: Boolean = reminderEnabled,
    ): TargetSettingsState {
        return TargetSettingsState(
            dailyTargetMinutes = validateDailyTargetMinutes(targetMinutes),
            isCustomDailyTarget = isCustom,
            customDailyTargetMinutes = validateDailyTargetMinutes(customMinutes),
            sessionDurationMinutes = sessionMinutes,
            reminderOnSessionEnd = reminder,
        )
    }

    SetTargetScreenContent(
        selectedTargetMinutes = selectedTargetMinutes,
        isCustomTarget = isCustomTarget,
        customTargetMinutes = customTargetMinutes,
        onPresetTargetSelected = { presetMinutes ->
            val validated = validateDailyTargetMinutes(presetMinutes)
            selectedTargetMinutes = validated
            isCustomTarget = false
            onTargetSettingsChanged(
                buildCurrentSettings(
                    targetMinutes = validated,
                    isCustom = false,
                ),
            )
        },
        onCustomCardClick = {
            showCustomTargetSheet = true
        },
        selectedSessionMinutes = selectedSessionMinutes,
        onSessionMinutesSelected = { selectedSessionMinutes = it },
        reminderEnabled = reminderEnabled,
        onReminderToggleChanged = { reminderEnabled = it },
        onBackClick = onBackClick,
        onSaveClick = {
            onSaveSettings(buildCurrentSettings())
        },
        modifier = modifier,
    )

    if (showCustomTargetSheet) {
        CustomDailyTargetModal(
            initialMinutes = if (isCustomTarget) selectedTargetMinutes else customTargetMinutes,
            onDismiss = {
                showCustomTargetSheet = false
            },
            onApplyCustomTarget = { appliedMinutes ->
                val validated = validateDailyTargetMinutes(appliedMinutes)
                selectedTargetMinutes = validated
                customTargetMinutes = validated
                isCustomTarget = true
                showCustomTargetSheet = false
                onTargetSettingsChanged(
                    buildCurrentSettings(
                        targetMinutes = validated,
                        isCustom = true,
                        customMinutes = validated,
                    ),
                )
            },
        )
    }
}

// --- Stateless SetTargetScreenContent ---

@Composable
fun SetTargetScreenContent(
    selectedTargetMinutes: Int,
    isCustomTarget: Boolean,
    customTargetMinutes: Int,
    onPresetTargetSelected: (Int) -> Unit,
    onCustomCardClick: () -> Unit,
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
                selectedMinutes = selectedTargetMinutes,
                isCustomTarget = isCustomTarget,
                onSelectPresetMinutes = onPresetTargetSelected,
                onCustomOptionClick = onCustomCardClick,
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
    selectedMinutes: Int,
    isCustomTarget: Boolean,
    onSelectPresetMinutes: (Int) -> Unit,
    onCustomOptionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors
    val validatedSelectedMinutes = validateDailyTargetMinutes(selectedMinutes)
    val activeFeedback = resolveDailyTargetFeedback(
        minutes = validatedSelectedMinutes,
        isCustom = isCustomTarget,
        colors = colors,
    )

    val firstRowPresets = DailyTargetPresetOptions.subList(0, 2) // 30 menit, 1 jam
    val secondRowPresets = DailyTargetPresetOptions.subList(2, 4) // 2 jam, 3 jam

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

            // 2-Column Grid of Target Cards (30m, 1j, 2j, 3j) + Full-Width Kustom (2 columns wide)
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(firstRowPresets, secondRowPresets).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowOptions.forEach { option ->
                            val isSelected = (!isCustomTarget) && (validatedSelectedMinutes == option.minutes)
                            val (accentColor, softBgColor) = resolveColorPair(option.colorTier, colors)
                            TargetOptionGridItem(
                                title = option.title,
                                subtitle = option.category,
                                isSelected = isSelected,
                                accentColor = accentColor,
                                softBgColor = softBgColor,
                                onClick = { onSelectPresetMinutes(option.minutes) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Third row: Kustom spanning full width (2 columns)
                val (customAccent, customSoftBg) = resolveColorPair(
                    DailyTargetColorTier.CUSTOM_BLUE,
                    colors,
                )
                val customCardTitle = if (isCustomTarget) {
                    formatDailyTargetDuration(validatedSelectedMinutes)
                } else {
                    "Kustom"
                }
                val customCardSubtitle = if (isCustomTarget) {
                    "Kustom"
                } else {
                    "Atur sendiri"
                }
                TargetOptionGridItem(
                    title = customCardTitle,
                    subtitle = customCardSubtitle,
                    isSelected = isCustomTarget,
                    accentColor = customAccent,
                    softBgColor = customSoftBg,
                    isCustomCard = true,
                    onClick = onCustomOptionClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_target_card"),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Explanation Card below grid
            DailyTargetExplanationCard(
                feedback = activeFeedback,
                isCustomTarget = isCustomTarget,
            )
        }
    }
}

@Composable
private fun TargetOptionGridItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    accentColor: Color,
    softBgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCustomCard: Boolean = false,
) {
    val colors = JedaInTheme.colors

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) softBgColor else colors.background,
        label = "TargetOptionBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else colors.divider,
        label = "TargetOptionBorder",
    )
    val subtitleColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else colors.textSecondary,
        label = "TargetOptionSubtitle",
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Proportional visual indicator
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Terpilih",
                            tint = colors.surface,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                } else if (isCustomCard) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.75f)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = subtitleColor,
            )
        }
    }
}

@Composable
private fun DailyTargetExplanationCard(
    feedback: DailyTargetFeedback,
    isCustomTarget: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors
    val headerLabel = if (isCustomTarget) {
        "Kustom (${feedback.durationText}) • ${feedback.category}"
    } else {
        "${feedback.durationText} • ${feedback.category}"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_target_explanation_card"),
        shape = RoundedCornerShape(14.dp),
        color = feedback.softBgColor,
        border = BorderStroke(1.dp, feedback.accentColor.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = feedback.accentColor,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = headerLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = feedback.accentColor,
                )
                Text(
                    text = feedback.description,
                    fontSize = 13.sp,
                    color = colors.textPrimary,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

// --- Custom Target Bottom Sheet Modal ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomDailyTargetModal(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onApplyCustomTarget: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftMinutes by rememberSaveable(initialMinutes) {
        mutableIntStateOf(validateDailyTargetMinutes(initialMinutes))
    }

    val hours = draftMinutes / 60
    val remainingMinutes = draftMinutes % 60
    val canDecreaseHours = hours > 0
    val canIncreaseHours = draftMinutes < TargetSettingsPreferences.MAX_DAILY_TARGET_MINUTES
    val canDecreaseMinutes = draftMinutes > TargetSettingsPreferences.MIN_DAILY_TARGET_MINUTES
    val canIncreaseMinutes = draftMinutes < TargetSettingsPreferences.MAX_DAILY_TARGET_MINUTES

    val liveFeedback = resolveDailyTargetFeedback(
        minutes = draftMinutes,
        isCustom = false,
        colors = colors,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Atur Target Kustom",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Tentukan batas waktu penggunaan harianmu.",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                )
            }

            // Hour and Minute Steppers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DurationStepperControl(
                    label = "Jam",
                    value = hours,
                    unitLabel = "jam",
                    canDecrease = canDecreaseHours,
                    canIncrease = canIncreaseHours,
                    decreaseDescription = "Kurangi jam",
                    increaseDescription = "Tambah jam",
                    onDecrease = {
                        draftMinutes = validateDailyTargetMinutes(draftMinutes - 60)
                    },
                    onIncrease = {
                        draftMinutes = validateDailyTargetMinutes(draftMinutes + 60)
                    },
                    modifier = Modifier.weight(1f),
                )

                DurationStepperControl(
                    label = "Menit",
                    value = remainingMinutes,
                    unitLabel = "menit",
                    canDecrease = canDecreaseMinutes,
                    canIncrease = canIncreaseMinutes,
                    decreaseDescription = "Kurangi menit",
                    increaseDescription = "Tambah menit",
                    onDecrease = {
                        draftMinutes = validateDailyTargetMinutes(
                            draftMinutes - TargetSettingsPreferences.DAILY_TARGET_STEP_MINUTES,
                        )
                    },
                    onIncrease = {
                        draftMinutes = validateDailyTargetMinutes(
                            draftMinutes + TargetSettingsPreferences.DAILY_TARGET_STEP_MINUTES,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = "Interval 15 menit • Min. 15 menit, maks. 4 jam",
                fontSize = 12.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // Live Target Summary
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_target_live_summary"),
                shape = RoundedCornerShape(14.dp),
                color = liveFeedback.softBgColor,
                border = BorderStroke(1.dp, liveFeedback.accentColor.copy(alpha = 0.35f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Ringkasan: ${liveFeedback.durationText}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = liveFeedback.category,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = liveFeedback.accentColor,
                        )
                    }
                    Text(
                        text = liveFeedback.description,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        lineHeight = 17.sp,
                    )
                }
            }

            // Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onApplyCustomTarget(draftMinutes) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.surface,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(
                        text = "Terapkan Target",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.surface,
                        contentColor = colors.textPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                ) {
                    Text(
                        text = "Batal",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DurationStepperControl(
    label: String,
    value: Int,
    unitLabel: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    decreaseDescription: String,
    increaseDescription: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = colors.background,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (canDecrease) colors.surface else colors.background,
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.size(36.dp),
                ) {
                    IconButton(
                        onClick = onDecrease,
                        enabled = canDecrease,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = decreaseDescription,
                            tint = if (canDecrease) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = value.toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = unitLabel,
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (canIncrease) colors.surface else colors.background,
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.size(36.dp),
                ) {
                    IconButton(
                        onClick = onIncrease,
                        enabled = canIncrease,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = increaseDescription,
                            tint = if (canIncrease) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
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
                dailyTargetMinutes = 120,
                isCustomDailyTarget = false,
                sessionDurationMinutes = 10,
                reminderOnSessionEnd = true,
            ),
            onBackClick = {},
            onSaveSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetTargetScreenCustomPreview() {
    JedaInTheme {
        SetTargetScreen(
            initialSettings = TargetSettingsState(
                dailyTargetMinutes = 90,
                isCustomDailyTarget = true,
                customDailyTargetMinutes = 90,
                sessionDurationMinutes = 10,
                reminderOnSessionEnd = true,
            ),
            onBackClick = {},
            onSaveSettings = {},
        )
    }
}
