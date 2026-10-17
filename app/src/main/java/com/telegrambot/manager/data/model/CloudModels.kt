package com.telegrambot.manager.data.model

/**
 * Per-bot configuration for the cloud brain (Cloudflare Workers).
 *
 * When [enabled] is true, Telegram delivers every message to a tiny Worker
 * function running on Cloudflare's edge — the bot answers commands 24/7 with
 * the phone completely off. The phone app becomes a management dashboard.
 *
 * The worker script embeds the bot token + reply rules at deploy time; this
 * config only keeps bookkeeping data (never the token).
 */
data class BotCloud(
    val botId: String = "",
    val enabled: Boolean = false,
    /** Cloudflare account id (auto-resolved when blank). */
    val accountId: String = "",
    /** Uploaded worker script name, e.g. tbm_ab12… */
    val scriptName: String = "",
    /** Public base URL on workers.dev (webhook = url + /hook). */
    val url: String = "",
    /** Shared secret echoed by Telegram in X-Telegram-Bot-Api-Secret-Token. */
    val secret: String = "",
    /** Owner's private chat id — every user message is forwarded there. */
    val adminChatId: Long = 0L,
    val deployedAt: Long = 0L
)

/** Global Cloudflare API credentials shared by every bot. */
data class CloudCredentials(
    val apiToken: String = "",
    val accountId: String = ""
)
