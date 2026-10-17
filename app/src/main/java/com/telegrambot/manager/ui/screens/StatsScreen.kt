package com.telegrambot.manager.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.ChatThread
import com.telegrambot.manager.data.model.ChatMsg
import com.telegrambot.manager.data.model.TgUser
import com.telegrambot.manager.data.model.TgWebhookInfo
import com.telegrambot.manager.data.store.ChatStore
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.EmptyHint
import com.telegrambot.manager.ui.components.KeyValueRow
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.ui.components.copyToClipboard
import com.telegrambot.manager.ui.theme.AccentGreen
import com.telegrambot.manager.ui.theme.AccentOrange
import com.telegrambot.manager.ui.theme.AccentPink
import com.telegrambot.manager.ui.theme.AccentTeal
import com.telegrambot.manager.ui.theme.HeroGradient
import com.telegrambot.manager.ui.theme.TgBlue
import com.telegrambot.manager.vm.BotVmFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class StatsViewModel(private val bot: Bot, private val chatStore: ChatStore) : ViewModel() {
    var info by mutableStateOf<TgUser?>(null)
        private set
    var webhook by mutableStateOf<TgWebhookInfo?>(null)
        private set
    var threads by mutableStateOf(listOf<ChatThread>())
        private set
    var messages by mutableStateOf(mapOf<String, List<ChatMsg>>())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            when (val r = TelegramRepo.getMe(bot.token)) {
                is TelegramRepo.ApiResult.Success -> info = r.data
                is TelegramRepo.ApiResult.Failure -> error = r.message
            }
            when (val w = TelegramRepo.getWebhookInfo(bot.token)) {
                is TelegramRepo.ApiResult.Success -> webhook = w.data
                else -> Unit
            }
            val d = chatStore.load(bot.id)
            threads = d.threads
            messages = d.messages
            loading = false
        }
    }

    /** Total messages across every known chat. */
    val totalMessages: Int
        get() = messages.values.sumOf { it.size }

    val receivedMessages: Int
        get() = messages.values.sumOf { list -> list.count { !it.isMine } }

    val sentMessages: Int
        get() = messages.values.sumOf { list -> list.count { it.isMine } }

    val totalUnread: Int
        get() = threads.sumOf { it.unread }

    val privateChats: Int
        get() = threads.count { it.type == "private" }

    val groupChats: Int
        get() = threads.count { it.type == "group" || it.type == "supergroup" }

    val channelChats: Int
        get() = threads.count { it.type == "channel" }

    /** Message counts for the last 7 days (oldest → newest, epoch seconds day start). */
    fun dailyCounts(): List<Pair<Long, Int>> {
        val out = mutableListOf<Pair<Long, Int>>()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis
        for (i in 6 downTo 0) {
            val dayStart = today - i * 86_400_000L
            val dayEnd = dayStart + 86_400_000L
            val count = messages.values.sumOf { list ->
                list.count { it.date * 1000 in dayStart until dayEnd }
            }
            out += dayStart to count
        }
        return out
    }

    /** Top chats by message count (max 5). */
    fun topChats(): List<Pair<ChatThread, Int>> =
        threads.mapNotNull { t ->
            val n = messages[t.chatId.toString()]?.size ?: 0
            if (n > 0) t to n else null
        }.sortedByDescending { it.second }.take(5)
}

