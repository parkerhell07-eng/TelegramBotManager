package com.telegrambot.manager.data.api

import com.telegrambot.manager.data.engine.BotEngine
import com.telegrambot.manager.data.model.ChatMsg
import com.telegrambot.manager.data.model.ChatThread
import com.telegrambot.manager.data.store.ChatStore

/**
 * Bridges the Telegram getUpdates stream into the local chat history kept by
 * [ChatStore]. Both the chats list and an open conversation call
 * [syncOnce] on a light polling loop; the persisted offset guarantees no
 * message is processed twice.
 */
object ChatSync {

    /**
     * Pulls new updates once and merges them into the local history.
     * Returns null on success (including "nothing new"), otherwise a
     * friendly Persian error message for the UI.
     */
    suspend fun syncOnce(token: String, botId: String, store: ChatStore): String? {
        // While the background engine polls this bot, it is already merging
        // every update into [store]; a second consumer here would only cause
        // Telegram 409 conflicts. Local data refreshes by itself.
        if (BotEngine.isPolling(botId)) return null

        // Cloud-enabled bots are polled by GitHub Actions around the clock; a
        // phone-side getUpdates would 409-fight the cloud worker. In cloud
        // mode the owner inbox (admin chat) is the conversation surface.
        if (botId in com.telegrambot.manager.data.store.CloudStore.enabledIds.value) return null

        val data = store.load(botId)
        val offset = if (data.offset > 0) data.offset else null
        return when (val r = TelegramRepo.getUpdates(token, offset)) {
            is TelegramRepo.ApiResult.Success -> {
                if (r.data.isEmpty()) {
                    null
                } else {
                    ingest(botId, store, r.data)
                    null
                }
            }
            is TelegramRepo.ApiResult.Failure -> when (r.code) {
                409 -> "وب‌هوک ربات فعال است یا دریافت همزمان انجام می‌شود؛ از ابزار «وب‌هوک» آن را حذف کنید یا چند لحظه بعد تلاش کنید."
                else -> r.message
            }
        }
    }

    /**
     * Merges raw updates into the local history (also used by the background
     * engine so chats stay in sync no matter who pulled the updates).
     */
    fun ingest(botId: String, store: ChatStore, updates: List<com.telegrambot.manager.data.model.TgUpdate>) {
        merge(store.load(botId), updates, store, botId)
    }

    private fun merge(
        data: ChatStore.BotChats,
        updates: List<com.telegrambot.manager.data.model.TgUpdate>,
        store: ChatStore,
        botId: String
    ) {
        val threads = data.threads.toMutableList()
        val msgs = data.messages.toMutableMap()
        var maxId = data.offset

        for (u in updates) {
            if (u.updateId >= maxId) maxId = u.updateId + 1
            val m = u.message ?: u.editedMessage ?: u.channelPost ?: continue
            val chat = m.chat ?: continue
            val chatId = chat.id

            val senderName = listOfNotNull(m.from?.firstName, m.from?.lastName)
                .joinToString(" ").trim()
            val sender = senderName.ifEmpty {
                chat.title ?: chat.username?.let { "@$it" } ?: "کاربر"
            }
            val body = (m.text ?: m.caption ?: "پیام رسانه‌ای").replace("\n", " ").trim()

            val key = chatId.toString()
            val existing = msgs[key] ?: emptyList()
            val isNew = existing.none { it.id == m.messageId && !it.isMine }
            if (isNew) {
                msgs[key] = existing + ChatMsg(
                    id = m.messageId,
                    chatId = chatId,
                    fromId = m.from?.id ?: 0,
                    fromName = if (chat.type == "private") sender else sender,
                    text = body,
                    isMine = false,
                    date = m.date
                )
            }

            val title = when {
                !chat.title.isNullOrEmpty() -> chat.title
                senderName.isNotBlank() -> senderName
                !chat.username.isNullOrEmpty() -> "@${chat.username}"
                else -> "چت ${chat.id}"
            }

            val idx = threads.indexOfFirst { it.chatId == chatId }
            if (idx >= 0) {
                val t = threads[idx]
                if (m.date >= t.lastDate) {
                    threads[idx] = t.copy(
                        title = title,
                        username = chat.username,
                        type = chat.type ?: t.type,
                        lastMessage = body.take(80),
                        lastDate = m.date,
                        unread = t.unread + if (isNew) 1 else 0
                    )
                }
            } else {
                threads.add(
                    ChatThread(
                        chatId = chatId,
                        type = chat.type ?: "private",
                        title = title,
                        username = chat.username,
                        lastMessage = body.take(80),
                        lastDate = m.date,
                        unread = if (isNew) 1 else 0
                    )
                )
            }
        }

        store.save(botId, data.copy(threads = threads, messages = msgs, offset = maxId))
    }
}
