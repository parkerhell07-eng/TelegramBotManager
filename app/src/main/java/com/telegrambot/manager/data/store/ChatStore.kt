package com.telegrambot.manager.data.store

import android.content.Context
import com.google.gson.Gson
import com.telegrambot.manager.data.model.ChatMsg
import com.telegrambot.manager.data.model.ChatThread
import java.io.File

/**
 * Persists per-bot chat history (threads + messages + polling offset) as a
 * JSON file in the app's internal storage. Chat data is private to the app;
 * no permission is required and nothing leaves the device except through
 * the Telegram Bot API itself.
 */
class ChatStore(context: Context) {

    /** Full chat state for one bot. */
    data class BotChats(
        val threads: List<ChatThread> = emptyList(),
        val messages: Map<String, List<ChatMsg>> = emptyMap(), // key = chatId.toString()
        val offset: Long = 0
    )

    private val gson = Gson()
    private val dir = File(context.filesDir, "chat_data").apply { if (!exists()) mkdirs() }
    private val lock = Any()

    fun load(botId: String): BotChats = synchronized(lock) {
        val f = fileFor(botId)
        if (!f.exists()) return BotChats()
        try {
            gson.fromJson(f.readText(), BotChats::class.java) ?: BotChats()
        } catch (e: Exception) {
            BotChats()
        }
    }

    fun save(botId: String, data: BotChats) = synchronized(lock) {
        try {
            val trimmed = data.copy(
                threads = data.threads.sortedByDescending { it.lastDate }.take(MAX_THREADS),
                messages = data.messages.mapValues { (_, v) ->
                    v.sortedBy { it.date }.takeLast(MAX_PER_CHAT)
                }
            )
            fileFor(botId).writeText(gson.toJson(trimmed))
        } catch (e: Exception) {
            // storage hiccup: keep in-memory state, retry on next save
        }
    }

    /** Marks every unread counter of [chatId] as read. */
    fun markRead(botId: String, chatId: Long) = synchronized(lock) {
        val d = load(botId)
        val updated = d.copy(
            threads = d.threads.map { if (it.chatId == chatId && it.unread != 0) it.copy(unread = 0) else it }
        )
        if (updated != d) save(botId, updated)
    }

    fun clear(botId: String) = synchronized(lock) {
        try { fileFor(botId).delete() } catch (e: Exception) { }
    }

    private fun fileFor(botId: String) = File(dir, "$botId.json")

    companion object {
        private const val MAX_THREADS = 100
        private const val MAX_PER_CHAT = 200

        @Volatile
        private var instance: ChatStore? = null

        fun get(context: Context): ChatStore =
            instance ?: synchronized(this) {
                instance ?: ChatStore(context.applicationContext).also { instance = it }
            }
    }
}
