package com.protectfinanceddevices.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Slate800,
    onPrimaryContainer = Slate100,
    secondary = EmeraldGreen,
    onSecondary = Color.White,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    error = CrimsonRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Slate100,
    onPrimaryContainer = Slate900,
    secondary = EmeraldGreen,
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    error = CrimsonRed,
    onError = Color.White
)

@Composable
fun ProtectFinancedDevicesTheme(
    darkTheme: Boolean = true, // Default to sleek enterprise fintech dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
