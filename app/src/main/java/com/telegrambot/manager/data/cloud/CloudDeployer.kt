package com.telegrambot.manager.data.cloud

import android.content.Context
import com.telegrambot.manager.BotEngineService
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.BotAutoReply
import com.telegrambot.manager.data.model.BotCloud
import com.telegrambot.manager.data.store.AutoReplyStore
import com.telegrambot.manager.data.store.CloudStore
import com.telegrambot.manager.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-tap 24/7 cloud engine on GitHub Actions.
 *
 * enable() = create public repo → upload engine.py + engine.yml + config.json
 * → store the bot token as an encrypted Actions secret → trigger the workflow
 * → wait for the first check-in. After that the bot answers from GitHub's
 * runners around the clock, phone off or on, with zero extra accounts beyond
 * the GitHub login the user already has.
 */
object CloudDeployer {

    data class Step(val id: String, val label: String)

    val STEPS = listOf(
        Step("repo", "ساخت مخزن عمومی"),
        Step("engine", "آپلود موتور"),
        Step("rules", "ثبت قوانین ربات"),
        Step("workflow", "نصب زمان‌بند Actions"),
        Step("secret", "رمزنگاری و ثبت توکن"),
        Step("start", "روشن‌سازی و نخستین اجرا")
    )

    class CloudException(val step: Int, message: String) : Exception(message)

    private fun api(ctx: Context): GitHubApi {
        val pat = CloudStore.get(ctx).pat()
        require(pat.isNotBlank()) { "no-pat" }
        return GitHubApi(pat)
    }

    private fun lang(ctx: Context): String =
        SettingsStore.get(ctx).lang.value.code

    private fun cfg(ctx: Context, bot: Bot): BotAutoReply =
        AutoReplyStore.get(ctx).load(bot.id)

    // ── Enable ───────────────────────────────────────────────────────────

    suspend fun enable(
        ctx: Context,
        bot: Bot,
        onProgress: suspend (Int, String?) -> Unit
    ): BotCloud = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val gh = api(app)
        val store = CloudStore.get(app)

        // Step 0 — public repo (public ⇒ unlimited free Actions minutes).
        onProgress(0, null)
        val suffix = java.util.UUID.randomUUID().toString().substring(0, 6)
        val repoName = GitHubApi.repoSlug(bot.name.ifBlank { "bot" }, suffix)
        val created = gh.createRepo(repoName)
        if (created is GitHubApi.Res.Err) throw CloudException(0, created.message)
        val owner = (created as GitHubApi.Res.Ok).body
            ?.get("owner")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("login")?.asString
            ?: gh.me().second

        // Step 1 — engine.py
        onProgress(1, null)
        val r1 = gh.putFile(
            owner, repoName, "engine.py",
            WorkflowBuilder.enginePy().toByteArray(Charsets.UTF_8), "add engine"
        )
        if (r1 is GitHubApi.Res.Err) throw CloudException(1, r1.message)

        // Step 2 — config.json
        onProgress(2, null)
        val r2 = gh.putFile(
            owner, repoName, "config.json",
            WorkflowBuilder.configJson(bot, cfg(app, bot), 0, lang(app))
                .toByteArray(Charsets.UTF_8), "add rules"
        )
        if (r2 is GitHubApi.Res.Err) throw CloudException(2, r2.message)

        // Step 3 — workflow
        onProgress(3, null)
        val r3 = gh.putFile(
            owner, repoName, ".github/workflows/engine.yml",
            WorkflowBuilder.engineYml().toByteArray(Charsets.UTF_8), "add scheduler"
        )
        if (r3 is GitHubApi.Res.Err) throw CloudException(3, r3.message)

        // Step 4 — token as encrypted Actions secret
        onProgress(4, null)
        val (ok, msg) = gh.putSecret(owner, repoName, "BOT_TOKEN", bot.token)
        if (!ok) throw CloudException(4, msg)

