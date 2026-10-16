package com.telegrambot.manager.data.api

import com.telegrambot.manager.data.model.TgBotCommand
import com.telegrambot.manager.data.model.TgDescriptionResult
import com.telegrambot.manager.data.model.TgMessage
import com.telegrambot.manager.data.model.TgNameResult
import com.telegrambot.manager.data.model.TgResponse
import com.telegrambot.manager.data.model.TgShortDescriptionResult
import com.telegrambot.manager.data.model.TgUpdate
import com.telegrambot.manager.data.model.TgUser
import com.telegrambot.manager.data.model.TgWebhookInfo
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

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

    private fun String.plainBody(): RequestBody =
        toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())

    suspend fun getMe(token: String): ApiResult<TgUser> = wrap(api.getMe(token))

    suspend fun sendMessage(
        token: String,
        chatId: String,
        text: String,
        parseMode: String?,
        replyMarkup: String?,
        silent: Boolean?
    ): ApiResult<TgMessage> = wrap(api.sendMessage(token, chatId, text, parseMode, replyMarkup, silent))

    suspend fun sendPhotoUrl(
        token: String,
        chatId: String,
        photo: String,
        caption: String?,
        parseMode: String?
    ): ApiResult<TgMessage> = wrap(api.sendPhotoUrl(token, chatId, photo, caption, parseMode))

    suspend fun sendPhotoFile(
        token: String,
        chatId: String,
        bytes: ByteArray,
        fileName: String,
        caption: String?
    ): ApiResult<TgMessage> {
        val fields = mutableMapOf<String, RequestBody>()
        fields["chat_id"] = chatId.plainBody()
        if (!caption.isNullOrBlank()) fields["caption"] = caption.plainBody()
        val part = MultipartBody.Part.createFormData(
            "photo", fileName, bytes.toRequestBody("image/*".toMediaTypeOrNull())
        )
        return wrap(api.sendPhotoUpload(token, fields, part))
    }

    suspend fun sendDocumentUrl(
        token: String,
        chatId: String,
        document: String,
        caption: String?,
        parseMode: String?
    ): ApiResult<TgMessage> = wrap(api.sendDocumentUrl(token, chatId, document, caption, parseMode))

    suspend fun sendDocumentFile(
        token: String,
        chatId: String,
        bytes: ByteArray,
        fileName: String,
        caption: String?
    ): ApiResult<TgMessage> {
        val fields = mutableMapOf<String, RequestBody>()
        fields["chat_id"] = chatId.plainBody()
        if (!caption.isNullOrBlank()) fields["caption"] = caption.plainBody()
        val part = MultipartBody.Part.createFormData(
            "document", fileName, bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        )
        return wrap(api.sendDocumentUpload(token, fields, part))
    }

    suspend fun sendLocation(
        token: String,
        chatId: String,
        latitude: Double,
        longitude: Double
    ): ApiResult<TgMessage> = wrap(api.sendLocation(token, chatId, latitude, longitude))

    suspend fun getUpdates(token: String, offset: Long?): ApiResult<List<TgUpdate>> =
        wrap(api.getUpdates(token, offset, 100))

    suspend fun setWebhook(token: String, url: String, dropPending: Boolean?): ApiResult<Boolean> =
        wrap(api.setWebhook(token, url, dropPending))

    suspend fun deleteWebhook(token: String, dropPending: Boolean?): ApiResult<Boolean> =
        wrap(api.deleteWebhook(token, dropPending))

    suspend fun getWebhookInfo(token: String): ApiResult<TgWebhookInfo> =
        wrap(api.getWebhookInfo(token))

    suspend fun setMyCommands(token: String, commandsJson: String): ApiResult<Boolean> =
        wrap(api.setMyCommands(token, commandsJson))

    suspend fun getMyCommands(token: String): ApiResult<List<TgBotCommand>> =
        wrap(api.getMyCommands(token))

    suspend fun deleteMyCommands(token: String): ApiResult<Boolean> =
        wrap(api.deleteMyCommands(token))

    suspend fun setMyName(token: String, name: String): ApiResult<Boolean> =
        wrap(api.setMyName(token, name))

    suspend fun getMyName(token: String): ApiResult<TgNameResult> =
        wrap(api.getMyName(token))

    suspend fun setMyDescription(token: String, description: String): ApiResult<Boolean> =
        wrap(api.setMyDescription(token, description))

    suspend fun getMyDescription(token: String): ApiResult<TgDescriptionResult> =
        wrap(api.getMyDescription(token))

    suspend fun setMyShortDescription(token: String, shortDescription: String): ApiResult<Boolean> =
        wrap(api.setMyShortDescription(token, shortDescription))

    suspend fun getMyShortDescription(token: String): ApiResult<TgShortDescriptionResult> =
        wrap(api.getMyShortDescription(token))
}
