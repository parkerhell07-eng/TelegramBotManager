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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.engine.BotEngine
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.TgUser
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.BotAvatar
import com.telegrambot.manager.ui.components.KeyValueRow
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.ui.components.ToolTile
import com.telegrambot.manager.ui.components.copyToClipboard
import androidx.compose.runtime.collectAsState
import com.telegrambot.manager.data.util.st
import com.telegrambot.manager.ui.theme.AccentGold
import com.telegrambot.manager.ui.theme.AccentGreen
import com.telegrambot.manager.ui.theme.AccentOrange
import com.telegrambot.manager.ui.theme.AccentPink
import com.telegrambot.manager.ui.theme.AccentTeal
import com.telegrambot.manager.ui.theme.HeroGradient
import com.telegrambot.manager.ui.theme.PremiumPurple
import com.telegrambot.manager.ui.theme.TgBlue
import com.telegrambot.manager.vm.BotVmFactory
import com.telegrambot.manager.vm.BotsViewModel
import kotlinx.coroutines.launch

class DashboardViewModel(private val bot: Bot) : ViewModel() {
    var info by mutableStateOf<TgUser?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            when (val r = TelegramRepo.getMe(bot.token)) {
                is TelegramRepo.ApiResult.Success -> info = r.data
                is TelegramRepo.ApiResult.Failure -> error = r.message
            }
            loading = false
        }
    }
}

@Composable
fun DashboardScreen(
    bot: Bot,
    botsVm: BotsViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit
) {
    val vm: DashboardViewModel = viewModel(factory = BotVmFactory { DashboardViewModel(bot) })
    val info = vm.info
    val loading = vm.loading
    val error = vm.error
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val pollingBots by BotEngine.polling.collectAsState()

    AppScaffold(
        title = "داشبورد ربات",
        subtitle = if (bot.username.isNotEmpty()) "@${bot.username}" else null,
        onBack = onBack,
        actions = {
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "بازخوانی")
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "حذف ربات",
                    tint = MaterialTheme.colorScheme.error
                )
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

            // ── Hero header ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(HeroGradient)
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BotAvatar(
                        telegramId = bot.telegramId,
                        name = bot.name,
                        size = 60.dp,
                        letterSize = 23.sp
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            bot.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            if (bot.username.isNotEmpty()) "@${bot.username}"
                            else "شناسه: ${bot.telegramId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "ربات متصل است",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                            if (bot.id in pollingBots) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(AccentGreen.copy(alpha = 0.85f))
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        "پاسخ‌گویی خودکار فعال",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF062B15)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Bot info card ────────────────────────────────────────────
            info?.let { u ->
                SectionCard(title = "اطلاعات ربات") {
                    KeyValueRow("نام", u.firstName ?: "-")
                    KeyValueRow("نام کاربری", if (u.username != null) "@${u.username}" else "-")
                    KeyValueRow(
                        "شناسه ربات",
                        u.id.toString(),
                        onCopy = { copyToClipboard(context, "bot_id", u.id.toString()) }
                    )
                    KeyValueRow("عضویت در گروه‌ها", if (u.canJoinGroups == true) "دارد" else "ندارد")
                    KeyValueRow(
                        "خواندن پیام گروه‌ها",
                        if (u.canReadAllGroupMessages == true) "دارد" else "ندارد"
                    )
                    if (u.username != null) {
                        KeyValueRow(
                            "لینک ربات",
                            "https://t.me/${u.username}",
                            onCopy = {
                                copyToClipboard(context, "link", "https://t.me/${u.username}")
                            }
                        )
                    }
                }
            }

            // ── Tools grid ───────────────────────────────────────────────
            Text(
                "ابزارهای مدیریت",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )

            val tools = listOf(
                Pair(Tools.autoreply, AccentGreen),
                Pair(Tools.assistant, AccentGold),
                Pair(Tools.chats, TgBlue),
                Pair(Tools.broadcast, AccentOrange),
                Pair(Tools.members, AccentTeal),
                Pair(Tools.poll, AccentPink),
                Pair(Tools.schedule, AccentGreen),
                Pair(Tools.stats, PremiumPurple),
                Pair(Tools.message, TgBlue),
                Pair(Tools.media, PremiumPurple),
                Pair(Tools.commands, AccentTeal),
                Pair(Tools.webhook, AccentOrange),
                Pair(Tools.updates, AccentPink),
                Pair(Tools.profile, AccentGreen)
            )
            tools.chunked(2).forEach { rowTools ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowTools.forEach { (tool, accent) ->
                        ToolTile(
                            label = st(tool.fa, tool.en),
                            icon = tool.icon,
                            accent = accent,
                            onClick = { onOpen(tool.route) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowTools.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            SectionCard(title = "راهنمای سریع") {
                Text(
                    "۱. در ابزار «پاسخگویی خودکار»، گوش‌دهی ۲۴ ساعته را روشن کنید تا ربات به /start و دستورها جواب بدهد.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "۲. در ابزار «پیام‌های دریافتی»، شناسه چت (chat_id) مخاطبان را پیدا و کپی کنید.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "۳. برای استفاده از «پیام‌های دریافتی»، نباید وب‌هوک فعالی روی ربات باشد.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "۴. برای ساخت منوی /commands از ابزار «دستورات ربات» استفاده کنید.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("حذف ربات") },
                text = { Text("«${bot.name}» از اپ حذف شود؟ توکن آن پاک خواهد شد.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        botsVm.deleteBot(bot.id)
                        onBack()
                    }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("انصراف") }
                }
            )
        }
    }
}

/** Static tool metadata: Persian + English label, icon and navigation route. */
private data class Tool(
    val route: String,
    val fa: String,
    val en: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private object Tools {
    val autoreply = Tool("autoreply", "پاسخگویی خودکار", "Auto responder", Icons.Filled.SupportAgent)
    val assistant = Tool("assistant", "دستیار هوشمند", "Smart assistant", Icons.Filled.AutoAwesome)
    val chats = Tool("chats", "چت‌ها", "Chats", Icons.Filled.Forum)
    val broadcast = Tool("broadcast", "پیام همگانی", "Broadcast", Icons.Filled.Campaign)
    val members = Tool("members", "مدیریت اعضا", "Members", Icons.Filled.Groups)
    val poll = Tool("poll", "نظرسنجی", "Polls", Icons.Filled.Poll)
    val schedule = Tool("schedule", "زمان‌بندی پیام", "Scheduled messages", Icons.Filled.Schedule)
    val stats = Tool("stats", "آمار ربات", "Bot statistics", Icons.Filled.BarChart)
    val message = Tool("message", "ارسال پیام", "Send message", Icons.Filled.Send)
    val media = Tool("media", "ارسال مدیا", "Send media", Icons.Filled.PhotoLibrary)
    val commands = Tool("commands", "دستورات ربات", "Bot commands", Icons.Filled.ListAlt)
    val webhook = Tool("webhook", "وب‌هوک", "Webhook", Icons.Filled.CloudUpload)
    val updates = Tool("updates", "پیام‌های دریافتی", "Incoming updates", Icons.Filled.NotificationsActive)
    val profile = Tool("profile", "پروفایل ربات", "Bot profile", Icons.Filled.Person)
}
