package com.telegrambot.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.TgUpdate
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.EmptyHint
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.ui.components.copyToClipboard
import com.telegrambot.manager.vm.BotVmFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UpdatesViewModel(private val bot: Bot) : ViewModel() {
    var updates by mutableStateOf(listOf<TgUpdate>())
        private set
    var polling by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var pollCount by mutableStateOf(0)
        private set

    private var offset: Long? = null
    private var job: Job? = null

    fun togglePolling() {
        if (polling) stop() else start()
    }

    private fun start() {
        polling = true
        error = null
        job = viewModelScope.launch {
            while (isActive) {
                when (val r = TelegramRepo.getUpdates(bot.token, offset)) {
                    is TelegramRepo.ApiResult.Success -> {
                        if (r.data.isNotEmpty()) {
                            updates = updates + r.data
                            offset = (r.data.maxOf { it.updateId }) + 1
                        }
                        error = null
                    }
                    is TelegramRepo.ApiResult.Failure -> {
                        error = "خطا: ${r.message}"
                        stop()
                        break
                    }
                }
                pollCount++
                delay(3000)
            }
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
        polling = false
    }

    fun clear() {
        updates = emptyList()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}

private fun describeUpdate(u: TgUpdate): String {
    val m = u.message ?: u.editedMessage ?: u.channelPost
    return when {
        m != null -> {
            val who = listOfNotNull(m.from?.firstName, m.from?.lastName)
                .joinToString(" ")
                .ifEmpty { m.from?.username ?: "ناشناس" }
            val body = m.text ?: m.caption ?: "(بدون متن)"
            "$who: $body"
        }
        u.callbackQuery != null -> {
            val name = u.callbackQuery?.from?.firstName ?: "کاربر"
            "کلیک روی دکمه «${u.callbackQuery?.data}» از $name"
        }
        else -> "آپدیت بدون پیام"
    }
}

private fun chatIdOf(u: TgUpdate): String? {
    val m = u.message ?: u.editedMessage ?: u.channelPost
    return m?.chat?.id?.toString()
}

private fun dateOf(u: TgUpdate): Long? =
    (u.message ?: u.editedMessage ?: u.channelPost)?.date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(bot: Bot, onBack: () -> Unit) {
    val vm: UpdatesViewModel = viewModel(factory = BotVmFactory { UpdatesViewModel(bot) })
    val context = LocalContext.current
    val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    AppScaffold(
        title = "پیام‌های دریافتی",
        subtitle = bot.name,
        onBack = onBack,
        actions = {
            IconButton(onClick = { vm.clear() }) {
                Icon(Icons.Filled.Delete, contentDescription = "پاک کردن لیست")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            SectionCard(title = "دریافت زنده (هر ۳ ثانیه)") {
                Text(
                    "برای آزمایش: در تلگرام به ربات خود /start بفرستید و سپس این صفحه را باز نگه دارید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(onClick = { vm.togglePolling() }) {
                        Icon(
                            if (vm.polling) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (vm.polling) "توقف" else "شروع",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        if (vm.polling) "در حال دریافت... (بازخوانی ${vm.pollCount})"
                        else "متوقف است",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (vm.error != null) StatusMessage(vm.error ?: "", isError = true)
            }

            Spacer(Modifier.padding(4.dp))

            if (vm.updates.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyHint(
                        if (vm.polling) "هنوز پیامی دریافت نشده؛ صبر کنید یا به ربات پیام بدهید."
                        else "دکمه پخش (▶) را بزنید تا پیام‌های جدید دریافت شوند."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(vm.updates.asReversed(), key = { it.updateId }) { u ->
                        val chatId = chatIdOf(u)
                        val dateSec = dateOf(u)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    describeUpdate(u),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "#${u.updateId}  •  " +
                                            (if (dateSec != null) fmt.format(Date(dateSec * 1000)) else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Start
                                    )
                                    if (chatId != null) {
                                        Text(
                                            "chat_id: $chatId",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        IconButton(
                                            onClick = { copyToClipboard(context, "chat_id", chatId) },
                                            modifier = Modifier.padding(0.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.ContentCopy,
                                                contentDescription = "کپی chat_id",
                                                modifier = Modifier.padding(0.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
