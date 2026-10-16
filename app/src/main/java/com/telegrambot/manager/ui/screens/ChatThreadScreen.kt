package com.telegrambot.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.data.api.ChatSync
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.ChatMsg
import com.telegrambot.manager.data.store.ChatStore
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.EmptyHint
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.ui.theme.BlueGradient
import com.telegrambot.manager.ui.theme.TgBlue
import com.telegrambot.manager.vm.BotVmFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatThreadViewModel(
    private val bot: Bot,
    private val chatId: Long,
    private val store: ChatStore
) : ViewModel() {
    var messages by mutableStateOf(listOf<ChatMsg>())
        private set
    var title by mutableStateOf("چت $chatId")
        private set
    var sending by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    init {
        refreshLocal()
        store.markRead(bot.id, chatId)
        job = viewModelScope.launch {
            while (isActive) {
                ChatSync.syncOnce(bot.token, bot.id, store)
                refreshLocal()
                store.markRead(bot.id, chatId)
                delay(4000)
            }
        }
    }

    private fun refreshLocal() {
        val d = store.load(bot.id)
        messages = (d.messages[chatId.toString()] ?: emptyList()).sortedBy { it.date }
        title = d.threads.firstOrNull { it.chatId == chatId }?.title ?: "چت $chatId"
    }

    fun send(textRaw: String) {
        val text = textRaw.trim()
        if (text.isEmpty() || sending) return
        viewModelScope.launch {
            sending = true
            error = null
            when (val r = TelegramRepo.sendMessage(bot.token, chatId.toString(), text, null, null, false)) {
                is TelegramRepo.ApiResult.Success -> {
                    val m = r.data
                    val mine = ChatMsg(
                        id = m.messageId,
                        chatId = chatId,
                        fromId = bot.telegramId,
                        fromName = bot.name,
                        text = text,
                        isMine = true,
                        date = if (m.date > 0) m.date else System.currentTimeMillis() / 1000
                    )
                    appendMine(mine)
                }
                is TelegramRepo.ApiResult.Failure -> error = r.message
            }
            sending = false
        }
    }

    private fun appendMine(mine: ChatMsg) {
        val d = store.load(bot.id)
        val key = chatId.toString()
        val updatedMsgs = (d.messages[key] ?: emptyList()) + mine
        val updatedThreads = d.threads.map {
            if (it.chatId == chatId) it.copy(
                lastMessage = mine.text.take(80),
                lastDate = mine.date
            ) else it
        }
        store.save(
            bot.id,
            d.copy(messages = d.messages + (key to updatedMsgs), threads = updatedThreads)
        )
        refreshLocal()
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}

private fun fmtMsgTime(sec: Long): String =
    if (sec <= 0) ""
    else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sec * 1000))

private fun fmtDayLabel(sec: Long): String =
    if (sec <= 0) ""
    else SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(sec * 1000))

@Composable
fun ChatThreadScreen(bot: Bot, chatId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: ChatThreadViewModel = viewModel(
        factory = BotVmFactory { ChatThreadViewModel(bot, chatId, ChatStore.get(context)) }
    )
    var input by remember { mutableStateOf("") }

    AppScaffold(
        title = vm.title,
        subtitle = "chat_id: $chatId",
        onBack = onBack
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (vm.messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyHint("پیامی در این گفتگو نیست؛ از اینجا برای مخاطب بنویسید.")
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    items(
                        vm.messages.asReversed(),
                        key = { "${it.id}_${it.isMine}_${it.date}" }
                    ) { m ->
                        ChatBubble(m)
                    }
                }
            }

            vm.error?.let {
                Box(Modifier.padding(horizontal = 12.dp)) { StatusMessage(it, isError = true) }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("پیام…") },
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(Modifier.size(8.dp))
                SendBubble(
                    enabled = input.isNotBlank() && !vm.sending,
                    loading = vm.sending,
                    onClick = {
                        vm.send(input)
                        input = ""
                    }
                )
            }
        }
    }
}

@Composable
private fun SendBubble(enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(
                if (enabled && !loading) BlueGradient
                else Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
    ) {
        IconButton(onClick = onClick, enabled = enabled && !loading) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = Color.White
                )
            } else {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "ارسال",
                    tint = if (enabled) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMsg) {
    val isMine = m.isMine
    val shape = if (isMine)
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    else
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 290.dp)
                .clip(shape)
                .background(
                    if (isMine) BlueGradient
                    else Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                if (!isMine && m.fromName.isNotBlank()) {
                    Text(
                        m.fromName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TgBlue,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                Text(
                    m.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    fmtMsgTime(m.date),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = (if (isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        .copy(alpha = 0.65f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
