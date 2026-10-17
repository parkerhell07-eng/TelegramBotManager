package com.telegrambot.manager.data.cloud

import android.content.Context
import com.telegrambot.manager.BuildConfig
import com.telegrambot.manager.EngineControl
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.BotAutoReply
import com.telegrambot.manager.data.model.BotCloud
import com.telegrambot.manager.data.store.AutoReplyStore
import com.telegrambot.manager.data.store.CloudStore
import com.telegrambot.manager.data.util.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Orchestrates cloud deployment in both modes:
 *
 *  - [deployShared] — the app's READY cloud ("کلود آماده"): registers the bot
 *    on the shared hub (no Cloudflare account, no token setup for the user),
 *    then setWebhook + command menu + park the phone engine.
 *  - [deploy] — the advanced personal mode: verify Cloudflare token → resolve
 *    account → read workers.dev subdomain → build + upload the Worker →
 *    enable the public route → setWebhook with a secret token → publish the
 *    command menu → park the phone engine.
 *
 * Everything runs on Dispatchers.IO and reports progress strings for the UI.
 */
object CloudDeployer {

    sealed class Result {
        data class Ok(val url: String) : Result()
        data class Err(val message: String) : Result()
    }

    fun newSecret(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        val sb = StringBuilder()
        for (b in bytes) sb.append("%02x".format(b))
        return sb.toString()
    }

    /** Telegram webhook target for either mode. */
    fun webhookUrl(cfg: BotCloud): String =
        if (cfg.isShared) cfg.url.trimEnd('/') + "/hook/" + cfg.route
        else cfg.url.trimEnd('/') + "/hook"

    /**
     * Base URL of the ready hub: in-app override wins, otherwise the URL
     * baked into the build (BuildConfig.HUB_URL).
     */
    fun hubBase(ctx: Context): String {
        val override = CloudStore.get(ctx).creds().hubUrlOverride.trim()
        return HubApi.normalizeBase(if (override.isNotBlank()) override else BuildConfig.HUB_URL)
    }

    // ── Ready cloud (shared hub) — zero setup for the user ──────────────

    /**
     * One-tap deployment on the app's shared hub: register (token encrypted
     * server-side) → setWebhook with the returned secret → command menu →
     * park the phone engine. No Cloudflare account involved.
     */
    suspend fun deployShared(
        ctx: Context,
        bot: Bot,
        adminChatId: Long,
        progress: (String) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        val base = hubBase(ctx)
        val cfgReply = AutoReplyStore.get(ctx).load(bot.id)

        progress(L10n.str("اتصال به کلود آماده…", "Connecting to the ready cloud…"))
        val reg = HubApi.register(
            base, bot.token, adminChatId,
            WorkerBuilder.cfgJson(buildWConfig(bot, cfgReply, adminChatId))
        )
        if (!reg.ok || reg.route.isBlank() || reg.secret.isBlank()) {
            val hint = L10n.str(
                " — اگر سرور اپ مستقر نشده، صاحب اپ باید یک‌بار «کلود آماده» را مستقر کند (cloud/hub/README.md) یا از حالت پیشرفته با حساب کلادفلر خودتان استفاده کنید.",
                " — if the app server is not deployed yet, the app owner must deploy the ready hub once (cloud/hub/README.md), or use the advanced mode with your own Cloudflare account."
            )
            return@withContext Result.Err((reg.message ?: L10n.str("ثبت ربات روی کلود آماده ناموفق بود.", "Bot registration on the ready cloud failed.")) + hint)
        }

        progress(L10n.str("تنظیم وب‌هوک تلگرام…", "Setting the Telegram webhook…"))
        val store = CloudStore.get(ctx)
        val hook = webhookUrl(
            BotCloud(mode = BotCloud.MODE_SHARED, url = base, route = reg.route)
        )
        when (val sw = TelegramRepo.setWebhook(
            bot.token, hook, dropPending = false,
            secretToken = reg.secret, allowedUpdates = "[\"message\"]"
        )) {
            is TelegramRepo.ApiResult.Failure -> return@withContext Result.Err(
                L10n.str("تنظیم وب‌هوک ناموفق بود: ", "Setting the webhook failed: ") + sw.message
            )
            else -> {}
        }

        progress(L10n.str("ثبت منوی دستورها…", "Publishing the command menu…"))
        syncMenu(bot, cfgReply)

        store.save(
            BotCloud(
                botId = bot.id,
                enabled = true,
                mode = BotCloud.MODE_SHARED,
                url = base,
                route = reg.route,
                secret = reg.secret,
                adminChatId = adminChatId,
                deployedAt = System.currentTimeMillis()
            )
        )

        // The phone engine must stop long-polling this bot (webhook is live).
        EngineControl.restart(ctx)

        Result.Ok(hook)
    }

