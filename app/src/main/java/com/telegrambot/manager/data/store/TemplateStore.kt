package com.telegrambot.manager.data.store

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.telegrambot.manager.data.model.MsgTemplate
import java.util.UUID

/**
 * Persists the user's saved message templates (global, shared across bots)
 * as a JSON file in the app's internal storage.
 */
class TemplateStore(context: Context) {

    private val gson = Gson()
    private val file = java.io.File(context.filesDir, "message_templates.json")
    private val lock = Any()

    fun load(): List<MsgTemplate> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        try {
            val type = object : TypeToken<List<MsgTemplate>>() {}.type
            gson.fromJson<List<MsgTemplate>>(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(title: String, body: String) = synchronized(lock) {
        val list = load().toMutableList()
        list.add(
            0,
            MsgTemplate(
                id = UUID.randomUUID().toString(),
                title = title,
                body = body,
                createdAt = System.currentTimeMillis()
            )
        )
        persist(list.take(MAX))
    }

    fun delete(id: String) = synchronized(lock) {
        persist(load().filterNot { it.id == id })
    }

    private fun persist(list: List<MsgTemplate>) {
        try {
            file.writeText(gson.toJson(list))
        } catch (e: Exception) {
            // storage hiccup: next save retries
        }
    }

    companion object {
        private const val MAX = 50

        @Volatile
        private var instance: TemplateStore? = null

        fun get(context: Context): TemplateStore =
            instance ?: synchronized(this) {
                instance ?: TemplateStore(context.applicationContext).also { instance = it }
            }
    }
}
