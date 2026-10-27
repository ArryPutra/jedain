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
    isDark = true,
)
