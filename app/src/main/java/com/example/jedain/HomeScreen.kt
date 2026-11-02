package com.example.jedain

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.core.content.ContextCompat
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.jedain.ui.theme.JedaInTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class JedaInScreen {
    HOME,
    SET_TARGET,
    MANAGE_APPS,
}

private enum class DisclosurePermissionType {
    USAGE_ACCESS,
    OVERLAY,
}

const val DISABLE_BUBBLE_CONFIRMATION_PHRASE =
    "Saya sadar bahwa tanpa pemantauan, kebiasaan scrolling saya bisa berlangsung lebih lama."

fun validateDisableBubbleConfirmationPhrase(input: String): Boolean {
    val trimmed = input.trim()
    return trimmed.equals(DISABLE_BUBBLE_CONFIRMATION_PHRASE, ignoreCase = true) ||
        trimmed.equals(DISABLE_BUBBLE_CONFIRMATION_PHRASE.removeSuffix("."), ignoreCase = true)
}

// --- Root Screen ---

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    monitoredAppsViewModel: MonitoredAppsViewModel = rememberMonitoredAppsViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember(context) { TargetSettingsPreferences(context) }
    var targetSettings by remember { mutableStateOf(preferences.loadSettings()) }
    val monitoredAppsUiState by monitoredAppsViewModel.uiState.collectAsState()
    var currentScreen by rememberSaveable { mutableStateOf(JedaInScreen.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var activityPrompt by remember {
        mutableStateOf(
            DailyActivityPromptProvider.getNextPrompt(
                currentHour = DailyActivityPromptProvider.getCurrentLocalHour()
            )
        )
    }

    DisposableEffect(lifecycleOwner, monitoredAppsViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                targetSettings = preferences.loadSettings()
                monitoredAppsViewModel.refreshPermissionsAndUsage()
                DailyAwarenessWidgetProvider.updateAllWidgets(context)
                val currentHour = DailyActivityPromptProvider.getCurrentLocalHour()
                activityPrompt = DailyActivityPromptProvider.getNextPrompt(
                    currentHour = currentHour,
                    excludeId = activityPrompt.id
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Dark mode state: default mengikuti preferensi sistem, bisa di-override manual
    val systemInDarkTheme = isSystemInDarkTheme()
    var isDarkThemeUserOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val isDarkTheme = isDarkThemeUserOverride ?: systemInDarkTheme

    val currentDateText = remember {
        val localeId = Locale.forLanguageTag("id-ID")
        val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", localeId)
        val formatted = try {
            LocalDate.now().format(formatter)
        } catch (_: Exception) {
            "Selasa, 27 Oktober 2026"
        }
        formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(localeId) else it.toString() }
    }

    JedaInTheme(darkTheme = isDarkTheme) {
        val colors = JedaInTheme.colors

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = colors.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding ->
            when (currentScreen) {
                JedaInScreen.HOME -> {
                    HomeScreen(
                        dateText = currentDateText,
                        monitoredAppsUiState = monitoredAppsUiState,
                        isDarkTheme = isDarkTheme,
                        onToggleDarkTheme = {
                            isDarkThemeUserOverride = !isDarkTheme
                        },
                        targetMinutes = targetSettings.validatedDailyTargetMinutes,
                        activityPrompt = activityPrompt,
                        onNextPromptClick = {
                            val currentHour = DailyActivityPromptProvider.getCurrentLocalHour()
                            activityPrompt = DailyActivityPromptProvider.getNextPrompt(
                                currentHour = currentHour,
                                excludeId = activityPrompt.id
                            )
                        },
                        onTargetSettingsClick = {
                            currentScreen = JedaInScreen.SET_TARGET
                        },
                        onManageAppsClick = {
                            currentScreen = JedaInScreen.MANAGE_APPS
                        },
                        onShowSnackbar = { message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
                JedaInScreen.SET_TARGET -> {
                    SetTargetScreen(
                        initialSettings = targetSettings,
                        onBackClick = {
                            currentScreen = JedaInScreen.HOME
                        },
                        onTargetSettingsChanged = { updatedSettings ->
                            preferences.saveSettings(updatedSettings)
                            targetSettings = updatedSettings
                            BreakSessionManager.resetSession(context)
                            DailyAwarenessWidgetProvider.updateAllWidgets(context)
                        },
                        onSaveSettings = { newSettings ->
                            preferences.saveSettings(newSettings)
                            targetSettings = newSettings
                            BreakSessionManager.resetSession(context)
                            DailyAwarenessWidgetProvider.updateAllWidgets(context)
                            currentScreen = JedaInScreen.HOME
                            scope.launch {
                                snackbarHostState.showSnackbar("Pengaturan target disimpan")
                            }
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
                JedaInScreen.MANAGE_APPS -> {
                    ManageMonitoredAppsScreen(
                        viewModel = monitoredAppsViewModel,
                        onBackClick = {
                            currentScreen = JedaInScreen.HOME
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

// --- Stateless Home Screen ---

@Composable
fun HomeScreen(
    dateText: String,
    monitoredAppsUiState: MonitoredAppsUiState,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    modifier: Modifier = Modifier,
    targetMinutes: Int = 0,
    activityPrompt: ActivityPrompt = remember {
        DailyActivityPromptProvider.getNextPrompt(DailyActivityPromptProvider.getCurrentLocalHour())
    },
    onNextPromptClick: () -> Unit = {},
    onTargetSettingsClick: () -> Unit = {},
    onManageAppsClick: () -> Unit = {},
    onShowSnackbar: (String) -> Unit = {},
) {
    val context = LocalContext.current

    val onAppClick: (String) -> Unit = { packageName ->
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                onShowSnackbar("Aplikasi tidak dapat dibuka. Pastikan aplikasi masih terpasang.")
            }
        } catch (_: Exception) {
            onShowSnackbar("Aplikasi tidak dapat dibuka. Pastikan aplikasi masih terpasang.")
        }
    }

    val scrollState = rememberScrollState()
    val isScrolled = scrollState.canScrollBackward
    var pendingDisclosurePermission by rememberSaveable {
        mutableStateOf<DisclosurePermissionType?>(null)
    }

    val bubbleController = remember(context) { FloatingBubbleController(context) }
    var isBubbleEnabled by rememberSaveable { mutableStateOf(bubbleController.isEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var tickTrigger by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lifecycleOwner) {
        while (isActive) {
            delay(1000L)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                tickTrigger = SystemClock.elapsedRealtime()
            }
        }
    }
    var showBreakDurationPicker by rememberSaveable { mutableStateOf(false) }
    var showDisableBubbleConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var disableBubbleConfirmationInput by rememberSaveable { mutableStateOf("") }

    var hasNotificationPermission by rememberSaveable {
        mutableStateOf(PermissionHelper.hasNotificationPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        bubbleController.setEnabled(true)
        isBubbleEnabled = true
        hasNotificationPermission = true
        BreakSessionManager.resetSession(context)
        FloatingBubbleService.start(context)
        DailyAwarenessWidgetProvider.updateAllWidgets(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasOverlay = PermissionHelper.hasOverlayPermission(context)
                if (hasOverlay) {
                    if (bubbleController.isEnabled()) {
                        FloatingBubbleService.start(context)
                    }
                } else {
                    FloatingBubbleService.stop(context)
                }
                isBubbleEnabled = bubbleController.isEnabled() && hasOverlay

                val notifGranted = PermissionHelper.hasNotificationPermission(context)
                hasNotificationPermission = notifGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val onToggleBubble: (Boolean) -> Unit = { enabled ->
        if (enabled) {
            val hasOverlay = PermissionHelper.hasOverlayPermission(context)
            if (!hasOverlay) {
                pendingDisclosurePermission = DisclosurePermissionType.OVERLAY
                isBubbleEnabled = false
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    bubbleController.setEnabled(true)
                    isBubbleEnabled = true
                    BreakSessionManager.resetSession(context)
                    FloatingBubbleService.start(context)
                    DailyAwarenessWidgetProvider.updateAllWidgets(context)
                }
            }
        } else {
            bubbleController.setEnabled(false)
            isBubbleEnabled = false
            FloatingBubbleService.stop(context)
            BreakSessionManager.onRestCompleted(context)
            BreakSessionManager.resetSession(context)
            DailyAwarenessWidgetProvider.updateAllWidgets(context)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        // Subtle organic curves in background to create calm, organic depth
        OrganicBackgroundShapes(
            isDarkTheme = isDarkTheme,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header Fixed di atas dengan logo monochrome dan toggle dark mode
            FixedHeaderSection(
                isDarkTheme = isDarkTheme,
                onToggleDarkTheme = onToggleDarkTheme,
                showDivider = isScrolled,
            )

            // Konten Scrollable
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Sapaan dan Tanggal dengan Ide Kegiatan Nyata
                GreetingSection(
                    dateText = dateText,
                    prompt = activityPrompt,
                    onNextPromptClick = onNextPromptClick,
                )

                // Card Izin Aplikasi (jika izin belum lengkap)
                if (!monitoredAppsUiState.hasUsageAccessPermission ||
                    !monitoredAppsUiState.hasOverlayPermission ||
                    !hasNotificationPermission
                ) {
                    PermissionSetupCard(
                        hasUsageAccessPermission = monitoredAppsUiState.hasUsageAccessPermission,
                        hasOverlayPermission = monitoredAppsUiState.hasOverlayPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        onRequestUsageAccess = {
                            if (monitoredAppsUiState.hasUsageAccessPermission) {
                                PermissionHelper.openUsageAccessSettings(context)
                            } else {
                                pendingDisclosurePermission = DisclosurePermissionType.USAGE_ACCESS
                            }
                        },
                        onRequestOverlayPermission = {
                            if (monitoredAppsUiState.hasOverlayPermission) {
                                PermissionHelper.openOverlaySettings(context)
                            } else {
                                pendingDisclosurePermission = DisclosurePermissionType.OVERLAY
                            }
                        },
                        onRequestNotificationPermission = {
                            PermissionHelper.openNotificationSettings(context)
                        },
                    )
                }

                // Kartu Waktu Layar Aplikasi Dipantau
                TodayScreenTimeCard(
                    durationText = monitoredAppsUiState.todayTotalDurationText,
                    changeText = monitoredAppsUiState.comparisonChangeText,
                    isTrendingDown = monitoredAppsUiState.isTrendingDown,
                    targetMinutes = targetMinutes,
                    totalUsageMillis = monitoredAppsUiState.todayTotalMillis,
                    onTargetSettingsClick = onTargetSettingsClick,
                )

                // Blok Status Sesi & Ambil Jeda
                SessionStatusCard(
                    onTakeBreakClick = { showBreakDurationPicker = true },
                    tickTrigger = tickTrigger,
                )

                // Daftar Aplikasi yang Dipantau & Tombol Kelola Aplikasi
                MonitoredAppsSection(
                    uiState = monitoredAppsUiState,
                    onManageAppsClick = onManageAppsClick,
                    onAppClick = onAppClick,
                )

                // Card Bubble Mengambang (Posisi Paling Bawah)
                FloatingBubbleCard(
                    isEnabled = isBubbleEnabled,
                    onCheckedChange = { willEnable ->
                        if (willEnable) {
                            onToggleBubble(true)
                        } else {
                            showDisableBubbleConfirmDialog = true
                        }
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showBreakDurationPicker) {
        val initialRestMinutes = remember { BreakSessionManager.getLastRestDurationMinutes(context) }
        val initialIsCustom = remember { BreakSessionManager.isLastRestCustom(context) }
        BreakDurationSelectionBottomSheet(
            initialMinutes = initialRestMinutes,
            initialIsCustom = initialIsCustom,
            onDismiss = { showBreakDurationPicker = false },
            onConfirmRest = { selectedMinutes, isCustom ->
                showBreakDurationPicker = false
                BreakSessionManager.startRest(context, selectedMinutes, isCustom)
                DailyAwarenessWidgetProvider.updateAllWidgets(context)
            },
        )
    }

    if (showDisableBubbleConfirmDialog) {
        DisableBubbleConfirmationDialog(
            input = disableBubbleConfirmationInput,
            onInputChange = { disableBubbleConfirmationInput = it },
            onConfirm = {
                showDisableBubbleConfirmDialog = false
                disableBubbleConfirmationInput = ""
                onToggleBubble(false)
            },
            onDismiss = {
                showDisableBubbleConfirmDialog = false
                disableBubbleConfirmationInput = ""
            },
        )
    }

    pendingDisclosurePermission?.let { permissionType ->
        PermissionProminentDisclosureDialog(
            permissionType = permissionType,
            onDismiss = { pendingDisclosurePermission = null },
            onConfirmOpenSettings = {
                pendingDisclosurePermission = null
                when (permissionType) {
                    DisclosurePermissionType.USAGE_ACCESS -> {
                        PermissionHelper.openUsageAccessSettings(context)
                    }
                    DisclosurePermissionType.OVERLAY -> {
                        PermissionHelper.openOverlaySettings(context)
                    }
                }
            },
        )
    }
}

// --- Dialog Konfirmasi Penonaktifan Bubble Mengambang ---

@Composable
fun DisableBubbleConfirmationDialog(
    input: String,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = JedaInTheme.colors
    val isConfirmed = remember(input) {
        validateDisableBubbleConfirmationPhrase(input)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = {
            Text(
                text = "Nonaktifkan Bubble Mengambang?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Deskripsi
                Text(
                    text = "Jika Bubble dinonaktifkan, sistem pemantauan, countdown, dan sesi jeda JedaIn tidak akan berjalan sampai kamu mengaktifkannya kembali.",
                    fontSize = 13.sp,
                    color = colors.textPrimary,
                    lineHeight = 18.sp,
                )

                // Penjelasan tambahan
                Text(
                    text = "Kamu tidak akan mendapatkan pengingat ketika terlalu lama menggunakan aplikasi yang dipantau.",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    lineHeight = 18.sp,
                )

                // Konfirmasi kesadaran
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.targetCard)
                        .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Sebelum melanjutkan, pahami konsekuensinya.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )

                    Text(
                        text = "Ketik kalimat berikut untuk memastikan kamu memahami konsekuensinya:",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        lineHeight = 16.sp,
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.background,
                        border = BorderStroke(1.dp, colors.divider),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = DISABLE_BUBBLE_CONFIRMATION_PHRASE,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 17.sp,
                        )
                    }
                }

                // Input Text Field
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            text = "Ketik kalimat di atas...",
                            fontSize = 12.sp,
                            color = colors.textSecondary.copy(alpha = 0.6f),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 13.sp,
                        color = colors.textPrimary,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isConfirmed) colors.durationGreen else colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        cursorColor = colors.accent,
                    ),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = isConfirmed,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.surface,
                    disabledContainerColor = colors.border,
                    disabledContentColor = colors.textSecondary.copy(alpha = 0.5f),
                ),
            ) {
                Text(
                    text = "Ya, Nonaktifkan",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(
                    text = "Batalkan",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                )
            }
        },
    )
}

// --- Kartu Bubble Mengambang dengan Switch ---

@Composable
fun FloatingBubbleCard(
    isEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Baris 1: Judul di kiri dan Switch di kanan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Bubble Mengambang",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.surface,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.background,
                        uncheckedBorderColor = colors.border,
                    ),
                )
            }

            // Baris 2: Badge status "Aktif" / "Nonaktif" di bawah judul (wrapContentWidth)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isEnabled) colors.durationGreenBg else colors.targetCard,
                border = BorderStroke(
                    1.dp,
                    if (isEnabled) colors.durationGreen.copy(alpha = 0.35f) else colors.border,
                ),
                modifier = Modifier.wrapContentWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isEnabled) colors.durationGreen else colors.textSecondary.copy(alpha = 0.6f)),
                    )
                    Text(
                        text = if (isEnabled) "Aktif" else "Nonaktif",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled) colors.durationGreen else colors.textSecondary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            // Deskripsi Card
            Text(
                text = "Tampilkan penghitung waktu mengambang saat kamu menggunakan aplikasi yang dipantau.",
                fontSize = 13.sp,
                color = colors.textPrimary,
                lineHeight = 18.sp,
            )

            HorizontalDivider(color = colors.divider, thickness = 1.dp)

            // Informasi Tambahan
            Text(
                text = "Bubble Mengambang diperlukan agar sistem pemantauan, countdown, dan sesi jeda JedaIn dapat berjalan.",
                fontSize = 12.sp,
                color = colors.textSecondary,
                lineHeight = 17.sp,
            )
        }
    }
}

// --- Subtle Organic Background Shapes ---

@Composable
fun OrganicBackgroundShapes(
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val shapeAlpha = if (isDarkTheme) 0.045f else 0.025f
        val shapeColor = if (isDarkTheme) Color(0xFFFFFFFF) else Color(0xFF171717)

        // Subtle soft organic curve in upper right background
        val topPath = Path().apply {
            moveTo(width * 0.40f, 0f)
            cubicTo(
                width * 0.65f, height * 0.08f,
                width * 1.05f, height * 0.03f,
                width, height * 0.24f,
            )
            lineTo(width, 0f)
            close()
        }
        drawPath(path = topPath, color = shapeColor.copy(alpha = shapeAlpha))

        // Subtle soft organic curve in middle-left background
        val midLeftPath = Path().apply {
            moveTo(0f, height * 0.35f)
            cubicTo(
                width * 0.30f, height * 0.32f,
                width * 0.22f, height * 0.52f,
                0f, height * 0.58f,
            )
            close()
        }
        drawPath(path = midLeftPath, color = shapeColor.copy(alpha = shapeAlpha * 0.8f))
    }
}

// --- Section 1: Header Nyangkut (Fixed) & Sapaan ---

@Composable
fun FixedHeaderSection(
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
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
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // App Logo & Branding
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    // App Logo with custom drawable inside a rounded square
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.size(42.dp),
                        shadowElevation = 0.dp,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                        ) {
                            Image(
                                painter = painterResource(
                                    id = if (isDarkTheme) R.drawable.dark_logo else R.drawable.logo,
                                ),
                                contentDescription = "Logo JedaIn",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "JedaIn",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            letterSpacing = (-0.3).sp,
                        )
                        Text(
                            text = "Lebih sadar, sedikit demi sedikit.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = colors.textSecondary,
                        )
                    }
                }

                // White rounded-square dark-mode toggle with black crescent moon icon
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            onClick = onToggleDarkTheme,
                            role = Role.Button,
                            onClickLabel = if (isDarkTheme) "Beralih ke mode terang" else "Beralih ke mode gelap",
                        ),
                    shadowElevation = 0.dp,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = if (isDarkTheme) "Beralih ke mode terang" else "Beralih ke mode gelap",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
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

@Composable
fun GreetingSection(
    dateText: String,
    modifier: Modifier = Modifier,
    prompt: ActivityPrompt? = null,
    onNextPromptClick: () -> Unit = {},
) {
    val colors = JedaInTheme.colors

    Text(
        text = dateText,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = colors.textSecondary,
        letterSpacing = 0.1.sp,
        modifier = modifier.fillMaxWidth(),
    )
}

// --- Section 1B: Kartu Status Izin Sistem & Prominent Disclosure ---

@Composable
private fun PermissionSetupCard(
    hasUsageAccessPermission: Boolean,
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestUsageAccess: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.targetCard,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Izin sistem diperlukan",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Aktifkan izin berikut agar pemantauan dan pengingat jeda berjalan akurat.",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        lineHeight = 16.sp,
                    )
                }
            }

            HorizontalDivider(color = colors.divider, thickness = 1.dp)

            PermissionStatusRow(
                icon = Icons.Outlined.QueryStats,
                title = "Akses Penggunaan",
                subtitle = "Membaca durasi layar aplikasi yang dipantau",
                isGranted = hasUsageAccessPermission,
                onActionClick = onRequestUsageAccess,
            )

            PermissionStatusRow(
                icon = Icons.Outlined.Layers,
                title = "Tampil di Atas Aplikasi",
                subtitle = "Menampilkan layar jeda saat sesi berakhir",
                isGranted = hasOverlayPermission,
                onActionClick = onRequestOverlayPermission,
            )

            PermissionStatusRow(
                icon = Icons.Outlined.Notifications,
                title = "Notifikasi JedaIn",
                subtitle = "Izinkan JedaIn menampilkan notifikasi pengingat.",
                isGranted = hasNotificationPermission,
                onActionClick = onRequestNotificationPermission,
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onActionClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.iconCharcoal,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (isGranted) {
            Surface(
                shape = RoundedCornerShape(50),
                color = colors.durationGreenBg,
                border = BorderStroke(1.dp, colors.durationGreen.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(colors.durationGreen),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colors.surface,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                    Text(
                        text = "Aktif",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.durationGreen,
                    )
                }
            }
        } else {
            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.surface,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                modifier = Modifier.height(34.dp),
            ) {
                Text(
                    text = "Izinkan",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PermissionProminentDisclosureDialog(
    permissionType: DisclosurePermissionType,
    onDismiss: () -> Unit,
    onConfirmOpenSettings: () -> Unit,
) {
    val colors = JedaInTheme.colors
    val title = when (permissionType) {
        DisclosurePermissionType.USAGE_ACCESS -> "Izin Akses Penggunaan"
        DisclosurePermissionType.OVERLAY -> "Izin Tampil di Atas Aplikasi"
    }
    val description = when (permissionType) {
        DisclosurePermissionType.USAGE_ACCESS ->
            "JedaIn memerlukan izin Akses Penggunaan (Usage Access) untuk menghitung durasi waktu layar harian dari aplikasi yang kamu pantau dan membandingkannya dengan hari sebelumnya. Seluruh data diproses secara lokal di perangkatmu dan tidak pernah dikirim ke luar perangkat."
        DisclosurePermissionType.OVERLAY ->
            "JedaIn memerlukan izin Tampil di Atas Aplikasi Lain (Appear on Top) agar dapat menampilkan layar pengingat jeda secara langsung ketika batas sesi waktu aplikasi yang dipantau telah tercapai."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
        },
        text = {
            Text(
                text = description,
                fontSize = 14.sp,
                color = colors.textSecondary,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmOpenSettings,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.surface,
                ),
            ) {
                Text(
                    text = "Buka Pengaturan",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Nanti",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                )
            }
        },
    )
}

enum class TargetExceededTier {
    NONE,
    WARNING_ORANGE,
    EXCEEDED_RED,
}

fun resolveTargetExceededTier(
    totalMinutes: Int,
    targetMinutes: Int,
): TargetExceededTier {
    if (targetMinutes <= 0 || totalMinutes <= targetMinutes) {
        return TargetExceededTier.NONE
    }
    // Ambang batas +50% dari target
    val threshold50Percent = (targetMinutes * 1.5).roundToInt()
    return if (totalMinutes <= threshold50Percent) {
        TargetExceededTier.WARNING_ORANGE
    } else {
        TargetExceededTier.EXCEEDED_RED
    }
}

// --- Section 2: Kartu Waktu Layar Aplikasi Dipantau ---

@Composable
fun TodayScreenTimeCard(
    durationText: String,
    changeText: String,
    modifier: Modifier = Modifier,
    comparisonNote: String = "",
    isTrendingDown: Boolean = true,
    targetMinutes: Int = 0,
    totalUsageMillis: Long = 0L,
    onTargetSettingsClick: () -> Unit = {},
) {
    val colors = JedaInTheme.colors

    val totalMinutes = (totalUsageMillis / 60_000L).toInt()
    val exceededTier = resolveTargetExceededTier(
        totalMinutes = totalMinutes,
        targetMinutes = targetMinutes,
    )

    val indicatorColor = when (exceededTier) {
        TargetExceededTier.WARNING_ORANGE -> colors.durationOrange
        TargetExceededTier.EXCEEDED_RED -> colors.durationRed
        TargetExceededTier.NONE -> Color.Unspecified
    }

    val cardBorderColor = if (exceededTier != TargetExceededTier.NONE) {
        indicatorColor
    } else {
        colors.border
    }

    val durationDigitsColor = if (exceededTier != TargetExceededTier.NONE) {
        indicatorColor
    } else {
        colors.textPrimary
    }

    // Format perbandingan satu baris: "↓ 3j 42m dari kemarin (jam yang sama)"
    val singleLineComparisonText = remember(changeText, isTrendingDown) {
        when {
            changeText.contains("lebih sedikit dari kemarin", ignoreCase = true) -> {
                val diff = changeText.substringBefore(" lebih sedikit dari kemarin").trim()
                "↓ $diff dari kemarin (jam yang sama)"
            }
            changeText.contains("lebih banyak dari kemarin", ignoreCase = true) -> {
                val diff = changeText.substringBefore(" lebih banyak dari kemarin").trim()
                "↑ $diff dari kemarin (jam yang sama)"
            }
            changeText.contains("Sama seperti kemarin", ignoreCase = true) -> {
                "Sama seperti kemarin (jam yang sama)"
            }
            else -> {
                changeText
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, cardBorderColor),
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle organic accent watermark in top right of card
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(28.dp)),
            ) {
                val cardWidth = size.width
                val cardHeight = size.height
                val watermarkColor = if (colors.isDark) Color.White.copy(alpha = 0.025f) else Color.Black.copy(alpha = 0.02f)
                val curvePath = Path().apply {
                    moveTo(cardWidth * 0.70f, 0f)
                    cubicTo(
                        cardWidth * 0.78f, cardHeight * 0.35f,
                        cardWidth * 0.95f, cardHeight * 0.40f,
                        cardWidth, cardHeight * 0.60f,
                    )
                    lineTo(cardWidth, 0f)
                    close()
                }
                drawPath(curvePath, watermarkColor)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
            ) {
                // Header kartu: Judul
                Text(
                    text = "Waktu layar aplikasi dipantau",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Angka Waktu Layar: Elemen Visual Paling Menonjol
                Text(
                    text = durationText,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = durationDigitsColor,
                    letterSpacing = (-1.2).sp,
                )

                if (exceededTier != TargetExceededTier.NONE) {
                    val targetFormatted = formatDailyTargetDuration(targetMinutes)
                    val diffMinutes = (totalMinutes - targetMinutes).coerceAtLeast(1)
                    val diffFormatted = formatScreenTimeShort(diffMinutes * 60_000L)

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(indicatorColor),
                        )
                        Text(
                            text = "Melewati target $targetFormatted · +$diffFormatted",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = indicatorColor,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Perbandingan Waktu Satu Baris: "↓ 3j 42m dari kemarin (jam yang sama)"
                Text(
                    text = singleLineComparisonText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 1,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Tombol Pill-Shaped Abu-Abu Terang dengan Teks Hitam Melebar Penuh di Dasar Kartu
                Button(
                    onClick = onTargetSettingsClick,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.targetCard,
                        contentColor = colors.textPrimary,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                ) {
                    Text(
                        text = "Atur target",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// --- Section 2B: Blok Status Sesi & Ambil Jeda ---

@Composable
fun SessionStatusCard(
    onTakeBreakClick: () -> Unit,
    tickTrigger: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = JedaInTheme.colors

    val isResting = remember(tickTrigger) {
        BreakSessionManager.isResting(context) && BreakSessionManager.getRemainingRestMillis(context) > 0L
    }
    val isSessionActive = remember(tickTrigger, isResting) {
        !isResting && BreakSessionManager.isSessionActive(context)
    }

    val remainingRestMillis = remember(tickTrigger, isResting) {
        if (isResting) BreakSessionManager.getRemainingRestMillis(context) else 0L
    }

    val remainingSessionMillis = remember(tickTrigger, isSessionActive) {
        if (isSessionActive) BreakSessionManager.getRemainingSessionMillis(context) else 0L
    }

    val totalSessionMillis = remember(tickTrigger, isSessionActive) {
        if (isSessionActive) BreakSessionManager.getTotalSessionMillis(context) else 0L
    }

    val usedMillis = (totalSessionMillis - remainingSessionMillis).coerceAtLeast(0L)
    val usedMinutes = (usedMillis / 60_000L).toInt()
    val totalMinutes = (totalSessionMillis / 60_000L).toInt()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            when {
                isResting -> {
                    // Keadaan 2: Sedang jeda
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.durationOrange),
                        )
                        Text(
                            text = "Sedang jeda",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.durationOrange,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = formatTimerMmSs(remainingRestMillis),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = (-0.5).sp,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Waktu istirahat tersisa sebelum sesi baru",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                    )
                }
                isSessionActive -> {
                    // Keadaan 1: Sesi berjalan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.durationGreen),
                            )
                            Text(
                                text = "Pemantauan aktif",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.durationGreen,
                            )
                        }

                        Button(
                            onClick = onTakeBreakClick,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.surface,
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text(
                                text = "Ambil jeda",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = formatTimerMmSs(remainingSessionMillis),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = (-0.5).sp,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Sudah dipakai $usedMinutes menit dari $totalMinutes",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                    )
                }
                else -> {
                    // Keadaan 3: Tidak ada sesi (kartu pasif, layout tidak melompat)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.textSecondary.copy(alpha = 0.4f)),
                        )
                        Text(
                            text = "Status sesi",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Belum ada sesi. Pemantauan mulai saat kamu membuka aplikasi terpantau.",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

fun formatTimerMmSs(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

// --- Section 3: Bagian Aplikasi yang Dipantau & Tombol Kelola Aplikasi ---

@Composable
fun MonitoredAppsSection(
    uiState: MonitoredAppsUiState,
    onManageAppsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAppClick: (String) -> Unit = {},
) {
    val colors = JedaInTheme.colors
    // Urutkan aplikasi yang dipantau dari durasi pemakaian tertinggi hari ini
    val activeMonitoredApps = remember(uiState.allApps) {
        uiState.allApps
            .filter { it.isMonitored }
            .sortedWith(
                compareByDescending<MonitoredAppModel> { it.todayUsageMillis }
                    .thenBy { it.appName.lowercase() },
            )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header bagian dengan jumlah aplikasi yang aktif dipantau (uiState.monitoredCount)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Aplikasi yang dipantau",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = "${uiState.monitoredCount} aplikasi",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
            )
        }

        // Wadah Putih Bersudut Bulat dengan Pemisah Abu-Abu Halus
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = 0.dp,
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                activeMonitoredApps.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Belum ada aplikasi yang dipantau",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Tekan tombol Kelola aplikasi di bawah untuk memilih aplikasi dari perangkatmu.",
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp,
                        )
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        activeMonitoredApps.forEachIndexed { index, app ->
                            MonitoredAppRow(
                                item = app,
                                onClick = { onAppClick(app.packageName) },
                            )
                            if (index < activeMonitoredApps.lastIndex) {
                                HorizontalDivider(
                                    color = colors.divider,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = 18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tombol Sekunder "Kelola aplikasi"
        OutlinedButton(
            onClick = onManageAppsClick,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, colors.border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = colors.surface,
                contentColor = colors.textPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = null,
                tint = colors.iconCharcoal,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Kelola aplikasi",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun MonitoredAppRow(
    item: MonitoredAppModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            // Ikon asli aplikasi dalam kotak bersudut halus
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
                        bitmap = item.iconBitmap,
                        contentDescription = item.appName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = item.appName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Waktu layar",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = item.formattedDuration,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionSetupCardPreview() {
    JedaInTheme {
        PermissionSetupCard(
            hasUsageAccessPermission = true,
            hasOverlayPermission = false,
            hasNotificationPermission = false,
            onRequestUsageAccess = {},
            onRequestOverlayPermission = {},
            onRequestNotificationPermission = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    JedaInTheme {
        MainScreen()
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MainScreenDarkPreview() {
    JedaInTheme(darkTheme = true) {
        MainScreen()
    }
}

@Preview(showBackground = true)
@Composable
private fun FloatingBubbleCardPreview() {
    JedaInTheme {
        FloatingBubbleCard(
            isEnabled = true,
            onCheckedChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DisableBubbleConfirmationDialogPreview() {
    JedaInTheme {
        DisableBubbleConfirmationDialog(
            input = "",
            onInputChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}


