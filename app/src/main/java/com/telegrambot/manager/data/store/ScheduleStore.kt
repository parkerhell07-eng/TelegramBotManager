package com.telegrambot.manager.data.store

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.telegrambot.manager.data.model.ScheduledMessage
import java.util.UUID

/**
 * Persists the user's scheduled messages (global list, filtered per bot)
 * as a JSON file in the app's internal storage. The WorkManager worker
 * reads pending items from here when it fires and writes the outcome back.
 */
class ScheduleStore(context: Context) {

    private val gson = Gson()
    private val file = java.io.File(context.filesDir, "scheduled_messages.json")
    private val lock = Any()

    fun all(): List<ScheduledMessage> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        try {
            val type = object : TypeToken<List<ScheduledMessage>>() {}.type
            gson.fromJson<List<ScheduledMessage>>(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun forBot(botId: String): List<ScheduledMessage> =
        all().filter { it.botId == botId }.sortedBy { it.scheduledAt }

    fun pendingForBot(botId: String): List<ScheduledMessage> =
        forBot(botId).filter { it.status == ScheduledMessage.STATUS_PENDING }

    fun byId(id: String): ScheduledMessage? = all().firstOrNull { it.id == id }

    /** Adds a new pending message and returns it. */
    fun add(
        botId: String,
        targets: List<String>,
        targetNames: List<String>,
        text: String,
        silent: Boolean,
        scheduledAt: Long,
        repeat: String
    ): ScheduledMessage = synchronized(lock) {
        val msg = ScheduledMessage(
            id = UUID.randomUUID().toString(),
            botId = botId,
            targets = targets,
            targetNames = targetNames,
            text = text,
            silent = silent,
            scheduledAt = scheduledAt,
            repeat = repeat,
            status = ScheduledMessage.STATUS_PENDING,
            createdAt = System.currentTimeMillis()
        )
        persist(listOf(msg) + all().take(MAX - 1))
        msg
    }

    /** Replaces a stored message (used by the worker to write the outcome). */
    fun update(msg: ScheduledMessage) = synchronized(lock) {
        persist(all().map { if (it.id == msg.id) msg else it })
    }

    /** Marks a pending message cancelled and drops its WorkManager job. */
    fun cancel(id: String) = synchronized(lock) {
        persist(all().map {
            if (it.id == id && it.status == ScheduledMessage.STATUS_PENDING) {
                it.copy(status = ScheduledMessage.STATUS_CANCELLED)
            } else it
        })
    }

    fun delete(id: String) = synchronized(lock) {
        persist(all().filterNot { it.id == id })
    }

    /** Called when a bot is removed from the app. */
    fun removeBot(botId: String) = synchronized(lock) {
        persist(all().filterNot { it.botId == botId })
    }

    private fun persist(list: List<ScheduledMessage>) {
        try {
            file.writeText(gson.toJson(list))
        } catch (e: Exception) {
            // storage hiccup: next save retries
        }
    }

    companion object {
        private const val MAX = 200

        @Volatile
        private var instance: ScheduleStore? = null

        fun get(context: Context): ScheduleStore =
            instance ?: synchronized(this) {
                instance ?: ScheduleStore(context.applicationContext).also { instance = it }
            }
    }
}
