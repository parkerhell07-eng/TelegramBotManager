package com.telegrambot.manager.data.store

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.telegrambot.manager.data.model.BotAutoReply
import java.io.File

/**
 * Persists per-bot auto-reply configuration ([BotAutoReply]) and the
 * background engine's per-bot getUpdates offset, as JSON files in internal
 * storage. The offset is persisted so a service restart never re-processes
 * (and never double-replies to) old messages.
 */
class AutoReplyStore(context: Context) {

    private val gson = Gson()
    private val cfgFile = File(context.filesDir, "auto_reply.json")
    private val offsetFile = File(context.filesDir, "engine_offsets.json")
    private val lock = Any()

    // ── Config ───────────────────────────────────────────────────────────

    fun load(botId: String): BotAutoReply = synchronized(lock) {
        allMap()[botId] ?: BotAutoReply(botId = botId)
    }

    fun save(cfg: BotAutoReply) = synchronized(lock) {
        val map = allMap().toMutableMap()
        map[cfg.botId] = cfg
        try {
            cfgFile.writeText(gson.toJson(map))
        } catch (e: Exception) {
            // storage hiccup: retried on next save
        }
    }

    fun removeBot(botId: String) = synchronized(lock) {
        val map = allMap().toMutableMap()
        map.remove(botId)
        try {
            cfgFile.writeText(gson.toJson(map))
        } catch (e: Exception) {
        }
        val off = offsetsMap().toMutableMap()
        off.remove(botId)
        try {
            offsetFile.writeText(gson.toJson(off))
        } catch (e: Exception) {
        }
    }

    /** Every bot id that has auto-reply switched ON (used by the service). */
    fun enabledBotIds(): Set<String> = synchronized(lock) {
        allMap().filter { it.value.enabled }.keys
    }

    /** Replaces the whole config map — used by backup restore. */
    fun replaceAll(map: Map<String, BotAutoReply>) = synchronized(lock) {
        try {
            cfgFile.writeText(gson.toJson(map))
        } catch (e: Exception) {
        }
    }

    fun allMap(): Map<String, BotAutoReply> {
        if (!cfgFile.exists()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, BotAutoReply>>() {}.type
            gson.fromJson<Map<String, BotAutoReply>>(cfgFile.readText(), type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ── Engine offsets ───────────────────────────────────────────────────

    fun offset(botId: String): Long = synchronized(lock) { offsetsMap()[botId] ?: 0L }

    fun saveOffset(botId: String, offset: Long) = synchronized(lock) {
        val map = offsetsMap().toMutableMap()
        map[botId] = offset
        try {
            offsetFile.writeText(gson.toJson(map))
        } catch (e: Exception) {
        }
    }

    private fun offsetsMap(): Map<String, Long> {
        if (!offsetFile.exists()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            gson.fromJson<Map<String, Long>>(offsetFile.readText(), type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    companion object {
        @Volatile
        private var instance: AutoReplyStore? = null

        fun get(context: Context): AutoReplyStore =
            instance ?: synchronized(this) {
                instance ?: AutoReplyStore(context.applicationContext).also { instance = it }
            }
    }
}
