package com.telegrambot.manager.data.backup

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.BotAutoReply
import com.telegrambot.manager.data.model.MsgTemplate
import com.telegrambot.manager.data.model.ScheduledMessage
import com.telegrambot.manager.data.store.AutoReplyStore
import com.telegrambot.manager.data.store.BotStore
import com.telegrambot.manager.data.store.ChatStore
import com.telegrambot.manager.data.store.CloudStore
import com.telegrambot.manager.data.store.ScheduleStore
import com.telegrambot.manager.data.store.SettingsStore
import com.telegrambot.manager.data.store.TemplateStore
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted, single-file backup of EVERYTHING the app stores locally:
 * bots + tokens, auto-reply rules, scheduled messages, templates, chat
 * history and appearance settings.
 *
 * File layout (raw bytes):
 *   magic "TBM2" (4) | format version (1) | salt (16) | iv (12) |
 *   AES-256-GCM ciphertext (JSON payload)
 *
 * The key is derived from the user's passphrase with PBKDF2-HMAC-SHA256
 * (150k iterations). Without the passphrase the blob is cryptographically
 * useless — there is NO recovery, and tokens ARE inside, so the passphrase
 * must be strong. Transport happens through the system file picker (SAF);
 * no storage permission is required.
 */
object BackupManager {

    private val MAGIC = "TBM2".toByteArray(Charsets.US_ASCII)
    private const val FORMAT = 1
    private const val ITERATIONS = 150_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12

    private val gson = Gson()

    // ── Export ───────────────────────────────────────────────────────────

    /** Builds and encrypts the backup; returns raw bytes to be written by SAF. */
    fun export(ctx: Context, passphrase: CharArray): ByteArray {
        val payload = JsonObject()
        payload.addProperty("app", "TelegramBotManager")
        payload.addProperty("format", FORMAT)
        payload.addProperty("exportedAt", System.currentTimeMillis())

        payload.add("bots", gson.toJsonTree(BotStore.get(ctx).loadBots()))
        payload.add("autoReply", gson.toJsonTree(AutoReplyStore.get(ctx).allMap()))
        payload.add("schedules", gson.toJsonTree(ScheduleStore.get(ctx).all()))
        payload.add("templates", gson.toJsonTree(TemplateStore.get(ctx).load()))

        // Cloud registrations + GitHub PAT (both sensitive; the whole payload
        // is AES-encrypted with the user passphrase, so they stay sealed).
        val cloudStore = CloudStore.get(ctx)
        val cloud = JsonObject()
        cloud.addProperty("pat", cloudStore.pat())
        cloud.add("bots", gson.toJsonTree(cloudStore.allMap()))
        payload.add("cloud", cloud)

        val chats = JsonObject()
        val chatStore = ChatStore.get(ctx)
        BotStore.get(ctx).loadBots().forEach { b ->
            chats.add(b.id, gson.toJsonTree(chatStore.load(b.id)))
        }
        payload.add("chats", chats)

        val s = SettingsStore.get(ctx)
        payload.addProperty("theme", s.themeMode().ordinal)
        payload.addProperty("lang", s.lang.value.code)

        return encrypt(payload.toString().toByteArray(Charsets.UTF_8), passphrase)
    }

    // ── Import ───────────────────────────────────────────────────────────

