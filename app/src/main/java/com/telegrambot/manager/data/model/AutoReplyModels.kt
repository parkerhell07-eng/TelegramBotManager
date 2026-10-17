package com.telegrambot.manager.data.model

/**
 * Per-bot auto-reply configuration consumed by the background engine
 * ([com.telegrambot.manager.data.engine.BotEngine]).
 *
 * Placeholders usable in every reply text:
 *   {name}      → sender first name (or "دوست عزیز")
 *   {username}  → sender @username (or empty)
 *   {chat_id}   → numeric chat id
 *   {bot_name}  → bot display name
 */
data class BotAutoReply(
    val botId: String = "",
    val enabled: Boolean = false,
    // /start welcome
    val welcomeEnabled: Boolean = true,
    val welcomeText: String = DEFAULT_WELCOME,
    // fallback for unknown /commands ("" = stay silent)
    val unknownText: String = DEFAULT_UNKNOWN,
    // /command → reply
    val commands: List<CommandReply> = emptyList(),
    // keyword (contains, case-insensitive) → reply
    val keywords: List<KeywordReply> = emptyList()
) {
    data class CommandReply(val command: String = "", val reply: String = "")
    data class KeywordReply(val keyword: String = "", val reply: String = "")

    companion object {
        const val DEFAULT_WELCOME =
            "سلام {name} عزیز 👋\nبه ربات {bot_name} خوش آمدید!\nبرای دیدن امکانات، دکمه منو را باز کنید یا /help بفرستید."
        const val DEFAULT_UNKNOWN =
            "دستور ناشناخته است 🙏\nدکمه منو را باز کنید یا /help بفرستید تا فهرست دستورات را ببینید."
    }
}