        // Step 5 — dispatch + wait for first check-in (runner boot ≈ 30-90 s)
        onProgress(5, null)
        val (dOk, dMsg) = gh.dispatch(owner, repoName)
        if (!dOk) throw CloudException(5, dMsg)
        var checkin: WorkflowBuilder.Checkin? = null
        var attempt = 0
        while (attempt < 24 && checkin == null) {
            Thread.sleep(5000)
            checkin = WorkflowBuilder.parseCheckin(
                gh.getFile(owner, repoName, "state/checkin.json")
            )
            attempt++
        }
        val ci = checkin
            ?: throw CloudException(5, "بدون check-in — اجرای Actions را در گیت‌هاب ببینید")
        if (ci.me?.username == null && ci.last_error.isNotBlank()) {
            throw CloudException(5, "توکن ربات رد شد: " + ci.last_error)
        }

        val cloud = BotCloud(
            botId = bot.id,
            repoOwner = owner,
            repoName = repoName,
            adminId = store.get(bot.id)?.adminId ?: 0,
            enabled = true,
            createdAt = System.currentTimeMillis()
        )
        store.put(cloud)

        // The phone engine must stop polling this bot (GitHub owns it now).
        BotEngineService.restart(app)
        cloud
    }

    // ── Sync rules (config.json) ─────────────────────────────────────────

    suspend fun syncRules(ctx: Context, bot: Bot): Boolean = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val cloud = CloudStore.get(app).get(bot.id) ?: return@withContext false
        val gh = api(app)
        val content = WorkflowBuilder.configJson(
            bot, cfg(app, bot), cloud.adminId, lang(app)
        ).toByteArray(Charsets.UTF_8)
        val r = gh.putFile(cloud.repoOwner, cloud.repoName, "config.json", content, "sync rules")
        if (r is GitHubApi.Res.Err) return@withContext false
        gh.dispatch(cloud.repoOwner, cloud.repoName).first
    }

    /** Sets the admin id (owner inbox) locally and in the cloud config. */
    suspend fun setAdmin(ctx: Context, bot: Bot, adminId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val app = ctx.applicationContext
            val store = CloudStore.get(app)
            val cur = store.get(bot.id) ?: return@withContext false
            store.put(cur.copy(adminId = adminId))
            syncRules(app, bot)
        }

    // ── Health / liveness ────────────────────────────────────────────────

    suspend fun checkin(ctx: Context, bot: Bot): WorkflowBuilder.Checkin? =
        withContext(Dispatchers.IO) {
            val app = ctx.applicationContext
            val cloud = CloudStore.get(app).peek(bot.id) ?: return@withContext null
            try {
                WorkflowBuilder.parseCheckin(
                    api(app).getFile(cloud.repoOwner, cloud.repoName, "state/checkin.json")
                )
            } catch (e: Exception) {
                null
            }
        }

    suspend fun seenChats(ctx: Context, bot: Bot): List<WorkflowBuilder.Checkin.LastChat> =
        withContext(Dispatchers.IO) {
            val app = ctx.applicationContext
            val cloud = CloudStore.get(app).peek(bot.id) ?: return@withContext emptyList()
            try {
                WorkflowBuilder.parseChats(
                    api(app).getFile(cloud.repoOwner, cloud.repoName, "state/chats.json")
                ).chats
            } catch (e: Exception) {
                emptyList()
            }
        }

    // ── Disable / delete ─────────────────────────────────────────────────

    /** Deletes the GitHub repo and switches the bot back to phone answering. */
    suspend fun disable(ctx: Context, bot: Bot): Boolean = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val store = CloudStore.get(app)
        val cloud = store.get(bot.id)
        var deleted = true
        if (cloud != null) {
            try {
                val r = api(app).deleteRepo(cloud.repoOwner, cloud.repoName)
                deleted = r is GitHubApi.Res.Ok || (r is GitHubApi.Res.Err && r.code == 404)
            } catch (e: Exception) {
                deleted = false
            }
        }
        store.removeBot(bot.id)
        BotEngineService.restart(app)
        deleted
    }

    /** Best-effort cleanup when a bot is deleted from the app. */
    suspend fun cleanupForDeletedBot(ctx: Context, botId: String) {
        withContext(Dispatchers.IO) {
            val app = ctx.applicationContext
            val store = CloudStore.get(app)
            val cloud = store.peek(botId)
            if (cloud != null) {
                try {
                    api(app).deleteRepo(cloud.repoOwner, cloud.repoName)
                } catch (e: Exception) {
                }
                store.removeBot(botId)
            }
        }
    }
}
