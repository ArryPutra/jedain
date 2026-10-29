package com.example.jedain

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jedain.ui.theme.JedaInTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

enum class JedaInScreen {
    HOME,
    SET_TARGET,
}

// --- Models & Dummy Data ---

@Immutable
data class MonitoredAppItem(
    val name: String,
    val duration: String,
    val icon: ImageVector,
)

private val DefaultMonitoredApps = listOf(
    MonitoredAppItem(
        name = "Instagram",
        duration = "1j 42m",
        icon = Icons.Default.CameraAlt,
    ),
    MonitoredAppItem(
        name = "TikTok",
        duration = "1j 12m",
        icon = Icons.Default.MusicNote,
    ),
    MonitoredAppItem(
        name = "X",
        duration = "30m",
        icon = Icons.Default.AlternateEmail,
    ),
)

// --- Root Screen ---

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember(context) { TargetSettingsPreferences(context) }
    var targetSettings by remember { mutableStateOf(preferences.loadSettings()) }
    var currentScreen by rememberSaveable { mutableStateOf(JedaInScreen.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                        monitoredApps = DefaultMonitoredApps,
                        isDarkTheme = isDarkTheme,
                        onToggleDarkTheme = {
                            isDarkThemeUserOverride = !isDarkTheme
                        },
                        onTargetSettingsClick = {
                            currentScreen = JedaInScreen.SET_TARGET
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
                        },
                        onSaveSettings = { newSettings ->
                            preferences.saveSettings(newSettings)
                            targetSettings = newSettings
                            currentScreen = JedaInScreen.HOME
                            scope.launch {
                                snackbarHostState.showSnackbar("Pengaturan target disimpan")
                            }
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
    monitoredApps: List<MonitoredAppItem>,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    modifier: Modifier = Modifier,
    onTargetSettingsClick: () -> Unit = {},
    onManageAppsClick: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val isScrolled = scrollState.canScrollBackward

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
                // Sapaan dan Tanggal
                GreetingSection(dateText = dateText)

                // Kartu Waktu Layar Hari Ini (dengan tombol Atur Target di dalamnya)
                TodayScreenTimeCard(
                    durationText = "3j 24m",
                    changeText = "45 menit lebih sedikit dari kemarin",
                    comparisonNote = "Dibandingkan pada waktu yang sama",
                    onTargetSettingsClick = onTargetSettingsClick,
                )

                // Daftar Aplikasi yang Dipantau & Tombol Kelola Aplikasi
                MonitoredAppsSection(
                    apps = monitoredApps,
                    onManageAppsClick = onManageAppsClick,
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
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
) {
    val colors = JedaInTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = dateText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary,
            letterSpacing = 0.1.sp,
        )
        Text(
            text = "Halo, selamat datang.",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            letterSpacing = (-0.5).sp,
        )
    }
}

// --- Section 2: Kartu Waktu Layar Hari Ini ---

@Composable
fun TodayScreenTimeCard(
    durationText: String,
    changeText: String,
    comparisonNote: String,
    modifier: Modifier = Modifier,
    onTargetSettingsClick: () -> Unit = {},
) {
    val colors = JedaInTheme.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
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
                // Header kartu: Judul & Icon Ponsel Berlatar Bulat Lembut
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Waktu layar hari ini",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary,
                    )

                    Surface(
                        shape = CircleShape,
                        color = colors.targetCard,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = colors.iconCharcoal,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Angka Waktu Layar: Elemen Visual Paling Menonjol
                Text(
                    text = durationText,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    letterSpacing = (-1.2).sp,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Perbandingan Waktu: Trend Icon Monokrom & Teks Dekat-Hitam
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = colors.iconCharcoal,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = changeText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Catatan Pendukung
                Text(
                    text = comparisonNote,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Tombol Pill-Shaped Abu-Abu Terang dengan Teks Hitam
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

// --- Section 3: Bagian Aplikasi yang Dipantau & Tombol Kelola Aplikasi ---

@Composable
fun MonitoredAppsSection(
    apps: List<MonitoredAppItem>,
    onManageAppsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header bagian
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
                text = "${apps.size} aplikasi",
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
            Column(modifier = Modifier.fillMaxWidth()) {
                apps.forEachIndexed { index, app ->
                    MonitoredAppRow(item = app)
                    if (index < apps.lastIndex) {
                        HorizontalDivider(
                            color = colors.divider,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
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
    item: MonitoredAppItem,
    modifier: Modifier = Modifier,
) {
    val colors = JedaInTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Icon aplikasi monokrom dalam kotak abu-abu bersudut halus
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.targetCard,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppMonochromeIcon(
                        appName = item.name,
                        fallbackIcon = item.icon,
                        tint = colors.iconCharcoal,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Waktu layar",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
        }

        Text(
            text = item.duration,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun AppMonochromeIcon(
    appName: String,
    fallbackIcon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    when (appName) {
        "X" -> {
            Canvas(modifier = modifier.size(18.dp)) {
                val strokeWidth = 2.4.dp.toPx()
                val padding = size.width * 0.16f
                drawLine(
                    color = tint,
                    start = Offset(padding, padding),
                    end = Offset(size.width - padding, size.height - padding),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width - padding, padding),
                    end = Offset(padding, size.height - padding),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
        else -> {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = appName,
                tint = tint,
                modifier = modifier.size(20.dp),
            )
        }
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