    /** Best-effort removal of a shared-mode bot from the hub (bot deleted). */
    fun unregisterShared(ctx: Context, botId: String) {
        try {
            val cfg = CloudStore.get(ctx).load(botId)
            if (cfg.enabled && cfg.isShared && cfg.route.isNotBlank() && cfg.secret.isNotBlank()) {
                HubApi.unregister(hubBase(ctx), cfg.route, cfg.secret)
            }
        } catch (e: Exception) {
            // best-effort only
        }
    }

    // ── Personal mode: deploy / sync rules ──────────────────────────────

    suspend fun deploy(
        ctx: Context,
        bot: Bot,
        adminChatId: Long,
        progress: (String) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        val store = CloudStore.get(ctx)
        val creds = store.creds()
        if (creds.apiToken.isBlank()) {
            return@withContext Result.Err(
                L10n.str(
                    "ابتدا توکن API کلادفلر را در گام ۱ وارد و ذخیره کنید.",
                    "Enter and save your Cloudflare API token in step 1 first."
                )
            )
        }

        progress(L10n.str("بررسی اعتبار توکن کلادفلر…", "Verifying the Cloudflare token…"))
        val v = CloudflareApi.verifyToken(creds.apiToken)
        if (!v.ok) {
            return@withContext Result.Err(
                L10n.str("توکن کلادفلر نامعتبر است: ", "Cloudflare token is invalid: ") + (v.message ?: "")
            )
        }

        val account = creds.accountId.trim().ifBlank {
            progress(L10n.str("شناسایی خودکار Account ID…", "Auto-detecting the Account ID…"))
            val id = CloudflareApi.firstAccountId(creds.apiToken)
            id ?: return@withContext Result.Err(
                L10n.str(
                    "دریافت خودکار Account ID ناموفق بود؛ آن را از داشبورد کلادفلر کپی و در گام ۱ وارد کنید.",
                    "Could not auto-detect the Account ID; copy it from the Cloudflare dashboard into step 1."
                )
            )
        }

        progress(L10n.str("دریافت زیردامنه workers.dev…", "Reading the workers.dev subdomain…"))
        val sub = CloudflareApi.workersSubdomain(creds.apiToken, account)
        val subName = sub.result?.asJsonObject?.get("subdomain")?.asString
        if (!sub.ok || subName.isNullOrBlank()) {
            return@withContext Result.Err(
                L10n.str("دریافت زیردامنه ناموفق بود: ", "Failed to read the subdomain: ") + (sub.message ?: "")
            )
        }

        val existing = store.load(bot.id)
        val secret = existing.secret.ifBlank { newSecret() }
        val scriptName = "tbm_" + bot.id.replace("-", "_")

        progress(L10n.str("ساخت و آپلود کد Worker…", "Building and uploading the Worker…"))
        val cfgReply = AutoReplyStore.get(ctx).load(bot.id)
        val script = WorkerBuilder.build(buildWConfig(bot, cfgReply, adminChatId), secret, bot.token)
        val up = CloudflareApi.uploadScript(creds.apiToken, account, scriptName, script)
        if (!up.ok) {
            return@withContext Result.Err(
                L10n.str(
                    "آپلود Worker ناموفق بود: ",
                    "Worker upload failed: "
                ) + (up.message ?: "") +
                    L10n.str(
                        " — مطمئن شوید توکن، مجوز «Edit Cloudflare Workers» دارد.",
                        " — make sure the token has the \"Edit Cloudflare Workers\" permission."
                    )
            )
        }

        progress(L10n.str("فعال‌سازی آدرس عمومی workers.dev…", "Enabling the public workers.dev route…"))
        val en = CloudflareApi.enableWorkersDev(creds.apiToken, account, scriptName)
        if (!en.ok) {
            return@withContext Result.Err(
                L10n.str("فعال‌سازی آدرس عمومی ناموفق بود: ", "Enabling the public route failed: ") + (en.message ?: "")
            )
        }

        val url = "https://$scriptName.$subName.workers.dev"

        progress(L10n.str("تنظیم وب‌هوک تلگرام…", "Setting the Telegram webhook…"))
        val sw = TelegramRepo.setWebhook(
            bot.token, webhookUrl(BotCloud(url = url)), dropPending = false,
            secretToken = secret, allowedUpdates = "[\"message\"]"
        )
        if (sw is TelegramRepo.ApiResult.Failure) {
            return@withContext Result.Err(
                L10n.str("تنظیم وب‌هوک ناموفق بود: ", "Setting the webhook failed: ") + sw.message
            )
        }

        progress(L10n.str("ثبت منوی دستورها…", "Publishing the command menu…"))
        syncMenu(bot, cfgReply)

        store.save(
            existing.copy(
                botId = bot.id,
                enabled = true,
                accountId = account,
                scriptName = scriptName,
                url = url,
                secret = secret,
                adminChatId = adminChatId,
                deployedAt = System.currentTimeMillis()
            )
        )

        // The phone engine must stop long-polling this bot (webhook is live).
        EngineControl.restart(ctx)

        Result.Ok(url)
    }

