package com.telegrambot.manager.data.model

/**
 * Per-bot cloud-engine registration: the GitHub repo that runs the bot's
 * 24/7 polling loop plus the admin id used by the owner inbox.
 */
data class BotCloud(
    val botId: String = "",
    val repoOwner: String = "",
    val repoName: String = "",
    val adminId: Long = 0,
    val enabled: Boolean = false,
    val createdAt: Long = 0
) {
    val repoFull: String get() = "$repoOwner/$repoName"
}
