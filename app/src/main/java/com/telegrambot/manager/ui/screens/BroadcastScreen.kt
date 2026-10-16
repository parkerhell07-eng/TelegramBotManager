package com.telegrambot.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.data.api.ChatSync
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.ChatThread
import com.telegrambot.manager.data.store.ChatStore
import com.telegrambot.manager.data.store.TemplateStore
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.BotAvatar
import com.telegrambot.manager.ui.components.EmptyHint
import com.telegrambot.manager.ui.components.LabeledField
import com.telegrambot.manager.ui.components.PrimaryAction
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.ui.components.TemplateManagerDialog
import com.telegrambot.manager.ui.components.applyTemplateVars
import com.telegrambot.manager.vm.BotVmFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BroadcastViewModel(private val bot: Bot, private val chatStore: ChatStore) : ViewModel() {
    var threads by mutableStateOf(listOf<ChatThread>())
        private set
    var selected by mutableStateOf(setOf<Long>())
        private set
    var sending by mutableStateOf(false)
        private set
    var sentCount by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set
    var done by mutableStateOf(false)
        private set
    var failed by mutableStateOf(listOf<String>())
        private set
    var refreshError by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    init {
        threads = chatStore.load(bot.id).threads.sortedByDescending { it.lastDate }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshError = ChatSync.syncOnce(bot.token, bot.id, chatStore)
            threads = chatStore.load(bot.id).threads.sortedByDescending { it.lastDate }
        }
    }

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun selectAll() {
        selected = if (selected.isNotEmpty()) emptySet() else threads.map { it.chatId }.toSet()
    }

    /**
     * Sends [text] to every target with a 60ms pause between sends so the
     * Telegram global rate limit (30 msgs/sec) is never hit. Cancel-safe.
     */
    fun send(targets: List<String>, text: String, parseMode: String?, silent: Boolean) {
        if (sending || targets.isEmpty() || text.isBlank()) return
        job = viewModelScope.launch {
            sending = true
            done = false
            failed = emptyList()
            sentCount = 0
            total = targets.size
            for (id in targets) {
                if (!isActive) break
                when (
                    val r = TelegramRepo.sendMessage(bot.token, id, text, parseMode, null, silent)
                ) {
                    is TelegramRepo.ApiResult.Success -> sentCount++
                    is TelegramRepo.ApiResult.Failure ->
                        failed = failed + "$id → ${r.message.take(80)}"
                }
                delay(60)
            }
            sending = false
            done = true
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        sending = false
        done = true
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastScreen(bot: Bot, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: BroadcastViewModel = viewModel(
        factory = BotVmFactory { BroadcastViewModel(bot, ChatStore.get(context)) }
    )
    val templateStore = remember { TemplateStore.get(context) }
    var templates by remember { mutableStateOf(templateStore.load()) }
    var showTemplates by remember { mutableStateOf(false) }

    var manualIds by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var parseMode by remember { mutableStateOf<String?>(null) }
    var silent by remember { mutableStateOf(false) }

    val manualTargets = manualIds.split(Regex("[,\\s]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val selectedCount = vm.selected.size + manualTargets.size

    AppScaffold(
        title = "پیام همگانی",
        subtitle = bot.name,
        onBack = onBack,
        actions = {
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "بازخوانی چت‌ها")
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
            // ── Audience ─────────────────────────────────────────────────
            SectionCard(title = "مخاطبان") {
                if (vm.threads.isEmpty()) {
                    EmptyHint(
                        "هنوز چتی ثبت نشده است. chat_idها را دستی وارد کنید یا از ابزار «چت‌ها» ابتدا پیامی دریافت کنید."
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${vm.threads.size} چت شناخته‌شده",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = vm.selected.isNotEmpty(),
                            onClick = { vm.selectAll() },
                            label = {
                                Text(if (vm.selected.isNotEmpty()) "لغو انتخاب همه" else "انتخاب همه")
                            }
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(vm.threads, key = { it.chatId }) { t ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = t.chatId in vm.selected,
                                    onCheckedChange = { vm.toggle(t.chatId) }
                                )
                                BotAvatar(
                                    telegramId = t.chatId,
                                    name = t.title,
                                    size = 34.dp,
                                    letterSize = 14.sp
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        t.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        t.chatId.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                LabeledField(
                    value = manualIds,
                    onValueChange = { manualIds = it },
                    label = "chat_id دستی (با کاما یا فاصله جدا کنید)"
                )
                Text(
                    "$selectedCount مخاطب انتخاب شده — بین هر ارسال ۶۰ میلی‌ثانیه فاصله می‌افتد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Message ──────────────────────────────────────────────────
            SectionCard(title = "متن پیام") {
                LabeledField(
                    value = text,
                    onValueChange = { text = it },
                    label = "متن پیام (متغیرها مجاز است)",
                    singleLine = false,
                    minLines = 3
                )
                TextButton(onClick = { showTemplates = true }) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("استفاده از قالب ذخیره‌شده")
                }
                Text("فرمت متن:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = parseMode == null,
                        onClick = { parseMode = null },
                        label = { Text("بدون فرمت") }
                    )
                    FilterChip(
                        selected = parseMode == "Markdown",
                        onClick = { parseMode = "Markdown" },
                        label = { Text("Markdown") }
                    )
                    FilterChip(
                        selected = parseMode == "HTML",
                        onClick = { parseMode = "HTML" },
                        label = { Text("HTML") }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ارسال بی‌صدا (بدون نوتیفیکیشن)",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = silent, onCheckedChange = { silent = it })
                }
            }

            // ── Progress / results ───────────────────────────────────────
            if (vm.sending) {
                SectionCard(title = "در حال ارسال") {
                    LinearProgressIndicator(
                        progress = {
                            if (vm.total > 0) vm.sentCount.toFloat() / vm.total else 0f
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("ارسال شد ${vm.sentCount} از ${vm.total}")
                    OutlinedButton(
                        onClick = { vm.cancel() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("توقف ارسال") }
                }
            }
            if (vm.done && !vm.sending) {
                StatusMessage(
                    "پایان ارسال: ${vm.sentCount} موفق، ${vm.failed.size} ناموفق" +
                        if (vm.failed.isEmpty()) " ✓" else "",
                    isError = vm.failed.isNotEmpty()
                )
                vm.failed.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            vm.refreshError?.let { StatusMessage(it, isError = true) }

            PrimaryAction(
                text = "ارسال به $selectedCount مخاطب",
                enabled = selectedCount > 0 && text.isNotBlank() && !vm.sending,
                loading = vm.sending,
                onClick = {
                    val targets = vm.selected.map { it.toString() } + manualTargets
                    vm.send(targets.distinct(), text, parseMode, silent)
                }
            )
        }
    }

    if (showTemplates) {
        TemplateManagerDialog(
            templates = templates,
            onPick = { t ->
                text = applyTemplateVars(t.body, null)
                showTemplates = false
            },
            onDelete = { t ->
                templateStore.delete(t.id)
                templates = templateStore.load()
            },
            onAdd = { ti, bo ->
                templateStore.add(ti, bo)
                templates = templateStore.load()
            },
            onDismiss = { showTemplates = false }
        )
    }
}
