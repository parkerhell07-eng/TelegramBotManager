package com.telegrambot.manager.data.store

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.telegrambot.manager.data.model.BotCloud
import com.telegrambot.manager.data.model.CloudCredentials
import java.io.File

/**
 * Persists per-bot cloud config ([BotCloud]) and the global Cloudflare API
 * credentials as JSON files in internal storage. Files live in the app's
 * private dir and allowBackup is false, so credentials never leave the
 * device except inside the user's own encrypted backup.
 */
class CloudStore(context: Context) {

    private val gson = Gson()
    private val botsFile = File(context.filesDir, "cloud_bots.json")
    private val credsFile = File(context.filesDir, "cloud_creds.json")
    private val lock = Any()

    // ── Per-bot config ───────────────────────────────────────────────────

    fun load(botId: String): BotCloud = synchronized(lock) {
        allMap()[botId] ?: BotCloud(botId = botId)
    }

    fun save(cfg: BotCloud) = synchronized(lock) {
        val map = allMap().toMutableMap()
        map[cfg.botId] = cfg
        try {
            botsFile.writeText(gson.toJson(map))
        } catch (e: Exception) {
            // storage hiccup: retried on next save
        }
    }

    fun removeBot(botId: String) = synchronized(lock) {
        val map = allMap().toMutableMap()
        map.remove(botId)
        try {
            botsFile.writeText(gson.toJson(map))
        } catch (e: Exception) {
        }
    }

    /** Replaces the whole config map — used by backup restore. */
    fun replaceAll(map: Map<String, BotCloud>) = synchronized(lock) {
        try {
            botsFile.writeText(gson.toJson(map))
        } catch (e: Exception) {
        }
    }

    fun allMap(): Map<String, BotCloud> {
        if (!botsFile.exists()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, BotCloud>>() {}.type
            gson.fromJson<Map<String, BotCloud>>(botsFile.readText(), type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ── Global credentials ───────────────────────────────────────────────

    fun creds(): CloudCredentials = synchronized(lock) {
        if (!credsFile.exists()) return CloudCredentials()
        try {
            gson.fromJson(credsFile.readText(), CloudCredentials::class.java) ?: CloudCredentials()
        } catch (e: Exception) {
            CloudCredentials()
        }
    }

    fun saveCreds(c: CloudCredentials) = synchronized(lock) {
        try {
            credsFile.writeText(gson.toJson(c))
        } catch (e: Exception) {
        }
    }

    companion object {
        @Volatile
        private var instance: CloudStore? = null

        fun get(context: Context): CloudStore =
            instance ?: synchronized(this) {
                instance ?: CloudStore(context.applicationContext).also { instance = it }
            }

        /** Null-safe accessor for layers without a Context (e.g. ChatSync). */
        fun peek(): CloudStore? = instance
    }
}