    /** Re-pushes the CURRENT rules to the cloud (after edits in the app). */
    suspend fun syncRules(
        ctx: Context,
        bot: Bot,
        progress: (String) -> Unit
    ): Result {
        val cfg = CloudStore.get(ctx).load(bot.id)
        if (!cfg.enabled || cfg.adminChatId <= 0L) {
            return Result.Err(
                L10n.str(
                    "ربات ابری فعال نیست؛ اول استقرار را انجام دهید.",
                    "Cloud mode is not on; deploy first."
                )
            )
        }
        val cfgReply = AutoReplyStore.get(ctx).load(bot.id)
        val wcfg = buildWConfig(bot, cfgReply, cfg.adminChatId)
        if (cfg.isShared) {
            // Shared hub: a lightweight JSON push — no worker re-upload needed.
            return withContext(Dispatchers.IO) {
                progress(L10n.str("همگام‌سازی قوانین با کلود آماده…", "Syncing rules with the ready cloud…"))
                val up = HubApi.updateRules(hubBase(ctx), cfg.route, cfg.secret, WorkerBuilder.cfgJson(wcfg))
                if (up.ok) Result.Ok(webhookUrl(cfg))
                else Result.Err(
                    L10n.str("به‌روزرسانی قوانین ناموفق بود: ", "Rule update failed: ") + (up.message ?: "")
                )
            }
        }
        return deploy(ctx, bot, cfg.adminChatId, progress)
    }

    // ── Disable ──────────────────────────────────────────────────────────

    /** Turns cloud mode off: drops the webhook so phone polling works again. */
    suspend fun disable(ctx: Context, bot: Bot): String? = withContext(Dispatchers.IO) {
        when (val r = TelegramRepo.deleteWebhook(bot.token, null)) {
            is TelegramRepo.ApiResult.Failure -> return@withContext r.message
            else -> {}
        }
        val store = CloudStore.get(ctx)
        val cfg = store.load(bot.id)
        // Shared mode: wipe the bot's data (encrypted token + rules) off the hub.
        if (cfg.isShared && cfg.route.isNotBlank() && cfg.secret.isNotBlank()) {
            try {
                HubApi.unregister(hubBase(ctx), cfg.route, cfg.secret)
            } catch (e: Exception) {
                // best-effort: hub may be unreachable; local config is cleared anyway
            }
        }
        store.save(cfg.copy(enabled = false))
        EngineControl.restart(ctx)
        null
    }

