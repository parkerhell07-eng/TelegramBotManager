package com.telegrambot.manager.data.model

/** Local entity: a reusable message template saved by the user. */
data class MsgTemplate(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val createdAt: Long = 0
)
