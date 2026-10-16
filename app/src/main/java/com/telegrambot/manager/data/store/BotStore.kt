package com.telegrambot.manager.data.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.telegrambot.manager.data.model.Bot

/**
 * Persists the list of bots (including their tokens) using
 * Android Keystore-backed encrypted SharedPreferences.
 */
class BotStore(context: Context) {

    private val prefs: SharedPreferences
    private val gson = Gson()

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "secure_bots_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun loadBots(): List<Bot> {
        val json = prefs.getString(KEY_BOTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Bot>>() {}.type
            gson.fromJson<List<Bot>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveBots(bots: List<Bot>) {
        prefs.edit().putString(KEY_BOTS, gson.toJson(bots)).apply()
    }

    companion object {
        private const val KEY_BOTS = "bots_json"

        @Volatile
        private var instance: BotStore? = null

        fun get(context: Context): BotStore =
            instance ?: synchronized(this) {
                instance ?: BotStore(context.applicationContext).also { instance = it }
            }
    }
}
