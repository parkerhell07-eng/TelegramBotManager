package com.telegrambot.manager.data.cloud

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import java.util.concurrent.TimeUnit

/**
 * Minimal Cloudflare API client (api.cloudflare.com) used to deploy the
 * auto-answer Worker for a bot.
 *
 * Required token permission: Account → Workers Scripts → Edit. The official
 * token template "Edit Cloudflare Workers" works out of the box.
 */
object CloudflareApi {

    data class CfOut(
        val ok: Boolean,
        val message: String? = null,
        val result: JsonElement? = null,
        val httpCode: Int = 0
    )

    private val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
    private val JS_MODULE = "application/javascript+module".toMediaTypeOrNull()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun call(request: Request): CfOut {
        val resp = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            return CfOut(false, "اتصال به api.cloudflare.com برقرار نشد: ${e.message ?: "نامشخص"}")
        }
        resp.use { r ->
            val bodyStr = try {
                r.body?.string() ?: ""
            } catch (e: Exception) {
                ""
            }
            return try {
                val obj = JsonParser.parseString(bodyStr).asJsonObject
                val success = obj.get("success")?.asBoolean ?: false
                val errMsg = obj.getAsJsonArray("errors")?.takeIf { it.size() > 0 }?.let { arr ->
                    val e0 = arr[0].asJsonObject
                    val msg = e0.get("message")?.asString ?: "خطای کلادفلر"
                    "$msg (کد ${e0.get("code")?.toString() ?: "?"})"
                }
                CfOut(
                    ok = success && r.isSuccessful,
                    message = if (success) null else (errMsg ?: "خطای کلادفلر (HTTP ${r.code})"),
                    result = obj.get("result"),
                    httpCode = r.code
                )
            } catch (e: Exception) {
                CfOut(false, "پاسخ کلادفلر قابل خواندن نبود (HTTP ${r.code})", httpCode = r.code)
            }
        }
    }

    private fun req(url: String, token: String, method: String, body: okhttp3.RequestBody? = null): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "TelegramBotManager/2.1")
            .method(method, body)

    /** Confirms the token is valid and usable. */
    fun verifyToken(token: String): CfOut =
        call(req("https://api.cloudflare.com/client/v4/user/tokens/verify", token, "GET").build())

    /** Returns the first account id visible to the token (via result array). */
    fun firstAccountId(token: String): String? {
        val out = call(req("https://api.cloudflare.com/client/v4/accounts?per_page=5", token, "GET").build())
        if (!out.ok) return null
        val arr = out.result?.asJsonArray ?: return null
        val first = arr.firstOrNull() ?: return null
        return first.asJsonObject?.get("id")?.asString
    }

    /** Reads the account's workers.dev subdomain (e.g. "john-doe"). */
    fun workersSubdomain(token: String, accountId: String): CfOut =
        call(
            req(
                "https://api.cloudflare.com/client/v4/accounts/$accountId/workers/subdomain",
                token, "GET"
            ).build()
        )

    /** Uploads a module worker script (metadata + worker.js multipart PUT). */
    fun uploadScript(token: String, accountId: String, scriptName: String, script: String): CfOut {
        val meta = "{\"main_module\":\"worker.js\",\"compatibility_date\":\"2024-09-23\"}"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("metadata", null, meta.toRequestBody(JSON))
            .addFormDataPart("worker.js", "worker.js", script.toRequestBody(JS_MODULE))
            .build()
        return call(
            req(
                "https://api.cloudflare.com/client/v4/accounts/$accountId/workers/scripts/$scriptName",
                token, "PUT", body
            ).build()
        )
    }

    /** Exposes the worker on https://{script}.{subdomain}.workers.dev */
    fun enableWorkersDev(token: String, accountId: String, scriptName: String): CfOut {
        val payload = "{\"enabled\":true,\"previews_enabled\":false}"
        val post = call(
            req(
                "https://api.cloudflare.com/client/v4/accounts/$accountId/workers/scripts/$scriptName/subdomain",
                token, "POST", payload.toRequestBody(JSON)
            ).build()
        )
        if (post.ok) return post
        // Older API revisions expose this as PUT — fall back once.
        if (post.httpCode == 404 || post.httpCode == 405) {
            return call(
                req(
                    "https://api.cloudflare.com/client/v4/accounts/$accountId/workers/scripts/$scriptName/subdomain",
                    token, "PUT", payload.toRequestBody(JSON)
                ).build()
            )
        }
        return post
    }

    /** Cheap liveness probe: the worker answers GET with 200 "ok". */
    fun ping(url: String): CfOut {
        return try {
            client.newCall(Request.Builder().url(url).header("User-Agent", "TelegramBotManager/2.1").build())
                .execute().use { r ->
                    if (r.isSuccessful) CfOut(true, result = null, httpCode = r.code)
                    else CfOut(false, "کد وضعیت HTTP ${r.code}", httpCode = r.code)
                }
        } catch (e: Exception) {
            CfOut(false, "اتصال به ورکر برقرار نشد: ${e.message ?: "نامشخص"}")
        }
    }
}
