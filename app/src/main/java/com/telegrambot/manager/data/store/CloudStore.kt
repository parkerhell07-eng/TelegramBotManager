package com.telegrambot.manager.data.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.telegrambot.manager.data.model.BotCloud

/**
 * Stores the GitHub Personal Access Token (classic, public_repo + workflow
 * scopes) and each bot's [BotCloud] registration inside Android
 * Keystore-backed encrypted SharedPreferences — same protection level as the
 * bot tokens themselves.
 */
class CloudStore(context: Context) {

    private val prefs: SharedPreferences
    private val gson = Gson()

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "secure_cloud_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── PAT ──────────────────────────────────────────────────────────────

    fun pat(): String = prefs.getString(KEY_PAT, null) ?: ""

    fun savePat(value: String) {
        prefs.edit().putString(KEY_PAT, value.trim()).apply()
    }

    // ── Per-bot registration ─────────────────────────────────────────────

    fun get(botId: String): BotCloud? = allMap()[botId]

    /** Context-free peek (safe: never touches disk on missing entries). */
    fun peek(botId: String): BotCloud? = allMap()[botId]

    fun put(cloud: BotCloud) {
        val map = allMap().toMutableMap()
        map[cloud.botId] = cloud
        prefs.edit().putString(KEY_MAP, gson.toJson(map)).apply()
        refreshRegistry()
    }

    fun removeBot(botId: String) {
        val map = allMap().toMutableMap()
        map.remove(botId)
        prefs.edit().putString(KEY_MAP, gson.toJson(map)).apply()
        refreshRegistry()
    }

    fun replaceAll(map: Map<String, BotCloud>) {
        prefs.edit().putString(KEY_MAP, gson.toJson(map)).apply()
        refreshRegistry()
    }

    private fun refreshRegistry() {
        enabledIds.value = allMap().filterValues { it.enabled }.keys
    }

    fun allMap(): Map<String, BotCloud> {
        val json = prefs.getString(KEY_MAP, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, BotCloud>>() {}.type
            gson.fromJson<Map<String, BotCloud>>(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    companion object {
        private const val KEY_PAT = "github_pat"
        private const val KEY_MAP = "cloud_map"

        /** Context-free mirror of cloud-enabled bot ids (guards in UI/API layers). */
        val enabledIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())

        @Volatile
        private var instance: CloudStore? = null

        fun get(context: Context): CloudStore =
            instance ?: synchronized(this) {
                instance ?: CloudStore(context.applicationContext).also {
                    instance = it
                    it.refreshRegistry()
                }
            }
    }
}
