package com.example.jedain

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jedain.ui.theme.JedaInTheme
import kotlinx.coroutines.delay

class InterventionActivity : ComponentActivity() {

    private var modeState by mutableStateOf(MODE_EXPIRED)
    private var targetPackageState by mutableStateOf<String?>(null)
    private var targetAppNameState by mutableStateOf("aplikasi ini")
    private var intentVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyIncomingIntent(intent)

        setContent {
            JedaInTheme {
                InterventionScreen(
                    initialMode = modeState,
                    targetAppName = targetAppNameState,
                    intentVersion = intentVersion,
                    onFinishActivity = {
                        isInterventionShowing = false
                        finish()
                    },
                    onReturnToTargetApp = {
                        returnToTargetApp(targetPackageState)
                    },
                    onCancelIntentPrompt = {
                        isInterventionShowing = false
                        FloatingBubbleService.onIntentPromptCancelled()
                        navigateToHomeScreen()
                    },
                    onNavigateHome = {
                        isInterventionShowing = false
                        navigateToHomeScreen()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isInterventionShowing = true
    }

    override fun onPause() {
        super.onPause()
        isInterventionShowing = false
    }

    override fun onStop() {
        super.onStop()
        isInterventionShowing = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIncomingIntent(intent)
    }

    private fun applyIncomingIntent(incoming: Intent?) {
        val resolvedMode = incoming?.getStringExtra(EXTRA_MODE) ?: run {
            if (BreakSessionManager.isResting(this)) {
                InterventionViewMode.RESTING.name
            } else {
                InterventionViewMode.EXPIRED.name
            }
        }
        val pkg = incoming?.getStringExtra(EXTRA_TARGET_PACKAGE)
        val appName = incoming?.getStringExtra(EXTRA_TARGET_APP_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: resolveAppDisplayName(this, pkg)

        modeState = resolvedMode
        targetPackageState = pkg
        targetAppNameState = appName
        intentVersion++
    }

    private fun returnToTargetApp(targetPackage: String?) {
        isInterventionShowing = false
        val movedToBack = moveTaskToBack(true)
        if (!movedToBack && !targetPackage.isNullOrBlank() && targetPackage != packageName) {
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null) {
                runCatching { startActivity(launchIntent) }
            }
        }
        finish()
    }

    private fun navigateToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        const val EXTRA_MODE = "extra_intervention_mode"
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val EXTRA_TARGET_APP_NAME = "extra_target_app_name"
        const val MODE_INTENT_PROMPT = "INTENT_PROMPT"
        const val MODE_EXPIRED = "EXPIRED"
        const val MODE_RESTING = "RESTING"

        const val BREATHING_BUTTON_DELAY_SECONDS = 5
        const val BREATHING_BUTTON_DELAY_MILLIS = BREATHING_BUTTON_DELAY_SECONDS * 1000L

        internal fun isBreathingButtonDelayFinished(
            firstDisplayTimestamp: Long,
            currentTimestamp: Long,
            delayMillis: Long = BREATHING_BUTTON_DELAY_MILLIS,
        ): Boolean {
            return (currentTimestamp - firstDisplayTimestamp) >= delayMillis
        }

        internal fun calculateRemainingBreathingDelay(
            firstDisplayTimestamp: Long,
            currentTimestamp: Long,
            delayMillis: Long = BREATHING_BUTTON_DELAY_MILLIS,
        ): Long {
            val elapsed = currentTimestamp - firstDisplayTimestamp
            return (delayMillis - elapsed).coerceAtLeast(0L)
        }

        @Volatile
        var isInterventionShowing: Boolean = false
            private set

        fun resolveAppDisplayName(context: Context, packageName: String?): String {
            if (packageName.isNullOrBlank()) return "aplikasi ini"
            val pm = context.packageManager
            return runCatching {
                @Suppress("DEPRECATION")
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString().trim().ifBlank { "aplikasi ini" }
            }.getOrDefault("aplikasi ini")
        }

        @JvmOverloads
        fun createIntent(
            context: Context,
            mode: String = MODE_EXPIRED,
            targetPackage: String? = null,
        ): Intent {
            val appName = resolveAppDisplayName(context, targetPackage)
            return Intent(context, InterventionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_MODE, mode)
                if (!targetPackage.isNullOrBlank()) {
                    putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
                    putExtra(EXTRA_TARGET_APP_NAME, appName)
                }
            }
        }
    }
}

enum class InterventionViewMode {
    INTENT_PROMPT,
    EXPIRED,
    RESTING,
    BREATHING,
    CONFIRMATION,
}

@Composable
fun InterventionScreen(
    initialMode: String,
    targetAppName: String = "aplikasi ini",
    intentVersion: Int = 0,
    onFinishActivity: () -> Unit,
    onReturnToTargetApp: () -> Unit = onFinishActivity,
    onCancelIntentPrompt: () -> Unit = {},
    onNavigateHome: () -> Unit,
) {
    val context = LocalContext.current
    val palette = JedaInTheme.colors

    var currentMode by rememberSaveable {
        mutableStateOf(
            runCatching { InterventionViewMode.valueOf(initialMode) }
                .getOrDefault(InterventionViewMode.EXPIRED)
        )
    }

    var showDurationSheet by rememberSaveable { mutableStateOf(false) }
    var breathingSessionId by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(intentVersion, initialMode) {
        currentMode = runCatching { InterventionViewMode.valueOf(initialMode) }
            .getOrDefault(InterventionViewMode.EXPIRED)
        showDurationSheet = false
    }

    val configuredMinutes = remember { BreakSessionManager.getConfiguredSessionMinutes(context) }
    val extensionMinutes = remember(configuredMinutes) {
        BreakSessionManager.getExtensionMinutes(configuredMinutes)
    }
    val canExtend = remember(currentMode) { BreakSessionManager.canExtend(context) }
    val consecutiveExtensions = remember(currentMode) { BreakSessionManager.getConsecutiveExtensions(context) }

    // Intercept back button based on active step
    BackHandler {
        if (showDurationSheet) {
            showDurationSheet = false
            return@BackHandler
        }
        when (currentMode) {
            InterventionViewMode.INTENT_PROMPT -> {
                onCancelIntentPrompt()
            }
            InterventionViewMode.CONFIRMATION -> {
                currentMode = InterventionViewMode.BREATHING
            }
            InterventionViewMode.BREATHING -> {
                currentMode = InterventionViewMode.EXPIRED
            }
            InterventionViewMode.EXPIRED,
            InterventionViewMode.RESTING -> {
                onNavigateHome()
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = palette.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            AnimatedContent(
                targetState = currentMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "InterventionViewTransition",
                modifier = Modifier.fillMaxSize(),
            ) { mode ->
                when (mode) {
                    InterventionViewMode.INTENT_PROMPT -> {
                        SessionIntentPromptView(
                            appName = targetAppName,
                            choices = BreakSessionManager.DEFAULT_INTENT_CHOICES,
                            onConfirmSession = { selectedChoice, durationMinutes ->
                                BreakSessionManager.confirmSessionIntent(context, selectedChoice, durationMinutes)
                                FloatingBubbleService.triggerPendingIntentSnackbar(context)
                                onReturnToTargetApp()
                            },
                            onCancelAndBack = {
                                onCancelIntentPrompt()
                            },
                        )
                    }

                    InterventionViewMode.EXPIRED -> {
                        SessionExpiredView(
                            extensionMinutes = extensionMinutes,
                            canExtend = canExtend,
                            consecutiveExtensions = consecutiveExtensions,
                            onTakeRest = {
                                showDurationSheet = true
                            },
                            onContinueSession = {
                                breathingSessionId++
                                currentMode = InterventionViewMode.BREATHING
                            },
                            onNavigateHome = onNavigateHome,
                        )
                    }

                    InterventionViewMode.RESTING -> {
                        RestingCountdownView(
                            onRestCompleted = {
                                BreakSessionManager.onRestCompleted(context)
                                onFinishActivity()
                            },
                            onNavigateHome = onNavigateHome,
                        )
                    }

                    InterventionViewMode.BREATHING -> {
                        BreathingReflectionView(
                            breathingSessionId = breathingSessionId,
                            onProceedToConfirmation = {
                                currentMode = InterventionViewMode.CONFIRMATION
                            },
                            onCancelAndRest = {
                                currentMode = InterventionViewMode.EXPIRED
                                showDurationSheet = true
                            },
                        )
                    }

                    InterventionViewMode.CONFIRMATION -> {
                        ConsciousConfirmationView(
                            extensionMinutes = extensionMinutes,
                            onConfirmed = {
                                BreakSessionManager.extendSession(context, extensionMinutes)
                                onReturnToTargetApp()
                            },
                            onBack = {
                                currentMode = InterventionViewMode.BREATHING
                            },
                        )
                    }
                }
            }
        }
    }

    if (showDurationSheet) {
        val initialRestMinutes = remember { BreakSessionManager.getLastRestDurationMinutes(context) }
        val initialIsCustom = remember { BreakSessionManager.isLastRestCustom(context) }
        BreakDurationSelectionBottomSheet(
            initialMinutes = initialRestMinutes,
            initialIsCustom = initialIsCustom,
            onDismiss = { showDurationSheet = false },
            onConfirmRest = { selectedMinutes, isCustom ->
                showDurationSheet = false
                BreakSessionManager.startRest(context, selectedMinutes, isCustom)
                currentMode = InterventionViewMode.RESTING
            },
        )
    }
}

/**
 * Layar Pertanyaan Niat dan Pilihan Durasi Countdown saat memulai sesi pemantauan baru.
 */
@Composable
private fun SessionIntentPromptView(
    appName: String,
    choices: List<String>,
    onConfirmSession: (intentChoice: String, durationMinutes: Int) -> Unit,
    onCancelAndBack: () -> Unit,
) {
    val palette = JedaInTheme.colors
    val displayAppName = appName.trim().ifBlank { "aplikasi ini" }

    var selectedIntent by rememberSaveable { mutableStateOf<String?>(null) }
    var showCustomDurationDialog by rememberSaveable { mutableStateOf(false) }

    // Intercept back button saat berada di langkah pemilihan durasi
    BackHandler(enabled = selectedIntent != null) {
        selectedIntent = null
    }

    AnimatedContent(
        targetState = selectedIntent,
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
        },
        label = "IntentAndDurationTransition",
        modifier = Modifier.fillMaxSize(),
    ) { currentIntent ->
        if (currentIntent == null) {
            // Langkah 1: Pilih Niat Penggunaan
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(palette.targetCard),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Spa,
                        contentDescription = null,
                        tint = palette.textPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Mau apa buka $displayAppName?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Pilih tujuanmu sebelum memulai sesi penggunaan.",
                    fontSize = 14.sp,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                Spacer(modifier = Modifier.height(28.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    choices.forEach { choice ->
                        Surface(
                            onClick = { selectedIntent = choice },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = palette.surface,
                            border = BorderStroke(1.dp, palette.border),
                            shadowElevation = 0.dp,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = choice,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.textPrimary,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onCancelAndBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(
                        text = "Tidak jadi, kembali",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textSecondary,
                    )
                }
            }
        } else {
            // Langkah 2: Pilihan Durasi Countdown (Susunan 2 × 2)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(palette.targetCard),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HourglassEmpty,
                        contentDescription = null,
                        tint = palette.textPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Berapa lama ingin menggunakan?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = palette.targetCard,
                    border = BorderStroke(1.dp, palette.border),
                    onClick = { selectedIntent = null },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Niat: $currentIntent",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.textSecondary,
                        )
                        Text(
                            text = "· Ubah",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accent,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Pilih durasi countdown sebelum diingatkan untuk jeda.",
                    fontSize = 14.sp,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Empat kotak pilihan dengan susunan 2 × 2
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DurationSelectionBox(
                            text = "5 menit",
                            modifier = Modifier.weight(1f),
                            onClick = { onConfirmSession(currentIntent, 5) },
                        )
                        DurationSelectionBox(
                            text = "10 menit",
                            modifier = Modifier.weight(1f),
                            onClick = { onConfirmSession(currentIntent, 10) },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DurationSelectionBox(
                            text = "15 menit",
                            modifier = Modifier.weight(1f),
                            onClick = { onConfirmSession(currentIntent, 15) },
                        )
                        DurationSelectionBox(
                            text = "Atur sendiri",
                            modifier = Modifier.weight(1f),
                            onClick = { showCustomDurationDialog = true },
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { selectedIntent = null },
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text(
                            text = "‹ Ubah niat",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.textSecondary,
                        )
                    }

                    TextButton(
                        onClick = onCancelAndBack,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text(
                            text = "Tidak jadi, kembali",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.textSecondary,
                        )
                    }
                }
            }
        }
    }

    if (showCustomDurationDialog && selectedIntent != null) {
        val activeIntent = selectedIntent!!
        CustomDurationDialog(
            onDismiss = { showCustomDurationDialog = false },
            onConfirm = { customMinutes ->
                showCustomDurationDialog = false
                onConfirmSession(activeIntent, customMinutes)
            },
        )
    }
}

@Composable
private fun DurationSelectionBox(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = JedaInTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CustomDurationDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val palette = JedaInTheme.colors
    var inputText by rememberSaveable { mutableStateOf("") }
    var hasError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = palette.surface,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        title = {
            Text(
                text = "Atur Durasi Sendiri",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Tentukan berapa menit kamu ingin menggunakan aplikasi (1 - 30 menit).",
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                    lineHeight = 18.sp,
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() }
                        if (filtered.length <= 3) {
                            inputText = filtered
                            hasError = false
                        }
                    },
                    singleLine = true,
                    placeholder = {
                        Text("Contoh: 10", color = palette.textSecondary.copy(alpha = 0.5f))
                    },
                    suffix = {
                        Text("menit", fontSize = 14.sp, color = palette.textSecondary)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = hasError,
                    supportingText = if (hasError) {
                        {
                            Text(
                                text = "Masukkan durasi antara 1 sampai 30 menit",
                                fontSize = 12.sp,
                                color = palette.durationRed,
                            )
                        }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Batal",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textSecondary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minutes = inputText.toIntOrNull()
                    if (minutes != null && minutes in 1..30) {
                        onConfirm(minutes)
                    } else {
                        hasError = true
                    }
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.surface,
                ),
            ) {
                Text(
                    text = "Mulai",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

/**
 * Layar Awal Intervensi saat Sesi Jeda mencapai 00:00.
 */
@Composable
private fun SessionExpiredView(
    extensionMinutes: Int,
    canExtend: Boolean,
    consecutiveExtensions: Int,
    onTakeRest: () -> Unit,
    onContinueSession: () -> Unit,
    onNavigateHome: () -> Unit,
) {
    val palette = JedaInTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Minimalist Zen/Rest Icon
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(palette.targetCard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Spa,
                contentDescription = null,
                tint = palette.textPrimary,
                modifier = Modifier.size(38.dp),
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Waktunya berhenti sejenak.",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Batas durasi sesi penggunaan telah tercapai. Istirahatkan mata dan pikiran sejenak untuk memulihkan fokusmu.",
            fontSize = 15.sp,
            color = palette.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        if (consecutiveExtensions > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.targetCard)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Perpanjangan digunakan: $consecutiveExtensions / ${BreakSessionManager.MAX_CONSECUTIVE_EXTENSIONS}",
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (!canExtend) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.warningBackground)
                    .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Text(
                    text = "Batas 2 kali perpanjangan berturut-turut telah digunakan. Ambil sesi jeda untuk memulai sesi baru.",
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Opsi 1: Ambil Sesi Jeda (Utama)
        Button(
            onClick = onTakeRest,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.surface,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.HourglassEmpty,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Ambil Sesi Jeda",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Opsi 2: Lanjutkan Sesi (Hanya jika belum mencapai batas 2x)
        if (canExtend) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onContinueSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, palette.border),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = palette.textPrimary,
                ),
            ) {
                Text(
                    text = "Lanjutkan Sesi (+$extensionMinutes Menit)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onNavigateHome,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Kembali ke Beranda",
                fontSize = 13.sp,
                color = palette.textSecondary,
            )
        }
    }
}

/**
 * Layar Periode Istirahat 5 Menit dengan Live Countdown.
 */
@Composable
private fun RestingCountdownView(
    onRestCompleted: () -> Unit,
    onNavigateHome: () -> Unit,
) {
    val context = LocalContext.current
    val palette = JedaInTheme.colors

    var remainingMillis by remember {
        mutableLongStateOf(BreakSessionManager.getRemainingRestMillis(context))
    }

    var activityPrompt by remember {
        mutableStateOf(
            DailyActivityPromptProvider.getNextPrompt(
                currentHour = DailyActivityPromptProvider.getCurrentLocalHour(),
            ),
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            val remaining = BreakSessionManager.getRemainingRestMillis(context)
            remainingMillis = remaining
            if (remaining <= 0L) {
                onRestCompleted()
                break
            }
            delay(1000L)
        }
    }

    val totalRestMillis = remember { BreakSessionManager.getTotalRestMillis(context) }
    val progress = ((totalRestMillis - remainingMillis).toFloat() / totalRestMillis.toFloat())
        .coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(palette.targetCard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.SelfImprovement,
                contentDescription = null,
                tint = palette.textPrimary,
                modifier = Modifier.size(42.dp),
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Waktu jeda masih berlangsung.",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Rekomendasi Ide Kegiatan dari DailyActivityPromptProvider (Fitur Beranda)
        AnimatedContent(
            targetState = activityPrompt,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "RestingActivityPromptAnimation",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) { targetPrompt ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Badge Kategori
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.border),
                    shadowElevation = 0.dp,
                ) {
                    Text(
                        text = targetPrompt.category.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }

                // Nama Kegiatan (Headline)
                Text(
                    text = targetPrompt.headline,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                // Deskripsi Singkat Kegiatan (Subtitle)
                Text(
                    text = targetPrompt.subtitle,
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tombol Kontrol "Ganti Ide"
        Surface(
            onClick = {
                val currentHour = DailyActivityPromptProvider.getCurrentLocalHour()
                activityPrompt = DailyActivityPromptProvider.getNextPrompt(
                    currentHour = currentHour,
                    excludeId = activityPrompt.id,
                )
            },
            shape = CircleShape,
            color = palette.surface,
            border = BorderStroke(1.dp, palette.border),
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Ganti Ide",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "Ganti Ide",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Large Digital Countdown Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(20.dp))
                .padding(vertical = 28.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SISA WAKTU ISTIRAHAT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary,
                    letterSpacing = 1.sp,
                )

                Spacer(modifier = Modifier.height(10.dp))

                val seconds = (remainingMillis / 1000L) % 60L
                val minutes = (remainingMillis / 1000L) / 60L
                val formattedTime = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)

                Text(
                    text = formattedTime,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFeatureSettings = "tnum",
                    ),
                )

                Spacer(modifier = Modifier.height(18.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(6.dp)
                        .clip(CircleShape),
                    color = palette.accent,
                    trackColor = palette.divider,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNavigateHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.surface,
            ),
        ) {
            Text(
                text = "Kembali ke Beranda",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Tahap 1: Refleksi Napas Sejenak (Breathing Reflection).
 */
@Composable
private fun BreathingReflectionView(
    breathingSessionId: Int = 0,
    onProceedToConfirmation: () -> Unit,
    onCancelAndRest: () -> Unit,
) {
    val palette = JedaInTheme.colors

    // Timestamp saat layar pertama kali ditampilkan, disimpan lintas rotasi/Activity recreation
    val firstDisplayTimestamp = rememberSaveable(breathingSessionId) {
        SystemClock.elapsedRealtime()
    }

    var isButtonEnabled by remember(breathingSessionId) {
        mutableStateOf(
            InterventionActivity.isBreathingButtonDelayFinished(
                firstDisplayTimestamp = firstDisplayTimestamp,
                currentTimestamp = SystemClock.elapsedRealtime(),
            )
        )
    }

    LaunchedEffect(firstDisplayTimestamp) {
        val remaining = InterventionActivity.calculateRemainingBreathingDelay(
            firstDisplayTimestamp = firstDisplayTimestamp,
            currentTimestamp = SystemClock.elapsedRealtime(),
        )
        if (remaining > 0L) {
            delay(remaining)
        }
        isButtonEnabled = true
    }

    // Transisi halus opasitas tombol (~300 ms) saat berubah menjadi aktif (redup 45% saat nonaktif)
    val buttonAlpha by animateFloatAsState(
        targetValue = if (isButtonEnabled) 1f else 0.45f,
        animationSpec = tween(durationMillis = 300),
        label = "breathingButtonAlpha",
    )

    // Gentle pulsing animation for breathing
    val infiniteTransition = rememberInfiniteTransition(label = "breathingTransition")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )

    // Breathing guidance state
    var breathPhaseText by remember { mutableStateOf("Tarik napas perlahan...") }
    LaunchedEffect(Unit) {
        while (true) {
            breathPhaseText = "Tarik napas perlahan..."
            delay(4000L)
            breathPhaseText = "Tahan sejenak..."
            delay(2000L)
            breathPhaseText = "Hembuskan perlahan..."
            delay(4000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Tarik napas sejenak.",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Beri ruang bagi dirimu untuk bernapas dan hadir sepenuhnya sebelum melanjutkan.",
            fontSize = 14.sp,
            color = palette.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Animated Breathing Ring
        Box(
            modifier = Modifier
                .size(190.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Expanding outer ring
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(breathScale)
                    .clip(CircleShape)
                    .background(palette.targetCard.copy(alpha = 0.5f))
                    .border(1.5.dp, palette.border, CircleShape),
            )

            // Inner core
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .border(1.dp, palette.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Spa,
                    contentDescription = null,
                    tint = palette.textPrimary,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = breathPhaseText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onProceedToConfirmation,
            enabled = isButtonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .graphicsLayer { alpha = buttonAlpha },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.surface,
                disabledContainerColor = palette.accent,
                disabledContentColor = palette.surface,
            ),
        ) {
            Text(
                text = "Lanjut ke Konfirmasi",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
            onClick = onCancelAndRest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Batal & Ambil Sesi Jeda",
                fontSize = 13.sp,
                color = palette.textSecondary,
            )
        }
    }
}

/**
 * Tahap 2: Konfirmasi Sadar (Mengetik Kalimat Template).
 */
@Composable
private fun ConsciousConfirmationView(
    extensionMinutes: Int,
    onConfirmed: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = JedaInTheme.colors
    val template = remember(extensionMinutes) {
        BreakSessionManager.getConfirmationTemplate(extensionMinutes)
    }

    var typedText by rememberSaveable { mutableStateOf("") }
    val isValid = remember(typedText, template) {
        BreakSessionManager.validateConfirmationInput(typedText, template)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Yakin ingin melanjutkan?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Ketik kalimat di bawah ini secara manual sebagai konfirmasi sadar sebelum melanjutkan penggunaan:",
            fontSize = 14.sp,
            color = palette.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Template Sentence Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.targetCard)
                .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                .padding(18.dp),
        ) {
            Column {
                Text(
                    text = "KALIMAT KONFIRMASI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary,
                    letterSpacing = 1.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\"$template\"",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    lineHeight = 22.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Input TextField
        OutlinedTextField(
            value = typedText,
            onValueChange = { typedText = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Ketik kalimat di atas...",
                    fontSize = 14.sp,
                    color = palette.textSecondary.copy(alpha = 0.6f),
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isValid) palette.durationGreen else palette.accent,
                unfocusedBorderColor = if (isValid) palette.durationGreen else palette.border,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary,
                cursorColor = palette.accent,
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (isValid) ImeAction.Done else ImeAction.None,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (isValid) {
                        onConfirmed()
                    }
                },
            ),
            singleLine = false,
            minLines = 2,
            maxLines = 4,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Status Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isValid) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = palette.durationGreen,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "Kalimat sesuai. Kamu siap melanjutkan.",
                    fontSize = 12.sp,
                    color = palette.durationGreen,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Text(
                    text = "Ketik kalimat persis dengan urutan kata di atas.",
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onConfirmed,
            enabled = isValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.surface,
                disabledContainerColor = palette.targetCard,
                disabledContentColor = palette.textSecondary.copy(alpha = 0.5f),
            ),
        ) {
            Text(
                text = "Konfirmasi & Lanjutkan (+$extensionMinutes Menit)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Kembali ke Panduan Napas",
                fontSize = 13.sp,
                color = palette.textSecondary,
            )
        }
    }
}

/**
 * Bottom sheet untuk memilih durasi istirahat saat pengguna memilih "Ambil sesi jeda".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakDurationSelectionBottomSheet(
    initialMinutes: Int,
    initialIsCustom: Boolean,
    onDismiss: () -> Unit,
    onConfirmRest: (durationMinutes: Int, isCustom: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = JedaInTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedMinutes by rememberSaveable(initialMinutes) {
        mutableIntStateOf(initialMinutes)
    }
    var isCustom by rememberSaveable(initialIsCustom) {
        mutableStateOf(initialIsCustom)
    }
    var customTextInput by rememberSaveable {
        val defaultCustom = if (initialIsCustom) initialMinutes.toString() else "15"
        mutableStateOf(defaultCustom)
    }

    val parsedCustom = customTextInput.toIntOrNull()
    val isCustomValid = parsedCustom != null && parsedCustom in 1..180
    val customErrorMessage = when {
        customTextInput.isBlank() -> "Masukkan durasi istirahat."
        parsedCustom != null && parsedCustom < 1 -> "Minimal 1 menit."
        parsedCustom != null && parsedCustom > 180 -> "Maksimal 180 menit."
        else -> null
    }

    val activeDurationMinutes = if (isCustom) (parsedCustom ?: selectedMinutes) else selectedMinutes
    val isConfirmEnabled = if (isCustom) isCustomValid else true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surface,
        contentColor = palette.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Berapa lama kamu ingin beristirahat?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                )
                Text(
                    text = "Pilih durasi jeda yang sesuai dengan kebutuhanmu.",
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                    lineHeight = 18.sp,
                )
            }

            // Duration choices grid: 1, 3, 5, 10 menit & Custom
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RestDurationChipItem(
                        label = "1 menit",
                        isSelected = !isCustom && selectedMinutes == 1,
                        onClick = {
                            isCustom = false
                            selectedMinutes = 1
                        },
                        modifier = Modifier.weight(1f),
                    )
                    RestDurationChipItem(
                        label = "3 menit",
                        isSelected = !isCustom && selectedMinutes == 3,
                        onClick = {
                            isCustom = false
                            selectedMinutes = 3
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RestDurationChipItem(
                        label = "5 menit",
                        isSelected = !isCustom && selectedMinutes == 5,
                        onClick = {
                            isCustom = false
                            selectedMinutes = 5
                        },
                        modifier = Modifier.weight(1f),
                    )
                    RestDurationChipItem(
                        label = "10 menit",
                        isSelected = !isCustom && selectedMinutes == 10,
                        onClick = {
                            isCustom = false
                            selectedMinutes = 10
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                val customLabel = if (isCustom && parsedCustom != null && parsedCustom in 1..180) {
                    "Custom ($parsedCustom m)"
                } else {
                    "Custom"
                }
                RestDurationChipItem(
                    label = customLabel,
                    isSelected = isCustom,
                    onClick = {
                        isCustom = true
                        if (parsedCustom != null && parsedCustom in 1..180) {
                            selectedMinutes = parsedCustom
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Custom input field when Custom is selected
            if (isCustom) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = palette.background,
                    border = BorderStroke(1.dp, palette.border),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Tentukan Durasi Kustom",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = customTextInput,
                                onValueChange = { input ->
                                    val digitsOnly = input.filter { it.isDigit() }
                                    customTextInput = digitsOnly
                                    digitsOnly.toIntOrNull()?.let {
                                        if (it in 1..180) {
                                            selectedMinutes = it
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        text = "Contoh: 15",
                                        color = palette.textSecondary.copy(alpha = 0.5f),
                                    )
                                },
                                trailingIcon = {
                                    Text(
                                        text = "menit",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.textSecondary,
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (customErrorMessage != null) palette.durationRed else palette.accent,
                                    unfocusedBorderColor = if (customErrorMessage != null) palette.durationRed else palette.divider,
                                    focusedContainerColor = palette.surface,
                                    unfocusedContainerColor = palette.surface,
                                    cursorColor = palette.accent,
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textPrimary,
                                ),
                                isError = customErrorMessage != null,
                            )

                            // Quick +/- buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val current = customTextInput.toIntOrNull() ?: 15
                                            val next = (current - 1).coerceAtLeast(1)
                                            customTextInput = next.toString()
                                            selectedMinutes = next
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = palette.surface,
                                    border = BorderStroke(1.dp, palette.border),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Remove,
                                            contentDescription = "Kurang 1 menit",
                                            tint = palette.textPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val current = customTextInput.toIntOrNull() ?: 15
                                            val next = (current + 1).coerceAtMost(180)
                                            customTextInput = next.toString()
                                            selectedMinutes = next
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = palette.surface,
                                    border = BorderStroke(1.dp, palette.border),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Add,
                                            contentDescription = "Tambah 1 menit",
                                            tint = palette.textPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (customErrorMessage != null) {
                            Text(
                                text = customErrorMessage,
                                fontSize = 11.sp,
                                color = palette.durationRed,
                            )
                        }
                    }
                }
            }

            // Tombol "Mulai istirahat"
            Button(
                onClick = {
                    onConfirmRest(activeDurationMinutes, isCustom)
                },
                enabled = isConfirmEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.surface,
                    disabledContainerColor = palette.divider,
                    disabledContentColor = palette.textSecondary,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HourglassEmpty,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Mulai istirahat",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun RestDurationChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = JedaInTheme.colors

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) palette.accent else palette.background,
        label = "RestDurationBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) palette.surface else palette.textPrimary,
        label = "RestDurationText",
    )
    val borderColor = if (isSelected) palette.accent else palette.border

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

@Preview(name = "Layar Sesi Habis - Terang", showBackground = true)
@Composable
private fun SessionExpiredViewLightPreview() {
    JedaInTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = JedaInTheme.colors.background) {
            Box(modifier = Modifier.padding(24.dp)) {
                SessionExpiredView(
                    extensionMinutes = 3,
                    canExtend = true,
                    consecutiveExtensions = 1,
                    onTakeRest = {},
                    onContinueSession = {},
                    onNavigateHome = {},
                )
            }
        }
    }
}

@Preview(name = "Layar Sesi Habis - Gelap", showBackground = true)
@Composable
private fun SessionExpiredViewDarkPreview() {
    JedaInTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = JedaInTheme.colors.background) {
            Box(modifier = Modifier.padding(24.dp)) {
                SessionExpiredView(
                    extensionMinutes = 3,
                    canExtend = false,
                    consecutiveExtensions = 2,
                    onTakeRest = {},
                    onContinueSession = {},
                    onNavigateHome = {},
                )
            }
        }
    }
}

@Preview(name = "Layar Istirahat 5 Menit", showBackground = true)
@Composable
private fun RestingCountdownViewPreview() {
    JedaInTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = JedaInTheme.colors.background) {
            Box(modifier = Modifier.padding(24.dp)) {
                RestingCountdownView(
                    onRestCompleted = {},
                    onNavigateHome = {},
                )
            }
        }
    }
}

@Preview(name = "Panduan Napas", showBackground = true)
@Composable
private fun BreathingReflectionViewPreview() {
    JedaInTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = JedaInTheme.colors.background) {
            Box(modifier = Modifier.padding(24.dp)) {
                BreathingReflectionView(
                    onProceedToConfirmation = {},
                    onCancelAndRest = {},
                )
            }
        }
    }
}

@Preview(name = "Konfirmasi Mengetik", showBackground = true)
@Composable
private fun ConsciousConfirmationViewPreview() {
    JedaInTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = JedaInTheme.colors.background) {
            Box(modifier = Modifier.padding(24.dp)) {
                ConsciousConfirmationView(
                    extensionMinutes = 3,
                    onConfirmed = {},
                    onBack = {},
                )
            }
        }
    }
}
