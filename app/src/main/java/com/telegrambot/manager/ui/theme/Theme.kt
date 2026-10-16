package com.telegrambot.manager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Brand accents ────────────────────────────────────────────────────────────
val TgBlue = Color(0xFF2AABEE)
val PremiumPurple = Color(0xFF8774E1)
val AccentTeal = Color(0xFF2BD9C7)
val AccentOrange = Color(0xFFFFA45C)
val AccentPink = Color(0xFFFF6BA8)
val AccentGreen = Color(0xFF5BDB8B)

// ── Dark premium surfaces ────────────────────────────────────────────────────
private val Bg = Color(0xFF0A1120)
private val Surface = Color(0xFF101B2D)
private val SurfaceHigh = Color(0xFF17253D)
private val Outline = Color(0xFF24365A)

private val DarkColors = darkColorScheme(
    primary = TgBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF123A54),
    onPrimaryContainer = Color(0xFFBFE7FF),
    secondary = PremiumPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2E2660),
    onSecondaryContainer = Color(0xFFE4DEFF),
    tertiary = AccentGreen,
    onTertiary = Color(0xFF062B15),
    background = Bg,
    onBackground = Color(0xFFEAF2FB),
    surface = Surface,
    onSurface = Color(0xFFEAF2FB),
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Color(0xFF9FB0CB),
    outline = Outline,
    outlineVariant = Outline,
    error = Color(0xFFFF5C7A),
    onError = Color.White,
    errorContainer = Color(0xFF4A1424),
    onErrorContainer = Color(0xFFFFD9DE)
)

// ── Shared gradients (used by buttons, avatars, hero headers) ────────────────
val BlueGradient = Brush.linearGradient(listOf(Color(0xFF2AABEE), Color(0xFF1E8FD0)))
val PurpleGradient = Brush.linearGradient(listOf(Color(0xFF8774E1), Color(0xFF5A8DEE)))
val HeroGradient = Brush.linearGradient(
    listOf(Color(0xFF123B63), Color(0xFF0F2946), Color(0xFF2C2461))
)
private val DisabledGradient = Brush.linearGradient(listOf(Color(0xFF1A2740), Color(0xFF1A2740)))

/** Bot avatar gradients — deterministic pick per bot id gives every bot its own color. */
val AvatarGradients = listOf(
    Brush.linearGradient(listOf(Color(0xFF2AABEE), Color(0xFF1B84C9))),
    Brush.linearGradient(listOf(Color(0xFF8774E1), Color(0xFF6C4FD8))),
    Brush.linearGradient(listOf(Color(0xFF2BD9C7), Color(0xFF18A99A))),
    Brush.linearGradient(listOf(Color(0xFFFFA45C), Color(0xFFF2762E))),
    Brush.linearGradient(listOf(Color(0xFFFF6BA8), Color(0xFFE24B85))),
    Brush.linearGradient(listOf(Color(0xFF5BDB8B), Color(0xFF2FAF62)))
)

/**
 * App theme — a fixed "Telegram Premium" dark look so the whole app feels
 * cohesive, modern and professional regardless of system settings.
 */
@Composable
fun TelegramBotManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content
    )
}
