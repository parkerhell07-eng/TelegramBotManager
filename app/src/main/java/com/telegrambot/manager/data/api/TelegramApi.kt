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
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path

/**
 * Telegram Bot API endpoints.
 * The bot token is injected into the URL path per call, so a single
 * Retrofit service instance can serve every saved bot.
 */
interface TelegramApi {

    @POST("bot{token}/getMe")
    suspend fun getMe(@Path("token") token: String): TgResponse<TgUser>

    @FormUrlEncoded
    @POST("bot{token}/sendMessage")
    suspend fun sendMessage(
        @Path("token") token: String,
        @Field("chat_id") chatId: String,
        @Field("text") text: String,
        @Field("parse_mode") parseMode: String?,
        @Field("reply_markup") replyMarkup: String?,
        @Field("disable_notification") disableNotification: Boolean?
    ): TgResponse<TgMessage>

    @FormUrlEncoded
    @POST("bot{token}/sendPhoto")
    suspend fun sendPhotoUrl(
        @Path("token") token: String,
        @Field("chat_id") chatId: String,
        @Field("photo") photo: String,
        @Field("caption") caption: String?,
        @Field("parse_mode") parseMode: String?
    ): TgResponse<TgMessage>

    @Multipart
    @POST("bot{token}/sendPhoto")
    suspend fun sendPhotoUpload(
        @Path("token") token: String,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part photo: MultipartBody.Part
    ): TgResponse<TgMessage>

    @FormUrlEncoded
    @POST("bot{token}/sendDocument")
    suspend fun sendDocumentUrl(
        @Path("token") token: String,
        @Field("chat_id") chatId: String,
        @Field("document") document: String,
        @Field("caption") caption: String?,
        @Field("parse_mode") parseMode: String?
    ): TgResponse<TgMessage>

    @Multipart
    @POST("bot{token}/sendDocument")
    suspend fun sendDocumentUpload(
        @Path("token") token: String,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part document: MultipartBody.Part
    ): TgResponse<TgMessage>

    @FormUrlEncoded
    @POST("bot{token}/sendLocation")
    suspend fun sendLocation(
        @Path("token") token: String,
        @Field("chat_id") chatId: String,
        @Field("latitude") latitude: Double,
        @Field("longitude") longitude: Double
    ): TgResponse<TgMessage>

    @FormUrlEncoded
    @POST("bot{token}/getUpdates")
    suspend fun getUpdates(
        @Path("token") token: String,
        @Field("offset") offset: Long?,
        @Field("limit") limit: Int?
    ): TgResponse<List<TgUpdate>>

    @FormUrlEncoded
    @POST("bot{token}/setWebhook")
    suspend fun setWebhook(
        @Path("token") token: String,
        @Field("url") url: String,
        @Field("drop_pending_updates") dropPendingUpdates: Boolean?
    ): TgResponse<Boolean>

    @FormUrlEncoded
    @POST("bot{token}/deleteWebhook")
    suspend fun deleteWebhook(
        @Path("token") token: String,
        @Field("drop_pending_updates") dropPendingUpdates: Boolean?
    ): TgResponse<Boolean>

    @POST("bot{token}/getWebhookInfo")
    suspend fun getWebhookInfo(@Path("token") token: String): TgResponse<TgWebhookInfo>

    @FormUrlEncoded
    @POST("bot{token}/setMyCommands")
    suspend fun setMyCommands(
        @Path("token") token: String,
        @Field("commands") commands: String
    ): TgResponse<Boolean>

    @POST("bot{token}/getMyCommands")
    suspend fun getMyCommands(@Path("token") token: String): TgResponse<List<TgBotCommand>>

    @POST("bot{token}/deleteMyCommands")
    suspend fun deleteMyCommands(@Path("token") token: String): TgResponse<Boolean>

    @FormUrlEncoded
    @POST("bot{token}/setMyName")
    suspend fun setMyName(
        @Path("token") token: String,
        @Field("name") name: String
    ): TgResponse<Boolean>

    @POST("bot{token}/getMyName")
    suspend fun getMyName(@Path("token") token: String): TgResponse<TgNameResult>

    @FormUrlEncoded
    @POST("bot{token}/setMyDescription")
    suspend fun setMyDescription(
        @Path("token") token: String,
        @Field("description") description: String
    ): TgResponse<Boolean>

    @POST("bot{token}/getMyDescription")
    suspend fun getMyDescription(@Path("token") token: String): TgResponse<TgDescriptionResult>

    @FormUrlEncoded
    @POST("bot{token}/setMyShortDescription")
    suspend fun setMyShortDescription(
        @Path("token") token: String,
        @Field("short_description") shortDescription: String
    ): TgResponse<Boolean>

    @POST("bot{token}/getMyShortDescription")
    suspend fun getMyShortDescription(@Path("token") token: String): TgResponse<TgShortDescriptionResult>
}
