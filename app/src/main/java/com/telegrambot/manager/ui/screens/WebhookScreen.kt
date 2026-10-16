package com.telegrambot.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.TgWebhookInfo
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.KeyValueRow
import com.telegrambot.manager.ui.components.LabeledField
import com.telegrambot.manager.ui.components.PrimaryAction
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.vm.BotVmFactory
import kotlinx.coroutines.launch

class WebhookViewModel(private val bot: Bot) : ViewModel() {
    var info by mutableStateOf<TgWebhookInfo?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var result by mutableStateOf<String?>(null)
        private set
    var isError by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            busy = true
            when (val r = TelegramRepo.getWebhookInfo(bot.token)) {
                is TelegramRepo.ApiResult.Success -> info = r.data
                is TelegramRepo.ApiResult.Failure -> {
                    result = "خطا: ${r.message}"
                    isError = true
                }
            }
            busy = false
        }
    }

    fun setWebhook(url: String, dropPending: Boolean) {
        if (!url.trim().startsWith("https://")) {
            result = "آدرس وب‌هوک باید با https:// شروع شود"
            isError = true
            return
        }
        viewModelScope.launch {
            busy = true
            when (val r = TelegramRepo.setWebhook(bot.token, url.trim(), dropPending)) {
                is TelegramRepo.ApiResult.Success -> {
                    result = "وب‌هوک تنظیم شد ✓"
                    isError = false
                    refresh()
                }
                is TelegramRepo.ApiResult.Failure -> {
                    result = "خطا: ${r.message}"
                    isError = true
                }
            }
            busy = false
        }
    }

    fun deleteWebhook(dropPending: Boolean) {
        viewModelScope.launch {
            busy = true
            when (val r = TelegramRepo.deleteWebhook(bot.token, dropPending)) {
                is TelegramRepo.ApiResult.Success -> {
                    result = "وب‌هوک حذف شد ✓"
                    isError = false
                    refresh()
                }
                is TelegramRepo.ApiResult.Failure -> {
                    result = "خطا: ${r.message}"
                    isError = true
                }
            }
            busy = false
        }
    }
}

@Composable
fun WebhookScreen(bot: Bot, onBack: () -> Unit) {
    val vm: WebhookViewModel = viewModel(factory = BotVmFactory { WebhookViewModel(bot) })
    var url by remember { mutableStateOf("") }
    var dropPending by remember { mutableStateOf(false) }

    AppScaffold(
        title = "وب‌هوک",
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
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.result != null) StatusMessage(vm.result ?: "", vm.isError)

            vm.info?.let { info ->
                SectionCard(title = "وضعیت فعلی") {
                    KeyValueRow("آدرس وب‌هوک", info.url?.ifBlank { "— تنظیم نشده —" } ?: "— تنظیم نشده —")
                    KeyValueRow("آپدیت‌های در انتظار", info.pendingUpdateCount.toString())
                    if (info.ipAddress != null) KeyValueRow("IP سرور", info.ipAddress ?: "-")
                    if (info.lastErrorMessage != null) {
                        KeyValueRow("آخرین خطا", info.lastErrorMessage ?: "-")
                    }
                }
            }

            SectionCard(title = "تنظیم وب‌هوک") {
                Text(
                    "سرور شما باید گواهی HTTPS معتبر داشته باشد و از پورت‌های 443، 80، 88 یا 8443 استفاده کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LabeledField(
                    value = url,
                    onValueChange = { url = it },
                    label = "آدرس وب‌هوک (https://example.com/bot)"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "حذف آپدیت‌های معلق هنگام تنظیم",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = dropPending, onCheckedChange = { dropPending = it })
                }
                PrimaryAction(
                    text = "تنظیم وب‌هوک",
                    enabled = url.isNotBlank(),
                    loading = vm.busy,
                    onClick = { vm.setWebhook(url, dropPending) }
                )
            }

            SectionCard(title = "حذف وب‌هوک") {
                Text(
                    "برای استفاده از ابزار «پیام‌های دریافتی» (getUpdates) باید وب‌هوک حذف شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PrimaryAction(
                    text = "حذف وب‌هوک",
                    loading = vm.busy,
                    onClick = { vm.deleteWebhook(dropPending) }
                )
            }
        }
    }
}
