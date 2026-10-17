package com.telegrambot.manager.data.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight bilingual (fa/en) support.
 *
 * The primary audience is Persian, so every screen keeps its polished Persian
 * copy. The [st] helper lets screens pick between a Persian and an English
 * string at composition time based on the user's language choice stored in
 * SettingsStore. The engine/notification layer reads [lang] directly.
 */
object L10n {

    enum class Lang(val code: String) { FA("fa"), EN("en") }

    private val _lang = MutableStateFlow(Lang.FA)
    val lang: StateFlow<Lang> = _lang.asStateFlow()

    /** Current language for non-composable layers (notifications, engine). */
    val current: Lang get() = _lang.value

    /** Called once at app start (and on change) by SettingsScreen/MainActivity. */
    fun set(lang: Lang) {
        _lang.value = lang
    }

    /** Non-composable variant of [st] — uses the current flow value. */
    fun str(fa: String, en: String): String = if (_lang.value == Lang.EN) en else fa
}

/** CompositionLocal holding the active language for the UI layer. */
val LocalLang = staticCompositionLocalOf { L10n.Lang.FA }

/** Picks the English or Persian copy depending on the active language. */
@Composable
fun st(fa: String, en: String): String =
    if (LocalLang.current == L10n.Lang.EN) en else fa
