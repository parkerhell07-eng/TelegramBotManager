package com.telegrambot.manager.data.api

import com.google.gson.JsonParseException
import com.telegrambot.manager.data.model.TgBotCommand
import com.telegrambot.manager.data.model.TgChatMember
import com.telegrambot.manager.data.model.TgDescriptionResult
import com.telegrambot.manager.data.model.TgMessage
import com.telegrambot.manager.data.model.TgNameResult
import com.telegrambot.manager.data.model.TgPoll
import com.telegrambot.manager.data.model.TgResponse
import com.telegrambot.manager.data.model.TgShortDescriptionResult
import com.telegrambot.manager.data.model.TgUpdate
import com.telegrambot.manager.data.model.TgUser
import com.telegrambot.manager.data.model.TgWebhookInfo
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** Repository: single entry point for every Telegram API call used by the app. */
object TelegramRepo {

    private val api: TelegramApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.telegram.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TelegramApi::class.java)
    }

    sealed class ApiResult<out T> {
        data class Success<out T>(val data: T) : ApiResult<T>()
        data class Failure(val message: String, val code: Int? = null) : ApiResult<Nothing>()
    }

    private fun <T> wrap(resp: TgResponse<T>): ApiResult<T> =
        if (resp.ok && resp.result != null) {
            ApiResult.Success(resp.result)
        } else {
            ApiResult.Failure(
                resp.description?.takeIf { it.isNotBlank() } ?: "خطای ناشناخته از سرور تلگرام",
                resp.errorCode
            )
        }

    /**
     * Runs an API call and converts ANY unexpected exception (no internet,
     * DNS failure, timeout, TLS error, bad JSON…) into [ApiResult.Failure]
     * so the UI shows a friendly message instead of crashing.
     * Cancellation is always re-thrown to keep coroutine cancellation intact.
     */
    private suspend fun <T> safeCall(block: suspend () -> TgResponse<T>): ApiResult<T> =
        try {
            wrap(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Failure(friendlyError(e))
        }

    /** Maps low-level exceptions to clear Persian messages for the user. */
    private fun friendlyError(e: Exception): String = when (e) {
        is UnknownHostException ->
            "اتصال به سرور تلگرام برقرار نشد. اینترنت را بررسی کنید؛ اگر تلگرام فیلتر است، VPN یا پروکسی را روشن کنید و دوباره تلاش کنید."
        is ConnectException ->
            "اتصال به سرور تلگرام رد شد. اینترنت یا فیلترشکن را بررسی کنید و دوباره تلاش کنید."
        is SocketTimeoutException ->
            "پاسخی از سرور تلگرام دریافت نشد (اتمام مهلت). اتصال خود را بررسی کنید و دوباره تلاش کنید."
        is SSLException ->
            "خطای امنیتی در اتصال (SSL). اتصال اینترنت یا فیلترشکن را بررسی کنید."
        is JsonParseException ->
            "پاسخ سرور قابل خواندن نبود. لطفاً دوباره تلاش کنید."
        is IOException ->
            "خطای شبکه هنگام ارتباط با سرور: ${e.message ?: "نامشخص"}"
        else ->
            "خطای غیرمنتظره (${e.javaClass.simpleName}): ${e.message ?: "بدون جزئیات"}"
    }

    private fun String.plainBody(): RequestBody =
        toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())

    /**
     * Builds the absolute Bot API URL for [method]. The token is embedded in
     * the path of an ABSOLUTE https URL, which is safe: scheme confusion
     * ("Malformed URL") only happens for RELATIVE templates like @Path.
     */
    private fun apiUrl(token: String, method: String) =
        "https://api.telegram.org/bot$token/$method"

    suspend fun getMe(token: String): ApiResult<TgUser> =
        safeCall { api.getMe(apiUrl(token, "getMe")) }

    suspend fun sendMessage(
        token: String,
        chatId: String,
        text: String,
        parseMode: String?,
        replyMarkup: String?,
        silent: Boolean?
    ): ApiResult<TgMessage> = safeCall {
        api.sendMessage(
            apiUrl(token, "sendMessage"), chatId, text, parseMode, replyMarkup, silent
        )
    }

    suspend fun sendPhotoUrl(
        token: String,
        chatId: String,
        photo: String,
        caption: String?,
        parseMode: String?
    ): ApiResult<TgMessage> = safeCall {
        api.sendPhotoUrl(apiUrl(token, "sendPhoto"), chatId, photo, caption, parseMode)
    }

    suspend fun sendPhotoFile(
        token: String,
        chatId: String,
        bytes: ByteArray,
        fileName: String,
        caption: String?
    ): ApiResult<TgMessage> = safeCall {
        val fields = mutableMapOf<String, RequestBody>()
        fields["chat_id"] = chatId.plainBody()
        if (!caption.isNullOrBlank()) fields["caption"] = caption.plainBody()
        val part = MultipartBody.Part.createFormData(
            "photo", fileName, bytes.toRequestBody("image/*".toMediaTypeOrNull())
        )
        api.sendPhotoUpload(apiUrl(token, "sendPhoto"), fields, part)
    }

    suspend fun sendDocumentUrl(
        token: String,
        chatId: String,
        document: String,
        caption: String?,
        parseMode: String?
    ): ApiResult<TgMessage> = safeCall {
        api.sendDocumentUrl(apiUrl(token, "sendDocument"), chatId, document, caption, parseMode)
    }

    suspend fun sendDocumentFile(
        token: String,
        chatId: String,
        bytes: ByteArray,
        fileName: String,
        caption: String?
    ): ApiResult<TgMessage> = safeCall {
        val fields = mutableMapOf<String, RequestBody>()
        fields["chat_id"] = chatId.plainBody()
        if (!caption.isNullOrBlank()) fields["caption"] = caption.plainBody()
        val part = MultipartBody.Part.createFormData(
            "document", fileName, bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        )
        api.sendDocumentUpload(apiUrl(token, "sendDocument"), fields, part)
    }

    suspend fun sendLocation(
        token: String,
        chatId: String,
        latitude: Double,
        longitude: Double
    ): ApiResult<TgMessage> = safeCall {
        api.sendLocation(apiUrl(token, "sendLocation"), chatId, latitude, longitude)
    }

    /**
     * Pulls pending updates. [timeout] > 0 enables Telegram long polling:
     * the server holds the request up to [timeout] seconds until an update
     * (or the timeout) arrives — ideal for the background engine because it
     * removes steady-state request storms. OkHttp read timeout is 40s, so
     * keep timeout <= 30.
     */
    suspend fun getUpdates(token: String, offset: Long?, timeout: Int? = null): ApiResult<List<TgUpdate>> =
        safeCall { api.getUpdates(apiUrl(token, "getUpdates"), offset, 100, timeout) }

    suspend fun setWebhook(token: String, url: String, dropPending: Boolean?): ApiResult<Boolean> =
        setWebhook(token, url, dropPending, null, null)

    /**
     * Full webhook setup. [secretToken] makes Telegram echo a shared secret
     * in the X-Telegram-Bot-Api-Secret-Token header so only the webhook receiver
     * can feed the bot; [allowedUpdates] narrows delivery (e.g. messages).
     */
    suspend fun setWebhook(
        token: String,
        url: String,
        dropPending: Boolean?,
        secretToken: String?,
        allowedUpdates: String?
    ): ApiResult<Boolean> =
        safeCall {
            api.setWebhook(apiUrl(token, "setWebhook"), url, dropPending, secretToken, allowedUpdates)
        }

    suspend fun deleteWebhook(token: String, dropPending: Boolean?): ApiResult<Boolean> =
        safeCall { api.deleteWebhook(apiUrl(token, "deleteWebhook"), dropPending) }

    suspend fun getWebhookInfo(token: String): ApiResult<TgWebhookInfo> =
        safeCall { api.getWebhookInfo(apiUrl(token, "getWebhookInfo")) }

    suspend fun setMyCommands(token: String, commandsJson: String): ApiResult<Boolean> =
        safeCall { api.setMyCommands(apiUrl(token, "setMyCommands"), commandsJson) }

    suspend fun getMyCommands(token: String): ApiResult<List<TgBotCommand>> =
        safeCall { api.getMyCommands(apiUrl(token, "getMyCommands")) }

    suspend fun deleteMyCommands(token: String): ApiResult<Boolean> =
        safeCall { api.deleteMyCommands(apiUrl(token, "deleteMyCommands")) }

    suspend fun setMyName(token: String, name: String): ApiResult<Boolean> =
        safeCall { api.setMyName(apiUrl(token, "setMyName"), name) }

    suspend fun getMyName(token: String): ApiResult<TgNameResult> =
        safeCall { api.getMyName(apiUrl(token, "getMyName")) }

    suspend fun setMyDescription(token: String, description: String): ApiResult<Boolean> =
        safeCall { api.setMyDescription(apiUrl(token, "setMyDescription"), description) }

    suspend fun getMyDescription(token: String): ApiResult<TgDescriptionResult> =
        safeCall { api.getMyDescription(apiUrl(token, "getMyDescription")) }

    suspend fun setMyShortDescription(token: String, shortDescription: String): ApiResult<Boolean> =
        safeCall { api.setMyShortDescription(apiUrl(token, "setMyShortDescription"), shortDescription) }

    suspend fun getMyShortDescription(token: String): ApiResult<TgShortDescriptionResult> =
        safeCall { api.getMyShortDescription(apiUrl(token, "getMyShortDescription")) }

    // ── Member management ─────────────────────────────────────────────

    suspend fun banChatMember(
        token: String, chatId: String, userId: Long, revokeMessages: Boolean?
    ): ApiResult<Boolean> = safeCall {
        api.banChatMember(apiUrl(token, "banChatMember"), chatId, userId, revokeMessages)
    }

    suspend fun unbanChatMember(
        token: String, chatId: String, userId: Long, onlyIfBanned: Boolean?
    ): ApiResult<Boolean> = safeCall {
        api.unbanChatMember(apiUrl(token, "unbanChatMember"), chatId, userId, onlyIfBanned)
    }

    suspend fun promoteChatMember(
        token: String,
        chatId: String,
        userId: Long,
        canChangeInfo: Boolean?,
        canDeleteMessages: Boolean?,
        canInviteUsers: Boolean?,
        canRestrictMembers: Boolean?,
        canPinMessages: Boolean?,
        canPromoteMembers: Boolean?
    ): ApiResult<Boolean> = safeCall {
        api.promoteChatMember(
            apiUrl(token, "promoteChatMember"), chatId, userId,
            canChangeInfo, canDeleteMessages, canInviteUsers,
            canRestrictMembers, canPinMessages, canPromoteMembers
        )
    }

    suspend fun getChatAdministrators(token: String, chatId: String): ApiResult<List<TgChatMember>> =
        safeCall { api.getChatAdministrators(apiUrl(token, "getChatAdministrators"), chatId) }

    suspend fun getChatMember(token: String, chatId: String, userId: Long): ApiResult<TgChatMember> =
        safeCall { api.getChatMember(apiUrl(token, "getChatMember"), chatId, userId) }

    suspend fun getChatMemberCount(token: String, chatId: String): ApiResult<Int> =
        safeCall { api.getChatMemberCount(apiUrl(token, "getChatMemberCount"), chatId) }

    suspend fun pinChatMessage(
        token: String, chatId: String, messageId: Long, disableNotification: Boolean?
    ): ApiResult<Boolean> = safeCall {
        api.pinChatMessage(apiUrl(token, "pinChatMessage"), chatId, messageId, disableNotification)
    }

    suspend fun unpinChatMessage(
        token: String, chatId: String, messageId: Long?
    ): ApiResult<Boolean> = safeCall {
        api.unpinChatMessage(apiUrl(token, "unpinChatMessage"), chatId, messageId)
    }

    // ── Polls ─────────────────────────────────────────────────────────

    suspend fun sendPoll(
        token: String,
        chatId: String,
        question: String,
        optionsJson: String,
        isAnonymous: Boolean?,
        type: String?,
        allowsMultipleAnswers: Boolean?,
        correctOptionId: Int?
    ): ApiResult<TgMessage> = safeCall {
        api.sendPoll(
            apiUrl(token, "sendPoll"), chatId, question, optionsJson,
            isAnonymous, type, allowsMultipleAnswers, correctOptionId
        )
    }

    suspend fun stopPoll(token: String, chatId: String, messageId: Long): ApiResult<TgPoll> =
        safeCall { api.stopPoll(apiUrl(token, "stopPoll"), chatId, messageId) }
}
