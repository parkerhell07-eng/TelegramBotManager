package com.telegrambot.manager.data.cloud

import com.google.gson.JsonParser
import com.telegrambot.manager.data.util.L10n
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for the app's ready-made cloud hub ("کلود آماده") — a single
 * Cloudflare Worker shared by every app user, deployed once by the app owner
 * (see cloud/hub/README.md in the repo).
 *
 * The hub eliminates ALL manual setup for end users:
 *   register(bot token + rules) → webhook URL + secret → setWebhook → done.
 * No Cloudflare account, no API token, no per-bot worker upload.
 *
 * Endpoints (hub base URL, e.g. https://tbm-hub.example.workers.dev):
 *   POST /v1/register          {token, admin, cfg}            → route+secret
 *   POST /v1/rules/{route}     {cfg} + X-TBM-Secret header    → update rules
 *   POST /v1/unregister/{route}      + X-TBM-Secret header    → wipe data
 *   POST /hook/{route}         (called by Telegram, secret header)
 *   GET  /                     → health JSON
 *
 * The hub stores the bot token AES-GCM encrypted (key lives in a Worker
 * secret, never in KV plaintext) and enforces a per-bot daily reply cap so
 * one noisy bot can not exhaust the shared free quota.
 */
object HubApi {

    data class HubOut(
        val ok: Boolean,
        val message: String? = null,
        val route: String = "",
        val secret: String = "",
        val url: String = "",
        val botUsername: String = ""
    )

    private val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private const val UA = "TelegramBotManager/2.2"

    /** Normalizes the base URL: no trailing slash, https enforced. */
    fun normalizeBase(raw: String): String {
        var b = raw.trim().trimEnd('/')
        if (b.isNotEmpty() && !b.startsWith("https://")) {
            if (b.startsWith("http://")) b = b.replaceFirst("http://", "https://")
            else b = "https://$b"
        }
        return b
    }

    private fun post(url: String, body: String, secret: String? = null): HubOut {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
        if (!secret.isNullOrBlank()) builder.header("X-TBM-Secret", secret)
        val resp = try {
            client.newCall(builder.build()).execute()
        } catch (e: Exception) {
            return HubOut(
                false,
                L10n.str(
                    "اتصال به کلود آماده برقرار نشد: ${e.message ?: "نامشخص"}",
                    "Could not reach the ready cloud: ${e.message ?: "unknown"}"
                )
            )
        }
        resp.use { r ->
            val text = try {
                r.body?.string() ?: ""
            } catch (e: Exception) {
                ""
            }
            val obj = try {
                JsonParser.parseString(text).asJsonObject
            } catch (e: Exception) {
                return HubOut(
                    false,
                    L10n.str(
                        "پاسخ کلود آماده قابل خواندن نبود (HTTP ${r.code})",
                        "Ready cloud response was unreadable (HTTP ${r.code})"
                    )
                )
            }
            val ok = obj.has("ok") && obj.get("ok").isJsonPrimitive && obj.get("ok").asBoolean
            if (ok && r.isSuccessful) {
                return HubOut(
                    ok = true,
                    route = obj.str("route"),
                    secret = obj.str("secret"),
                    url = obj.str("url"),
                    botUsername = obj.path("bot")?.takeIf { it.isJsonObject }?.asJsonObject?.str("username") ?: ""
                )
            }
            val err = obj.str("error").ifBlank {
                L10n.str("خطای کلود آماده (HTTP ${r.code})", "Ready cloud error (HTTP ${r.code})")
            }
            return HubOut(false, err)
        }
    }

    private fun com.google.gson.JsonElement.str(key: String): String =
        (this.asJsonObject.get(key) as? com.google.gson.JsonPrimitive)?.asString ?: ""

    /**
     * Registers the bot on the hub. The hub validates the token against
     * Telegram getMe (possession of the token proves control of the bot),
     * generates the route + webhook secret and stores the token encrypted.
     */
    fun register(base: String, botToken: String, adminChatId: Long, cfgJson: String): HubOut {
        val payload = JSONObject()
            .put("token", botToken)
            .put("admin", adminChatId)
            .put("cfg", JSONObject(cfgJson))
        return post(normalizeBase(base) + "/v1/register", payload.toString())
    }

    /** Pushes fresh rules after the user edits the auto-responder. */
    fun updateRules(base: String, route: String, secret: String, cfgJson: String): HubOut {
        val payload = JSONObject().put("cfg", JSONObject(cfgJson))
        return post(normalizeBase(base) + "/v1/rules/" + route, payload.toString(), secret)
    }

    /** Removes the bot (token + rules) from the hub. Best-effort. */
    fun unregister(base: String, route: String, secret: String): HubOut =
        post(normalizeBase(base) + "/v1/unregister/" + route, "{}", secret)

    /** Cheap liveness probe: the hub answers GET with 200. */
    fun ping(base: String): CloudflareApi.CfOut {
        return try {
            client.newCall(
                Request.Builder()
                    .url(normalizeBase(base) + "/")
                    .header("User-Agent", UA)
                    .build()
            ).execute().use { r ->
                if (r.isSuccessful) CloudflareApi.CfOut(true, httpCode = r.code)
                else CloudflareApi.CfOut(false, "HTTP ${r.code}", httpCode = r.code)
            }
        } catch (e: Exception) {
            CloudflareApi.CfOut(
                false,
                L10n.str(
                    "اتصال به کلود آماده برقرار نشد: ${e.message ?: "نامشخص"}",
                    "Could not reach the ready cloud: ${e.message ?: "unknown"}"
                )
            )
        }
    }
}
