package com.telegrambot.manager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import com.telegrambot.manager.R

/** Persian-first font family (Vazirmatn) — crisp, modern and highly readable. */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private val Defaults = Typography()

/** Material 3 typography wired to Vazirmatn across every style. */
val AppTypography = Typography(
    displayLarge = Defaults.displayLarge.copy(fontFamily = Vazirmatn),
    displayMedium = Defaults.displayMedium.copy(fontFamily = Vazirmatn),
    displaySmall = Defaults.displaySmall.copy(fontFamily = Vazirmatn),
    headlineLarge = Defaults.headlineLarge.copy(fontFamily = Vazirmatn),
    headlineMedium = Defaults.headlineMedium.copy(fontFamily = Vazirmatn),
    headlineSmall = Defaults.headlineSmall.copy(fontFamily = Vazirmatn),
    titleLarge = Defaults.titleLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
    titleMedium = Defaults.titleMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold),
    titleSmall = Defaults.titleSmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold),
    bodyLarge = Defaults.bodyLarge.copy(fontFamily = Vazirmatn),
    bodyMedium = Defaults.bodyMedium.copy(fontFamily = Vazirmatn),
    bodySmall = Defaults.bodySmall.copy(fontFamily = Vazirmatn),
    labelLarge = Defaults.labelLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium),
    labelMedium = Defaults.labelMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium),
    labelSmall = Defaults.labelSmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium)
)
