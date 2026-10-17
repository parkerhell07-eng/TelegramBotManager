package com.telegrambot.manager.data.store

import android.content.Context
import android.content.SharedPreferences
import com.telegrambot.manager.data.util.L10n
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Theme appearance chosen by the user. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Small key/value settings (theme, language, update bookkeeping) kept in a
 * plain SharedPreferences — nothing here is secret (tokens stay in
 * EncryptedSharedPreferences inside BotStore).
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // ── Theme ────────────────────────────────────────────────────────────
    private val _theme = MutableStateFlow(themeFromInt(prefs.getInt(KEY_THEME, 0)))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun themeMode(): ThemeMode = _theme.value

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putInt(KEY_THEME, mode.ordinal).apply()
        _theme.value = mode
    }

    // ── Language ─────────────────────────────────────────────────────────
    private val _lang = MutableStateFlow(
        if (prefs.getString(KEY_LANG, "fa") == "en") L10n.Lang.EN else L10n.Lang.FA
    )
    val lang: StateFlow<L10n.Lang> = _lang.asStateFlow()

    fun setLang(lang: L10n.Lang) {
        prefs.edit().putString(KEY_LANG, lang.code).apply()
        _lang.value = lang
        L10n.set(lang)
    }

    // ── In-app update bookkeeping ────────────────────────────────────────
    fun lastUpdateCheck(): Long = prefs.getLong(KEY_LAST_CHECK, 0L)

    fun setLastUpdateCheck(ts: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK, ts).apply()
    }

    fun skippedVersion(): String? = prefs.getString(KEY_SKIPPED, null)

    fun setSkippedVersion(version: String?) {
        if (version == null) prefs.edit().remove(KEY_SKIPPED).apply()
        else prefs.edit().putString(KEY_SKIPPED, version).apply()
    }

    private fun themeFromInt(i: Int): ThemeMode =
        ThemeMode.entries.getOrElse(i) { ThemeMode.SYSTEM }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LANG = "lang"
        private const val KEY_LAST_CHECK = "last_update_check"
        private const val KEY_SKIPPED = "skipped_version"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
