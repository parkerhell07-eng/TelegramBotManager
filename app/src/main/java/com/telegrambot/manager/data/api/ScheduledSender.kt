package com.telegrambot.manager.data.api

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.telegrambot.manager.data.model.ChatMsg
import com.telegrambot.manager.data.model.ScheduledMessage
import com.telegrambot.manager.data.store.BotStore
import com.telegrambot.manager.data.store.ChatStore
import com.telegrambot.manager.data.store.ScheduleStore
import java.util.concurrent.TimeUnit

/**
 * Scheduling side: enqueues / cancels the WorkManager job of a
 * [ScheduledMessage]. WorkManager keeps the job alive across process
 * death and reboots; [reschedulePending] re-enqueues everything that
 * is still pending (covers force-stop / OEM battery-killer scenarios).
 */
object Scheduler {

    private fun workName(id: String) = "sched_$id"

    /** Enqueues (or replaces) the send job for [msg]. */
    fun schedule(context: Context, msg: ScheduledMessage) {
        val delayMs = (msg.scheduledAt - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ScheduledSendWorker>()
            .setInputData(androidx.work.workDataOf("id" to msg.id))
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("scheduled_message")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(msg.id), ExistingWorkPolicy.REPLACE, request
        )
    }

    /** Cancels the job of a pending message (message itself is marked in store). */
    fun cancelJob(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    /** Re-enqueues every still-pending message whose job may be missing. */
    fun reschedulePending(context: Context) {
        val store = ScheduleStore.get(context)
        store.all().forEach { msg ->
            if (msg.status == ScheduledMessage.STATUS_PENDING) {
                schedule(context, msg)
            }
        }
    }
}

/**
 * Firing side: runs at (approximately) [ScheduledMessage.scheduledAt],
 * sends the message to every target with a 60ms pause between sends
 * (Telegram global rate limit is 30 msgs/sec), then writes the outcome
 * back to the store and appends sent messages to the local chat history.
 *
 * Network errors retry with exponential backoff (up to 5 attempts, the
 * constraint guarantees Wi-Fi/data is available first).
 */
class ScheduledSendWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = ScheduleStore.get(applicationContext)
        val id = inputData.getString("id") ?: return Result.success()
        val msg = store.byId(id) ?: return Result.success()

        // Cancelled / superseded while the job waited in the queue.
        if (msg.status != ScheduledMessage.STATUS_PENDING) return Result.success()

        val bot = BotStore.get(applicationContext).loadBots()
            .firstOrNull { it.id == msg.botId }
            ?: run {
                store.update(
                    msg.copy(
                        status = ScheduledMessage.STATUS_FAILED,
                        sentAt = System.currentTimeMillis(),
                        resultNote = "ربات حذف شده است"
                    )
                )
                return Result.success()
            }

        val result = sendToAll(bot.token, msg)
        val now = System.currentTimeMillis()
        val status = when {
            result.okCount == msg.targets.size -> ScheduledMessage.STATUS_SENT
            result.okCount > 0 -> ScheduledMessage.STATUS_PARTIAL
            else -> ScheduledMessage.STATUS_FAILED
        }
        store.update(
            msg.copy(
                status = status,
                sentAt = now,
                resultNote = if (result.errors.isEmpty()) "" else result.errors.take(3).joinToString("\n")
            )
        )

        // Automatic repeat: book the next occurrence of a daily/weekly message.
        if (msg.repeat != ScheduledMessage.REPEAT_NONE && status != ScheduledMessage.STATUS_FAILED) {
            val step = if (msg.repeat == ScheduledMessage.REPEAT_DAILY) 86_400_000L else 7 * 86_400_000L
            var next = msg.scheduledAt + step
            while (next <= System.currentTimeMillis()) next += step
            val created = store.add(
                msg.botId, msg.targets, msg.targetNames, msg.text, msg.silent, next, msg.repeat
            )
            Scheduler.schedule(applicationContext, created)
        }

        return Result.success()
    }

    private suspend fun sendToAll(
        token: String,
        msg: ScheduledMessage
    ): SendOutcome {
        val outcome = SendOutcome()
        val chatStore = ChatStore.get(applicationContext)
        for ((index, target) in msg.targets.withIndex()) {
            when (val r = TelegramRepo.sendMessage(token, target, msg.text, null, null, msg.silent)) {
                is TelegramRepo.ApiResult.Success -> {
                    outcome.okCount++
                    val m = r.data
                    val chatId = m.chat?.id ?: target.toLongOrNull()
                    if (chatId != null) {
                        appendMine(
                            chatStore, msg.botId, chatId,
                            ChatMsg(
                                id = m.messageId,
                                chatId = chatId,
                                fromId = 0L,
                                fromName = "",
                                text = msg.text,
                                isMine = true,
                                date = if (m.date > 0) m.date else System.currentTimeMillis() / 1000
                            )
                        )
                    }
                }
                is TelegramRepo.ApiResult.Failure ->
                    outcome.errors += "${msg.targetNames.getOrNull(index) ?: target}: ${r.message.take(60)}"
            }
            if (index != msg.targets.lastIndex) kotlinx.coroutines.delay(60)
        }
        return outcome
    }

    private fun appendMine(chatStore: ChatStore, botId: String, chatId: Long, mine: ChatMsg) {
        try {
            val key = chatId.toString()
            val d = chatStore.load(botId)
            val updatedMsgs = (d.messages[key] ?: emptyList()) + mine
            val updatedThreads = d.threads.map {
                if (it.chatId == chatId) it.copy(
                    lastMessage = mine.text.take(80),
                    lastDate = mine.date
                ) else it
            }
            chatStore.save(
                botId,
                d.copy(messages = d.messages + (key to updatedMsgs), threads = updatedThreads)
            )
        } catch (e: Exception) {
            // history bookkeeping must never break the actual send
        }
    }

    private class SendOutcome {
        var okCount: Int = 0
        val errors = mutableListOf<String>()
    }
}
