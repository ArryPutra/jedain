package com.example.jedain.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// JedaIn Minimalist Monochrome Palette (Light Mode)
val BackgroundOffWhite = Color(0xFFF7F7F5)
val SurfaceWhite = Color(0xFFFFFFFF)
val TextNearBlack = Color(0xFF171717)
val TextMediumGray = Color(0xFF707070)
val DividerSubtleGray = Color(0xFFE7E7E7)
val BorderSubtleGray = Color(0xFFE7E7E7)
val ButtonLightGray = Color(0xFFEEEEEC)
val IconCharcoal = Color(0xFF2C2C2C)
val IconMutedGray = Color(0xFF777777)
val WarningBackgroundGray = Color(0xFFF2F2F0)

// JedaIn Minimalist Monochrome Palette (Dark Mode)
val BackgroundDarkCharcoal = Color(0xFF121212)
val SurfaceDarkCharcoal = Color(0xFF1C1C1C)
val TextPrimaryDark = Color(0xFFF4F4F4)
val TextSecondaryDark = Color(0xFF8E8E8E)
val ButtonDarkGray = Color(0xFF262626)
val DividerDarkGray = Color(0xFF2B2B2B)
val BorderDarkGray = Color(0xFF2B2B2B)
val IconCharcoalDark = Color(0xFFE0E0E0)
val WarningBackgroundDark = Color(0xFF242422)

// Semantic Duration Colors (Light Mode)
val DurationGreenLight = Color(0xFF16A34A)
val DurationGreenBgLight = Color(0xFFF0FDF4)
val DurationYellowLight = Color(0xFFD97706)
val DurationYellowBgLight = Color(0xFFFFFBEB)
val DurationOrangeLight = Color(0xFFEA580C)
val DurationOrangeBgLight = Color(0xFFFFF7ED)
val DurationRedLight = Color(0xFFDC2626)
val DurationRedBgLight = Color(0xFFFEF2F2)
val DurationCustomBlueLight = Color(0xFF2563EB)
val DurationCustomBlueBgLight = Color(0xFFEFF6FF)

// Semantic Duration Colors (Dark Mode)
val DurationGreenDark = Color(0xFF4ADE80)
val DurationGreenBgDark = Color(0xFF132A1C)
val DurationYellowDark = Color(0xFFFACC15)
val DurationYellowBgDark = Color(0xFF2B2411)
val DurationOrangeDark = Color(0xFFFB923C)
val DurationOrangeBgDark = Color(0xFF2E1C12)
val DurationRedDark = Color(0xFFF87171)
val DurationRedBgDark = Color(0xFF2F1616)
val DurationCustomBlueDark = Color(0xFF60A5FA)
val DurationCustomBlueBgDark = Color(0xFF152238)

@Immutable
data class JedaInColorPalette(
    val background: Color,
    val surface: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val targetCard: Color,
    val divider: Color,
    val border: Color,
    val warningBackground: Color,
    val iconCharcoal: Color = textPrimary,
    val iconMuted: Color = textSecondary,
    val durationGreen: Color = DurationGreenLight,
    val durationGreenBg: Color = DurationGreenBgLight,
    val durationYellow: Color = DurationYellowLight,
    val durationYellowBg: Color = DurationYellowBgLight,
    val durationOrange: Color = DurationOrangeLight,
    val durationOrangeBg: Color = DurationOrangeBgLight,
    val durationRed: Color = DurationRedLight,
    val durationRedBg: Color = DurationRedBgLight,
    val durationCustomBlue: Color = DurationCustomBlueLight,
    val durationCustomBlueBg: Color = DurationCustomBlueBgLight,
    val isDark: Boolean,
)

val LightJedaInColors = JedaInColorPalette(
    background = BackgroundOffWhite,
    surface = SurfaceWhite,
    accent = TextNearBlack,
    textPrimary = TextNearBlack,
    textSecondary = TextMediumGray,
    targetCard = ButtonLightGray,
    divider = DividerSubtleGray,
    border = BorderSubtleGray,
    warningBackground = WarningBackgroundGray,
    iconCharcoal = IconCharcoal,
    iconMuted = IconMutedGray,
    durationGreen = DurationGreenLight,
    durationGreenBg = DurationGreenBgLight,
    durationYellow = DurationYellowLight,
    durationYellowBg = DurationYellowBgLight,
    durationOrange = DurationOrangeLight,
    durationOrangeBg = DurationOrangeBgLight,
    durationRed = DurationRedLight,
    durationRedBg = DurationRedBgLight,
    durationCustomBlue = DurationCustomBlueLight,
    durationCustomBlueBg = DurationCustomBlueBgLight,
    isDark = false,
)

val DarkJedaInColors = JedaInColorPalette(
    background = BackgroundDarkCharcoal,
    surface = SurfaceDarkCharcoal,
    accent = TextPrimaryDark,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    targetCard = ButtonDarkGray,
    divider = DividerDarkGray,
    border = BorderDarkGray,
    warningBackground = WarningBackgroundDark,
    iconCharcoal = IconCharcoalDark,
    iconMuted = TextSecondaryDark,
    durationGreen = DurationGreenDark,
    durationGreenBg = DurationGreenBgDark,
    durationYellow = DurationYellowDark,
    durationYellowBg = DurationYellowBgDark,
    durationOrange = DurationOrangeDark,
    durationOrangeBg = DurationOrangeBgDark,
    durationRed = DurationRedDark,
    durationRedBg = DurationRedBgDark,
    durationCustomBlue = DurationCustomBlueDark,
    durationCustomBlueBg = DurationCustomBlueBgDark,
    isDark = true,
)