    /**
     * Decrypts [bytes] and REPLACES all local data with the backup content.
     * Returns null on success, otherwise a Persian error message.
     */
    fun import(ctx: Context, bytes: ByteArray, passphrase: CharArray): String? {
        val plain = try {
            decrypt(bytes, passphrase)
        } catch (e: javax.crypto.AEADBadTagException) {
            return "رمز عبور اشتباه است یا فایل خراب شده (اعتبارسنجی رمزنگاری رد شد)."
        } catch (e: Exception) {
            return "فایل پشتیبان قابل خواندن نیست: ${e.message ?: "نامشخص"}"
        }

        return try {
            val root = JSONObject(plain.toString(Charsets.UTF_8))
            if (root.optString("app") != "TelegramBotManager") {
                return "این فایل یک پشتیبان معتبر این برنامه نیست."
            }

            // Bots (tokens included) — the heart of the restore.
            val bots = gson.fromJson<List<Bot>>(
                root.getJSONArray("bots").toString(),
                object : TypeToken<List<Bot>>() {}.type
            ) ?: emptyList()
            BotStore.get(ctx).saveBots(bots)

            // Auto-reply configuration.
            val autoReply = gson.fromJson<Map<String, BotAutoReply>>(
                root.optJSONObject("autoReply")?.toString() ?: "{}",
                object : TypeToken<Map<String, BotAutoReply>>() {}.type
            ) ?: emptyMap()
            AutoReplyStore.get(ctx).replaceAll(autoReply)

            // Scheduled messages.
            val schedules = gson.fromJson<List<ScheduledMessage>>(
                root.optJSONArray("schedules")?.toString() ?: "[]",
                object : TypeToken<List<ScheduledMessage>>() {}.type
            ) ?: emptyList()
            ScheduleStore.get(ctx).replaceAll(schedules)

            // Templates.
            val templates = gson.fromJson<List<MsgTemplate>>(
                root.optJSONArray("templates")?.toString() ?: "[]",
                object : TypeToken<List<MsgTemplate>>() {}.type
            ) ?: emptyList()
            TemplateStore.get(ctx).replaceAll(templates)

            // Cloud registrations + PAT (v2.4.0+; optional for old backups).
            root.optJSONObject("cloud")?.let { cloud ->
                val pat = cloud.optString("pat", "")
                if (pat.isNotBlank()) CloudStore.get(ctx).savePat(pat)
                val cloudMap = gson.fromJson<Map<String, com.telegrambot.manager.data.model.BotCloud>>(
                    cloud.optJSONObject("bots")?.toString() ?: "{}",
                    object : TypeToken<Map<String, com.telegrambot.manager.data.model.BotCloud>>() {}.type
                ) ?: emptyMap()
                CloudStore.get(ctx).replaceAll(cloudMap)
            }

            // Chat history per bot.
            val chatStore = ChatStore.get(ctx)
            root.optJSONObject("chats")?.let { chats ->
                val keys = chats.keys()
                while (keys.hasNext()) {
                    val botId = keys.next()
                    val data = gson.fromJson(
                        chats.getJSONObject(botId).toString(),
                        ChatStore.BotChats::class.java
                    )
                    if (data != null) chatStore.save(botId, data)
                }
            }

            // Appearance.
            val settings = SettingsStore.get(ctx)
            val themeOrd = root.optInt("theme", settings.themeMode().ordinal)
            settings.setTheme(
                com.telegrambot.manager.data.store.ThemeMode.entries.getOrElse(themeOrd) { com.telegrambot.manager.data.store.ThemeMode.SYSTEM }
            )
            val lang = root.optString("lang", "fa")
            settings.setLang(if (lang == "en") com.telegrambot.manager.data.util.L10n.Lang.EN else com.telegrambot.manager.data.util.L10n.Lang.FA)

            null
        } catch (e: Exception) {
            "بازیابی ناتمام ماند (ساختار فایل نامعتبر): ${e.message ?: "نامشخص"}"
        }
    }

    // ── Crypto primitives ────────────────────────────────────────────────

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain)
        return ByteArrayOutputStream(MAGIC.size + 1 + salt.size + iv.size + ct.size).use { out ->
            out.write(MAGIC)
            out.write(FORMAT)
            out.write(salt)
            out.write(iv)
            out.write(ct)
            out.toByteArray()
        }
    }

    private fun decrypt(bytes: ByteArray, passphrase: CharArray): ByteArray {
        val head = 4 + 1 + SALT_LEN + IV_LEN
        require(bytes.size > head) { "فایل کوتاه‌تر از حداقل ممکن است" }
        require(bytes.copyOfRange(0, 4).contentEquals(MAGIC)) { "امضای فایل ناشناخته است" }
        val salt = bytes.copyOfRange(5, 5 + SALT_LEN)
        val iv = bytes.copyOfRange(5 + SALT_LEN, head)
        val ct = bytes.copyOfRange(head, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }
}
