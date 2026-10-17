package com.telegrambot.manager.data.model

/**
 * Local entity: a message scheduled by the user to be sent later through
 * the bot. Persisted by [com.telegrambot.manager.data.store.ScheduleStore]
 * and fired by [com.telegrambot.manager.data.api.ScheduledSendWorker]
 * (WorkManager) at [scheduledAt] (epoch millis).
 *
 * Sending itself is retried by WorkManager with backoff when the device is
 * offline; the outcome of the final attempt is written back into [status].
 */
data class ScheduledMessage(
    val id: String = "",
    val botId: String = "",
    /** Recipient chat ids (numeric chat_id or @channelusername). */
    val targets: List<String> = emptyList(),
    /** Human-readable names for the targets (parallel to [targets]). */
    val targetNames: List<String> = emptyList(),
    val text: String = "",
    val silent: Boolean = false,
    /** When the message should fire (epoch millis, local device time). */
    val scheduledAt: Long = 0,
    /** none | daily | weekly — automatic rescheduling after a successful run. */
    val repeat: String = REPEAT_NONE,
    /** pending | sent | partial | failed | cancelled */
    val status: String = STATUS_PENDING,
    /** When the run finished (epoch millis, 0 = not finished yet). */
    val sentAt: Long = 0,
    /** "3/5 ارسال شد" or the error text for failed runs. */
    val resultNote: String = "",
    val createdAt: Long = 0
) {
    companion object {
        const val REPEAT_NONE = "none"
        const val REPEAT_DAILY = "daily"
        const val REPEAT_WEEKLY = "weekly"

        const val STATUS_PENDING = "pending"
        const val STATUS_SENT = "sent"
        const val STATUS_PARTIAL = "partial"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"
    }
}
