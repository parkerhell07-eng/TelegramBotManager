package com.telegrambot.manager.data.model

/**
 * Local entity: one conversation with a chat that has talked to the bot.
 * Persisted locally by [com.telegrambot.manager.data.store.ChatStore].
 */
data class ChatThread(
    val chatId: Long = 0,
    val type: String = "private", // private | group | supergroup | channel
    val title: String = "",
    val username: String? = null,
    val lastMessage: String = "",
    val lastDate: Long = 0,       // telegram epoch (seconds)
    val unread: Int = 0           // incoming messages since last open
)

/** Local entity: a single message inside a [ChatThread]. */
data class ChatMsg(
    val id: Long = 0,             // telegram message_id (unique per chat)
    val chatId: Long = 0,
    val fromId: Long = 0,         // sender user id (0 for channel posts)
    val fromName: String = "",
    val text: String = "",
    val isMine: Boolean = false,  // sent by the manager from this app
    val date: Long = 0            // telegram epoch (seconds)
)
