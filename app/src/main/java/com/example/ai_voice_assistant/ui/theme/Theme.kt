package com.example.ai_voice_assistant.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = BackgroundStart,
    surface = BackgroundStart,
    onBackground = Color.White,
    onSurface = Color.White
)

// Force dark theme for the Glassmorphism look
private val ForceDarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = BackgroundStart,
    surface = BackgroundStart,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun AI_voice_assistantTheme(
    darkTheme: Boolean = true, // Always true for this app's style
    dynamicColor: Boolean = false, // Disabled to maintain consistent glass look
    content: @Composable () -> Unit
) {
    val colorScheme = ForceDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
