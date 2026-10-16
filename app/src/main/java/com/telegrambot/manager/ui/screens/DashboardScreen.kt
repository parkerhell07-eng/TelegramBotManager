package com.telegrambot.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.TgUser
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.KeyValueRow
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.ui.components.copyToClipboard
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
                is TelegramRepo.ApiResult.Failure -> error = "خطا: ${r.message}"
            }
            loading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    bot: Bot,
    botsVm: BotsViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit
) {
    val vm: DashboardViewModel = viewModel(factory = BotVmFactory { DashboardViewModel(bot) })
    val info by vm.info.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    AppScaffold(
        title = bot.name,
        subtitle = if (bot.username.isNotEmpty()) "@${bot.username}" else null,
        onBack = onBack,
        actions = {
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "بازخوانی")
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "حذف ربات")
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
            if (error != null) StatusMessage(error ?: "", isError = true)

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

            Text("ابزارهای مدیریت", style = MaterialTheme.typography.titleMedium)

            val tools = listOf(
                Triple("message", "ارسال پیام", Icons.Filled.Send),
                Triple("media", "ارسال مدیا", Icons.Filled.PhotoLibrary),
                Triple("commands", "دستورات ربات", Icons.Filled.ListAlt),
                Triple("webhook", "وب‌هوک", Icons.Filled.CloudUpload),
                Triple("updates", "پیام‌های دریافتی", Icons.Filled.NotificationsActive),
                Triple("profile", "پروفایل ربات", Icons.Filled.Person)
            )
            tools.chunked(2).forEach { rowTools ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowTools.forEach { (route, label, icon) ->
                        Card(
                            onClick = { onOpen(route) },
                            modifier = Modifier.weight(1f).height(88.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (rowTools.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            SectionCard(title = "راهنمای سریع") {
                Text(
                    "۱. در ابزار «پیام‌های دریافتی»، شناسه چت (chat_id) مخاطبان را پیدا و کپی کنید.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "۲. برای استفاده از «پیام‌های دریافتی»، نباید وب‌هوک فعالی روی ربات باشد.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "۳. برای ساخت منوی /commands از ابزار «دستورات ربات» استفاده کنید.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
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
