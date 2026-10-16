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
import retrofit2.http.Url

/**
 * Telegram Bot API endpoints.
 *
 * IMPORTANT: the full URL (which contains the bot token) is passed with @Url
 * instead of being interpolated into a @Path template. A bot token contains
 * a colon ("123456:AAH…"), and when Retrofit substitutes it into a relative
 * @Path the URL resolver misreads "bot123456:AAH…" as a URI scheme,
 * failing with "Malformed URL". Building the absolute URL per call avoids
 * that entirely.
 */
interface TelegramApi {

    @POST
    suspend fun getMe(@Url url: String): TgResponse<TgUser>

    @FormUrlEncoded
    @POST
    suspend fun sendMessage(
        @Url url: String,
        @Field("chat_id") chatId: String,
        @Field("text") text: String,
        @Field("parse_mode") parseMode: String?,
        @Field("reply_markup") replyMarkup: String?,
        @Field("disable_notification") disableNotification: Boolean?
    ): TgResponse<TgMessage>

    @FormUrlEncoded
    @POST
    suspend fun sendPhotoUrl(
        @Url url: String,
        @Field("chat_id") chatId: String,
        @Field("photo") photo: String,
        @Field("caption") caption: String?,
        @Field("parse_mode") parseMode: String?
    ): TgResponse<TgMessage>

    @Multipart
    @POST
    suspend fun sendPhotoUpload(
        @Url url: String,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part photo: MultipartBody.Part
    ): TgResponse<TgMessage>

    @FormUrlEncoded
    @POST
    suspend fun sendDocumentUrl(
        @Url url: String,
        @Field("chat_id") chatId: String,
        @Field("document") document: String,
        @Field("caption") caption: String?,
        @Field("parse_mode") parseMode: String?
    ): TgResponse<TgMessage>

    @Multipart
    @POST
    suspend fun sendDocumentUpload(
        @Url url: String,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part document: MultipartBody.Part
    ): TgResponse<TgMessage>

    @FormUrlEncoded
    @POST
    suspend fun sendLocation(
        @Url url: String,
        @Field("chat_id") chatId: String,
        @Field("latitude") latitude: Double,
        @Field("longitude") longitude: Double
    ): TgResponse<TgMessage>

    @FormUrlEncoded
    @POST
    suspend fun getUpdates(
        @Url url: String,
        @Field("offset") offset: Long?,
        @Field("limit") limit: Int?
    ): TgResponse<List<TgUpdate>>

    @FormUrlEncoded
    @POST
    suspend fun setWebhook(
        @Url url: String,
        @Field("url") urlField: String,
        @Field("drop_pending_updates") dropPendingUpdates: Boolean?
    ): TgResponse<Boolean>

    @FormUrlEncoded
    @POST
    suspend fun deleteWebhook(
        @Url url: String,
        @Field("drop_pending_updates") dropPendingUpdates: Boolean?
    ): TgResponse<Boolean>

    @POST
    suspend fun getWebhookInfo(@Url url: String): TgResponse<TgWebhookInfo>

    @FormUrlEncoded
    @POST
    suspend fun setMyCommands(
        @Url url: String,
        @Field("commands") commands: String
    ): TgResponse<Boolean>

    @POST
    suspend fun getMyCommands(@Url url: String): TgResponse<List<TgBotCommand>>

    @POST
    suspend fun deleteMyCommands(@Url url: String): TgResponse<Boolean>

    @FormUrlEncoded
    @POST
    suspend fun setMyName(
        @Url url: String,
        @Field("name") name: String
    ): TgResponse<Boolean>

    @POST
    suspend fun getMyName(@Url url: String): TgResponse<TgNameResult>

    @FormUrlEncoded
    @POST
    suspend fun setMyDescription(
        @Url url: String,
        @Field("description") description: String
    ): TgResponse<Boolean>

    @POST
    suspend fun getMyDescription(@Url url: String): TgResponse<TgDescriptionResult>

    @FormUrlEncoded
    @POST
    suspend fun setMyShortDescription(
        @Url url: String,
        @Field("short_description") shortDescription: String
    ): TgResponse<Boolean>

    @POST
    suspend fun getMyShortDescription(@Url url: String): TgResponse<TgShortDescriptionResult>
}
