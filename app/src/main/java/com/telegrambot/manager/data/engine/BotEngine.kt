package com.telegrambot.manager.data.engine

import android.content.Context
import com.telegrambot.manager.data.api.ChatSync
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.BotAutoReply
import com.telegrambot.manager.data.model.ChatMsg
import com.telegrambot.manager.data.model.TgUpdate
import com.telegrambot.manager.data.store.AutoReplyStore
import com.telegrambot.manager.data.store.BotStore
import com.telegrambot.manager.data.store.ChatStore
import com.telegrambot.manager.data.util.L10n
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The 24/7 answer machine.
 *
 * WHY THIS EXISTS — a Telegram bot is not a program that runs somewhere; it is
 * just an HTTP endpoint. Someone must repeatedly call getUpdates and answer
 * with sendMessage. Until now the app only listened while the Chats / Updates
 * screen was open and NEVER auto-answered — that is why /start and other
 * commands went unanswered. [BotEngine] long-polls getUpdates for every bot
 * whose auto-reply is enabled (from a foreground service), matches each
 * incoming text against the configured commands / keywords / welcome /
 * fallback rules and answers automatically.
 */
object BotEngine {

    private const val LONG_POLL_SECONDS = 25
    private const val SEND_PACING_MS = 80L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rootJob: Job? = null

    private val _polling = MutableStateFlow<Set<String>>(emptySet())

    /** Bot ids currently being long-polled (drives UI badges + ChatSync guard). */
    val polling: StateFlow<Set<String>> = _polling.asStateFlow()

    fun isPolling(botId: String): Boolean = _polling.value.contains(botId)

    /** Entry point used by [com.telegrambot.manager.BotEngineService]. */
    fun run(context: Context): Job {
        stopInternal()
        val app = context.applicationContext
        val job = scope.launch {
            while (isActive) {
                val bots = BotStore.get(app).loadBots()
                val store = AutoReplyStore.get(app)
                val cloudStore = com.telegrambot.manager.data.store.CloudStore.get(app)
                val active = bots.mapNotNull { b ->
                    val cfg = store.load(b.id)
                    if (!cfg.enabled) return@mapNotNull null
                    // Cloud-enabled bots are answered by GitHub Actions — the
                    // phone must NOT also poll them (Telegram would 409).
                    if (cloudStore.peek(b.id)?.enabled == true) return@mapNotNull null
                    b to cfg
                }
                _polling.value = active.map { it.first.id }.toSet()
                if (active.isEmpty()) {
                    // Nothing to listen to — stop the foreground service so
                    // Android can drop the notification and save battery.
                    com.telegrambot.manager.BotEngineService.stop(app)
                    break
                }
                val jobs = active.map { (bot, cfg) ->
                    launch { runBot(app, bot, cfg) }
                }
                jobs.forEach { it.join() }
            }
        }
        rootJob = job
        return job
    }

    fun shutdown() {
        stopInternal()
    }

    private fun stopInternal() {
        rootJob?.cancel()
        rootJob = null
        _polling.value = emptySet()
    }

    // ── Per-bot long-poll loop ───────────────────────────────────────────

    private suspend fun runBot(app: Context, bot: Bot, cfg: BotAutoReply) {
        val replyStore = AutoReplyStore.get(app)
        var offset = replyStore.offset(bot.id)
        var drained = offset > 0   // first-ever run: swallow backlog silently
        var webhookHealed = false

        while (kotlin.coroutines.coroutineContext.isActive) {
            when (val r = TelegramRepo.getUpdates(bot.token, if (offset > 0) offset else null, LONG_POLL_SECONDS)) {
                is TelegramRepo.ApiResult.Success -> {
                    if (r.data.isEmpty()) continue
                    if (!drained) {
                        // Old unconfirmed updates from before the engine ever
                        // ran: advance past them WITHOUT replying so a freshly
                        // configured bot does not spam old chats.
                        offset = (r.data.maxOf { it.updateId }) + 1
                        drained = true
                        replyStore.saveOffset(bot.id, offset)
                        continue
                    }
                    for (u in r.data) {
                        if (u.updateId >= offset) offset = u.updateId + 1
                        try {
                            handleUpdate(app, bot, cfg, u)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // one bad update must never kill the listener
                        }
                    }
                    replyStore.saveOffset(bot.id, offset)
                }
                is TelegramRepo.ApiResult.Failure -> {
                    if (r.code == 409 && !webhookHealed) {
                        // A webhook left over from other tooling blocks
                        // getUpdates; drop it once and keep listening.
                        webhookHealed = true
                        val info = TelegramRepo.getWebhookInfo(bot.token)
                        if (info is TelegramRepo.ApiResult.Success && !info.data.url.isNullOrEmpty()) {
                            TelegramRepo.deleteWebhook(bot.token, null)
                        }
                        delay(2_000)
                    } else {
                        delay(if (r.code == 401) 30_000 else 8_000)
                    }
                }
            }
        }
    }

    // ── Single update → decide reply → send ─────────────────────────────