@Composable
fun StatsScreen(bot: Bot, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: StatsViewModel = viewModel(
        factory = BotVmFactory { StatsViewModel(bot, ChatStore.get(context)) }
    )
    val loading = vm.loading
    val error = vm.error
    val webhook = vm.webhook

    AppScaffold(
        title = "آمار ربات",
        subtitle = bot.name,
        onBack = onBack,
        actions = {
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "بازخوانی")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != null) StatusMessage(error, isError = true)

            // ── Hero counters ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(HeroGradient)
                    .padding(18.dp)
            ) {
                Column {
                    Text(
                        "خلاصه فعالیت",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        HeroStat(faNum(vm.threads.size.toLong()), "چت", Modifier.weight(1f))
                        HeroStat(faNum(vm.totalMessages.toLong()), "کل پیام‌ها", Modifier.weight(1f))
                        HeroStat(faNum(vm.totalUnread.toLong()), "نخوانده", Modifier.weight(1f))
                    }
                }
            }

            // ── Message breakdown ────────────────────────────────────────
            SectionCard(title = "تفکیک پیام‌ها و چت‌ها") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("دریافتی", vm.receivedMessages, TgBlue, Modifier.weight(1f))
                    StatTile("ارسالی", vm.sentMessages, AccentGreen, Modifier.weight(1f))
                    StatTile("شخصی", vm.privateChats, AccentTeal, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("گروهی", vm.groupChats, AccentOrange, Modifier.weight(1f))
                    StatTile("کانال", vm.channelChats, AccentPink, Modifier.weight(1f))
                }
            }

            // ── 7-day activity chart ─────────────────────────────────────
            SectionCard(title = "فعالیت ۷ روز اخیر") {
                val daily = vm.dailyCounts()
                if (daily.all { it.second == 0 }) {
                    EmptyHint("در ۷ روز گذشته پیامی ثبت نشده است. چت‌ها را در «چت‌ها» بازخوانی کنید.")
                } else {
                    WeekChart(daily)
                    Row(Modifier.fillMaxWidth()) {
                        daily.forEach { (dayStart, _) ->
                            Text(
                                dayLabel(dayStart),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── Top chats ────────────────────────────────────────────────
            val top = vm.topChats()
            if (top.isNotEmpty()) {
                SectionCard(title = "پرگفت‌وگوترین چت‌ها") {
                    top.forEach { (thread, count) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                thread.title.ifBlank { thread.username ?: "?" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(TgBlue.copy(alpha = 0.14f))
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "${faNum(count.toLong())} پیام",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TgBlue
                                )
                            }
                        }
                    }
                }
            }

            // ── Webhook status ───────────────────────────────────────────
            SectionCard(title = "وضعیت اتصال (وب‌هوک)") {
                if (webhook == null) {
                    Text(
                        "اطلاعات وب‌هوک دریافت نشد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (webhook.url.isNullOrBlank()) Icons.Filled.CloudOff
                            else Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = if (webhook.url.isNullOrBlank()) AccentOrange else AccentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (webhook.url.isNullOrBlank()) "حالت getUpdates (وب‌هوک غیرفعال)"
                            else "وب‌هوک فعال: ${webhook.url}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    KeyValueRow("به‌روزرسانی‌های در صف", faNum(webhook.pendingUpdateCount.toLong()))
                    webhook.lastErrorMessage?.let { msg ->
                        KeyValueRow("آخرین خطا", msg.take(60))
                    }
                }
            }

            // ── Bot identity ─────────────────────────────────────────────
            vm.info?.let { u ->
                SectionCard(title = "شناسنامه ربات") {
                    KeyValueRow("نام", u.firstName ?: "-")
                    KeyValueRow("نام کاربری", if (u.username != null) "@${u.username}" else "-")
                    KeyValueRow(
                        "شناسه ربات",
                        u.id.toString(),
                        onCopy = { copyToClipboard(context, "bot_id", u.id.toString()) }
                    )
                }
            }

            Text(
                "آمار بر اساس تاریخچهٔ ذخیره‌شده در همین دستگاه است (حداکثر ۲۰۰ پیام آخر هر چت).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            faNum(value.toLong()),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Simple bar chart of the last 7 days, drawn with Canvas. */
@Composable
private fun WeekChart(daily: List<Pair<Long, Int>>) {
    val maxCount = (daily.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val barColor = TgBlue
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val chartHeight = 120.dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(chartHeight)
            .padding(vertical = 4.dp)
    ) {
        val barSpace = 2.dp.toPx()
        val slot = size.width / daily.size
        val barWidth = (slot - barSpace * 2).coerceAtLeast(4.dp.toPx())

        // baseline
        drawLine(
            color = gridColor,
            start = Offset(0f, size.height - 1),
            end = Offset(size.width, size.height - 1),
            strokeWidth = 1.dp.toPx()
        )

        daily.forEachIndexed { i, (_, count) ->
            val h = if (count == 0) 2.dp.toPx() else (count.toFloat() / maxCount) * (size.height - 8.dp.toPx())
            drawRoundRect(
                color = barColor,
                topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
        }
    }
}

private fun dayLabel(dayStartMillis: Long): String =
    SimpleDateFormat("E", Locale("fa")).format(Date(dayStartMillis))

private fun faNum(n: Long): String =
    NumberFormat.getInstance(Locale("fa")).format(n)