    /** Re-attaches the Telegram webhook after someone deleted it manually. */
    suspend fun reattachWebhook(ctx: Context, bot: Bot): String? = withContext(Dispatchers.IO) {
        val cfg = CloudStore.get(ctx).load(bot.id)
        if (!cfg.enabled || cfg.url.isBlank() || cfg.secret.isBlank()) {
            return@withContext L10n.str("پیکربندی ابری کامل نیست؛ دوباره مستقر کنید.", "Cloud config is incomplete; deploy again.")
        }
        when (val r = TelegramRepo.setWebhook(
            bot.token, webhookUrl(cfg), dropPending = false,
            secretToken = cfg.secret, allowedUpdates = "[\"message\"]"
        )) {
            is TelegramRepo.ApiResult.Failure -> r.message
            else -> null
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun buildWConfig(bot: Bot, cfg: BotAutoReply, adminChatId: Long): WorkerBuilder.WConfig {
        val commands = cfg.commands
            .map { WorkerBuilder.WRule(it.command.trim().trimStart('/').lowercase(), it.reply.trim()) }
            .filter { it.command.isNotBlank() && it.reply.isNotBlank() }
        val keywords = cfg.keywords
            .map { WorkerBuilder.WKeyword(it.keyword.trim(), it.reply.trim()) }
            .filter { it.keyword.isNotBlank() && it.reply.isNotBlank() }
        return WorkerBuilder.WConfig(
            botName = bot.name.ifBlank { L10n.str("ربات", "Bot") },
            botUsername = bot.username,
            defaultName = L10n.str("دوست عزیز", "dear friend"),
            welcomeEnabled = cfg.welcomeEnabled,
            welcomeText = cfg.welcomeText,
            unknownText = cfg.unknownText,
            helpText = buildHelp(bot, cfg),
            idText = L10n.str(
                "شناسه چت شما:\n{chat_id}\n\nاین عدد را در ابزارهای ارسال به‌عنوان chat_id وارد کنید.",
                "Your chat id:\n{chat_id}\n\nUse this number as chat_id in the sending tools."
            ),
            aboutText = L10n.str(
                "{bot_name}\nمدیریت‌شده با اپ «مدیر ربات تلگرام».",
                "{bot_name}\nManaged by the Telegram Bot Manager app."
            ),
            commands = commands,
            keywords = keywords,
            admin = adminChatId,
            forward = true,
            forwardHeader = L10n.str("پیام جدید برای ربات:", "New message for the bot:"),
            forwardFrom = L10n.str("از", "From"),
            relayPrefix = L10n.str("پاسخ پشتیبانی:\n", "Support reply:\n"),
            hintReply = L10n.str(
                "برای پاسخ به کاربر، روی پیام فورواردشده او ریپلای کنید.",
                "To answer a user, reply to their forwarded message."
            )
        )
    }

    private fun buildHelp(bot: Bot, cfg: BotAutoReply): String {
        val sb = StringBuilder()
        sb.append(L10n.str("دستورات ربات {bot_name}:", "Commands of {bot_name}:")).append("\n")
        sb.append("/start — ").append(L10n.str("شروع کار", "Get started")).append("\n")
        sb.append("/help — ").append(L10n.str("همین راهنما", "This help")).append("\n")
        sb.append("/id — ").append(L10n.str("شناسه چت شما", "Your chat id")).append("\n")
        sb.append("/about — ").append(L10n.str("درباره ربات", "About this bot"))
        val custom = cfg.commands.filter { it.command.isNotBlank() }
        if (custom.isNotEmpty()) {
            sb.append("\n\n").append(L10n.str("دستورهای اختصاصی:", "Custom commands:")).append("\n")
            sb.append(custom.joinToString("\n") { "/" + it.command.trim().trimStart('/') })
        }
        return sb.toString()
    }

    /** Same menu the Auto-responder screen publishes (built-ins + custom). */
    private suspend fun syncMenu(bot: Bot, cfg: BotAutoReply) {
        val arr = JSONArray()
        fun add(cmd: String, desc: String) {
            if (desc.isNotBlank()) arr.put(JSONObject().put("command", cmd).put("description", desc.take(60)))
        }
        add("start", L10n.str("شروع کار", "Get started"))
        add("help", L10n.str("راهنما", "Help"))
        add("id", L10n.str("شناسه چت شما", "Your chat id"))
        add("about", L10n.str("درباره ربات", "About this bot"))
        cfg.commands
            .filter { it.command.isNotBlank() && it.reply.isNotBlank() }
            .forEach {
                add(
                    it.command.trim().trimStart('/').lowercase(),
                    it.reply.trim().lineSequence().firstOrNull().orEmpty()
                )
            }
        if (arr.length() > 0) TelegramRepo.setMyCommands(bot.token, arr.toString())
    }
}