    private suspend fun handleUpdate(app: Context, bot: Bot, cfg: BotAutoReply, u: TgUpdate) {
        val m = u.message ?: return          // replies only to fresh messages
        val chat = m.chat ?: return
        val from = m.from
        if (from?.isBot == true) return      // never answer our own kind

        // Store the incoming message in the local chat history (same format
        // as the manual sync so every screen keeps working unchanged).
        try {
            ChatSync.ingest(bot.id, ChatStore.get(app), listOf(u))
        } catch (e: Exception) {
        }

        val text = m.text?.trim().takeUnless { it.isNullOrEmpty() } ?: return
        val isGroup = chat.type == "group" || chat.type == "supergroup" || chat.type == "channel"

        val reply = decideReply(bot, cfg, text, chat.id.toString(), from?.firstName, from?.username, isGroup)
            ?: return

        val sent = TelegramRepo.sendMessage(
            bot.token, chat.id.toString(), reply, null, null, null
        )
        if (sent is TelegramRepo.ApiResult.Success) {
            appendOutgoing(app, bot, chat.id, reply, sent.data.messageId, sent.data.date)
        }
        delay(SEND_PACING_MS)
    }

    /**
     * Match order: exact command (built-ins first, then user commands) →
     * keyword → unknown-command fallback. Keywords are only honoured in
     * private chats to keep groups quiet; in groups only commands aimed at
     * THIS bot are answered.
     */
    private fun decideReply(
        bot: Bot,
        cfg: BotAutoReply,
        text: String,
        chatId: String,
        firstName: String?,
        username: String?,
        isGroup: Boolean
    ): String? {
        if (text.startsWith("/")) {
            val head = text.drop(1).substringBefore(" ")
            val target = head.substringBefore("@").lowercase()
            if (isGroup) {
                val mention = head.substringAfter("@", "")
                if (mention.isNotEmpty() && mention.lowercase() != bot.username.lowercase()) return null
            }
            return when (target) {
                "start" -> if (cfg.welcomeEnabled) fill(cfg.welcomeText, bot, chatId, firstName, username) else null
                "help" -> fill(buildHelp(cfg, bot), bot, chatId, firstName, username)
                "id" -> fill(
                    L10n.str(
                        "شناسه چت شما:\n{chat_id}\n\nاین عدد را در ابزارهای ارسال به‌عنوان chat_id وارد کنید.",
                        "Your chat id:\n{chat_id}\n\nUse this number as chat_id in the sending tools."
                    ), bot, chatId, firstName, username
                )
                "about" -> fill(
                    L10n.str(
                        "{bot_name}\nمدیریت‌شده با اپ «مدیر ربات تلگرام».",
                        "{bot_name}\nManaged by the Telegram Bot Manager app."
                    ), bot, chatId, firstName, username
                )
                else -> {
                    val hit = cfg.commands
                        .firstOrNull { it.command.trim().trimStart('/').lowercase() == target }
                        ?.takeIf { it.reply.isNotBlank() }
                    hit?.let { return fill(it.reply, bot, chatId, firstName, username) }
                    if (cfg.unknownText.isNotBlank()) fill(cfg.unknownText, bot, chatId, firstName, username) else null
                }
            }
        }
        if (isGroup) return null
        val lower = text.lowercase()
        val kw = cfg.keywords.firstOrNull { k ->
            val key = k.keyword.trim().lowercase()
            key.isNotEmpty() && lower.contains(key) && k.reply.isNotBlank()
        } ?: return null
        return fill(kw.reply, bot, chatId, firstName, username)
    }

    /** Replaces {name} {username} {chat_id} {bot_name} placeholders. */
    private fun fill(tpl: String, bot: Bot, chatId: String, firstName: String?, username: String?): String =
        tpl
            .replace("{name}", firstName?.takeIf { it.isNotBlank() } ?: "دوست عزیز")
            .replace("{username}", username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "")
            .replace("{chat_id}", chatId)
            .replace("{bot_name}", bot.name.ifBlank { "ربات" })

    private fun buildHelp(cfg: BotAutoReply, bot: Bot): String {
        val sb = StringBuilder()
        sb.append(L10n.str("دستورات ربات {bot_name}:", "Commands of {bot_name}:")).append("\n")
        sb.append("/start — ").append(L10n.str("شروع کار", "Get started")).append("\n")
        sb.append("/help — ").append(L10n.str("همین راهنما", "This help")).append("\n")
        sb.append("/id — ").append(L10n.str("شناسه چت شما", "Your chat id")).append("\n")
        sb.append("/about — ").append(L10n.str("درباره ربات", "About this bot"))
        val custom = cfg.commands.filter { it.command.isNotBlank() }
        if (custom.isNotEmpty()) {
            sb.append("\n\n").append(L10n.str("دستورهای اختصاصی:", "Custom commands:")).append("\n")
            sb.append(custom.joinToString("\n") { "/${it.command.trim().trimStart('/')}" })
        }
        return sb.toString()
    }

    /** Appends the bot reply to the local history so the chat UI mirrors Telegram. */
    private fun appendOutgoing(
        app: Context,
        bot: Bot,
        chatId: Long,
        text: String,
        messageId: Long,
        date: Long
    ) {
        val store = ChatStore.get(app)
        val data = store.load(bot.id)
        val key = chatId.toString()
        val msgs = data.messages.toMutableMap()
        val existing = msgs[key] ?: emptyList()
        if (existing.any { it.id == messageId && it.isMine }) return
        msgs[key] = existing + ChatMsg(
            id = messageId,
            chatId = chatId,
            fromId = bot.telegramId,
            fromName = bot.name,
            text = text.replace("\n", " ").trim(),
            isMine = true,
            date = date
        )
        val threads = data.threads.map {
            if (it.chatId == chatId) {
                it.copy(lastMessage = text.replace("\n", " ").trim().take(80), lastDate = date)
            } else it
        }
        store.save(bot.id, data.copy(threads = threads, messages = msgs))
    }
}
