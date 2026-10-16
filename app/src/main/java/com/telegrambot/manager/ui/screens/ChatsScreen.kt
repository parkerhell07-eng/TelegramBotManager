package com.telegrambot.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.data.api.ChatSync
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.ChatThread
import com.telegrambot.manager.data.store.ChatStore
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.BotAvatar
import com.telegrambot.manager.ui.components.EmptyHint
import com.telegrambot.manager.ui.components.StatusMessage
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

class ChatsViewModel(private val bot: Bot, private val store: ChatStore) : ViewModel() {
    var threads by mutableStateOf(listOf<ChatThread>())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    init {
        threads = store.load(bot.id).threads.sortedByDescending { it.lastDate }
        job = viewModelScope.launch {
            while (isActive) {
                error = ChatSync.syncOnce(bot.token, bot.id, store)
                threads = store.load(bot.id).threads.sortedByDescending { it.lastDate }
                loading = false
                delay(4000)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            error = ChatSync.syncOnce(bot.token, bot.id, store)
            threads = store.load(bot.id).threads.sortedByDescending { it.lastDate }
            loading = false
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}

private fun typeBadge(type: String): String? = when (type) {
    "group", "supergroup" -> "گروه"
    "channel" -> "کانال"
    else -> null
}

private fun fmtChatTime(sec: Long): String {
    if (sec <= 0) return ""
    val cal = Calendar.getInstance().apply { timeInMillis = sec * 1000 }
    val now = Calendar.getInstance()
    val pattern =
        if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        ) "HH:mm" else "MM/dd"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(cal.timeInMillis))
}

@Composable
fun ChatsScreen(bot: Bot, onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    val context = LocalContext.current
    val vm: ChatsViewModel = viewModel(
        factory = BotVmFactory { ChatsViewModel(bot, ChatStore.get(context)) }
    )

    AppScaffold(
        title = "چت‌ها",
        subtitle = bot.name,
        onBack = onBack,
        actions = {
            IconButton(onClick = { vm.refreshNow() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "بازخوانی چت‌ها")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.error?.let { Box(Modifier.padding(top = 10.dp)) { StatusMessage(it, isError = true) } }

            if (!vm.loading && vm.threads.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyHint(
                        if (vm.error == null)
                            "هنوز کسی به ربات پیام نداده است.\nدر تلگرام به ربات پیام بدهید؛ اینجا گفتگوها ظاهر می‌شوند."
                        else "دریافت پیام‌ها ممکن نشد؛ اتصال اینترنت یا وب‌هوک را بررسی کنید."
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
                ) {
                    items(vm.threads, key = { it.chatId }) { t ->
                        ChatRow(thread = t) { onOpenChat(t.chatId) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRow(thread: ChatThread, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            BotAvatar(telegramId = thread.chatId, name = thread.title, size = 46.dp, letterSize = 18.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        thread.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    typeBadge(thread.type)?.let { badge ->
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    thread.lastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmtChatTime(thread.lastDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (thread.unread > 0) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(TgBlue)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "${thread.unread}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
