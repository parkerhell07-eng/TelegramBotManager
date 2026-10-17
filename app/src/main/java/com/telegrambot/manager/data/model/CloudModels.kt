package com.telegrambot.manager.data.model

/**
 * Per-bot configuration for the cloud brain (Cloudflare Workers).
 *
 * When [enabled] is true, Telegram delivers every message to a tiny Worker
 * function running on Cloudflare's edge — the bot answers commands 24/7 with
 * the phone completely off. The phone app becomes a management dashboard.
 *
 * Two deployment modes exist:
 *  - [MODE_SHARED]: the app's ready-made hub (one Worker shared by all app
 *    users, deployed once by the app owner). Zero setup: no Cloudflare
 *    account, no API token. The bot registers itself on the hub and the hub
 *    runs the same reply rules. The bot token is stored AES-GCM encrypted on
 *    the hub and can be removed any time via unregister.
 *  - [MODE_OWN]: the user's personal Cloudflare account (advanced). The app
 *    uploads a dedicated Worker per bot into the user's own account.
 *
 * For MODE_OWN the worker script embeds the bot token + reply rules at deploy
 * time; this config only keeps bookkeeping data (never the token). For
 * MODE_SHARED the hub holds the encrypted token; this config keeps the route
 * name and the per-bot webhook secret.
 */
data class BotCloud(
    val botId: String = "",
    val enabled: Boolean = false,
    /** "shared" (app's ready hub) or "own" (user's Cloudflare account). */
    val mode: String = MODE_OWN,
    /** Cloudflare account id (auto-resolved when blank). Own mode only. */
    val accountId: String = "",
    /** Uploaded worker script name, e.g. tbm_ab12… Own mode only. */
    val scriptName: String = "",
    /**
     * Public base URL: for MODE_OWN the per-bot workers.dev URL
     * (webhook = url + /hook); for MODE_SHARED the hub base URL
     * (webhook = url + /hook/ + route).
     */
    val url: String = "",
    /** Hub route name for MODE_SHARED, e.g. b1234567890. */
    val route: String = "",
    /** Shared secret echoed by Telegram in X-Telegram-Bot-Api-Secret-Token. */
    val secret: String = "",
    /** Owner's private chat id — every user message is forwarded there. */
    val adminChatId: Long = 0L,
    val deployedAt: Long = 0L
) {
    val isShared: Boolean get() = mode == MODE_SHARED
    val isOwn: Boolean get() = mode != MODE_SHARED

    companion object {
        const val MODE_SHARED = "shared"
        const val MODE_OWN = "own"
    }
}

/** Global Cloudflare API credentials shared by every bot (own mode). */
data class CloudCredentials(
    val apiToken: String = "",
    val accountId: String = "",
    /**
     * Optional override of the shared hub base URL. When blank the app uses
     * BuildConfig.HUB_URL. Useful for self-hosted hubs on a different
     * workers.dev subdomain.
     */
    val hubUrlOverride: String = ""
)
