package com.telegrambot.manager.ui.theme

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

private val TelegramBlue = Color(0xFF2AABEE)
private val TelegramBlueDark = Color(0xFF229ED9)

private val LightColors = lightColorScheme(
    primary = TelegramBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F0FF),
    onPrimaryContainer = Color(0xFF00344A),
    secondary = Color(0xFF4A636D),
    onSecondary = Color.White,
    background = Color(0xFFF6FAFD),
    surface = Color.White,
    surfaceVariant = Color(0xFFE2ECEF),
    onSurfaceVariant = Color(0xFF40484C)
)

private val DarkColors = darkColorScheme(
    primary = TelegramBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF004B66),
    onPrimaryContainer = Color(0xFFC0E9FF),
    secondary = Color(0xFFB4C9D2),
    onSecondary = Color(0xFF1F333C),
    background = Color(0xFF17212B),
    surface = Color(0xFF232E3C),
    surfaceVariant = Color(0xFF324148),
    onSurfaceVariant = Color(0xFFBAC8D0)
)

/**
 * App theme: uses Android 12+ Dynamic Color when available (user's answer),
 * and falls back to a Telegram-inspired blue palette.
 */
@Composable
fun TelegramBotManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
