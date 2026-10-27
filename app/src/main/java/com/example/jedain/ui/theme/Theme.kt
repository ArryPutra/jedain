package com.example.jedain.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalJedaInColors = staticCompositionLocalOf { LightJedaInColors }

object JedaInTheme {
    val colors: JedaInColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalJedaInColors.current
}

@Composable
fun JedaInTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkJedaInColors else LightJedaInColors
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.surface,
            primaryContainer = palette.targetCard,
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.targetCard,
            onSecondary = palette.textPrimary,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.background,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.surface,
            primaryContainer = palette.targetCard,
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.targetCard,
            onSecondary = palette.textPrimary,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.background,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider,
        )
    }

    CompositionLocalProvider(LocalJedaInColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
