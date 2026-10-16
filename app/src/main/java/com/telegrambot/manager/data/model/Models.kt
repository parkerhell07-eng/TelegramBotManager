package com.telegrambot.manager.data.model

import com.google.gson.annotations.SerializedName

/** Generic Telegram Bot API response wrapper. */
data class TgResponse<T>(
    val ok: Boolean = false,
    val result: T? = null,
    val description: String? = null,
    @SerializedName("error_code") val errorCode: Int? = null
)

/** Telegram user / bot identity (getMe result). */
data class TgUser(
    val id: Long = 0,
    @SerializedName("is_bot") val isBot: Boolean = false,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    val username: String? = null,
    @SerializedName("can_join_groups") val canJoinGroups: Boolean? = null,
    @SerializedName("can_read_all_group_messages") val canReadAllGroupMessages: Boolean? = null,
    @SerializedName("supports_inline_queries") val supportsInlineQueries: Boolean? = null
)

data class TgChat(
    val id: Long = 0,
    val type: String? = null,
    val title: String? = null,
    val username: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null
)

data class TgMessage(
    @SerializedName("message_id") val messageId: Long = 0,
    val from: TgUser? = null,
    val chat: TgChat? = null,
    val date: Long = 0,
    val text: String? = null,
    val caption: String? = null
)

data class TgCallbackQuery(
    val id: String? = null,
    val from: TgUser? = null,
    @SerializedName("chat_instance") val chatInstance: String? = null,
    val data: String? = null,
    val message: TgMessage? = null
)

data class TgUpdate(
    @SerializedName("update_id") val updateId: Long = 0,
    val message: TgMessage? = null,
    @SerializedName("edited_message") val editedMessage: TgMessage? = null,
    @SerializedName("channel_post") val channelPost: TgMessage? = null,
    @SerializedName("callback_query") val callbackQuery: TgCallbackQuery? = null
)

data class TgWebhookInfo(
    val url: String? = null,
    @SerializedName("has_custom_certificate") val hasCustomCertificate: Boolean = false,
    @SerializedName("pending_update_count") val pendingUpdateCount: Int = 0,
    @SerializedName("ip_address") val ipAddress: String? = null,
    @SerializedName("last_error_date") val lastErrorDate: Long? = null,
    @SerializedName("last_error_message") val lastErrorMessage: String? = null,
    @SerializedName("max_connections") val maxConnections: Int? = null
)

data class TgBotCommand(
    val command: String = "",
    val description: String = ""
)

data class TgNameResult(val name: String? = null)

data class TgDescriptionResult(val description: String? = null)

data class TgShortDescriptionResult(
    @SerializedName("short_description") val shortDescription: String? = null
)

/** Local entity: a bot saved inside the app. */
data class Bot(
    val id: String = "",
    val token: String = "",
    val name: String = "",
    val username: String = "",
    val telegramId: Long = 0,
    val addedAt: Long = 0
)
