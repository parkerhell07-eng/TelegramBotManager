package com.telegrambot.manager.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.EngineControl
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.engine.BotEngine
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.BotAutoReply
import com.telegrambot.manager.data.store.AutoReplyStore
import com.telegrambot.manager.data.util.L10n
import com.telegrambot.manager.data.util.st
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.LabeledField
import com.telegrambot.manager.ui.components.PrimaryAction
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.vm.BotVmFactory
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// ── ViewModel ────────────────────────────────────────────────────────────────

class AutoReplyViewModel(private val bot: Bot, appContext: Context) : ViewModel() {

    private val appCtx: Context = appContext.applicationContext

    // Public set: the screen edits rule rows directly and saves atomically.
    var cfg by mutableStateOf(BotAutoReply(botId = bot.id))
    var loaded by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var noticeIsError by mutableStateOf(false)
        private set

    // Health-check outputs.
    var checking by mutableStateOf(false)
        private set
    var healthLines by mutableStateOf(listOf<HealthLine>())
        private set

    data class HealthLine(val text: String, val ok: Boolean)

    init {
        cfg = AutoReplyStore.get(appCtx).load(bot.id)
        loaded = true
    }

    // ── Field editors ────────────────────────────────────────────────────

    fun setEnabled(on: Boolean) {
        cfg = cfg.copy(enabled = on)
        val ctx = appCtx
        AutoReplyStore.get(ctx).save(cfg)
        if (EngineControl.running.value) {
            EngineControl.restart(ctx)
        } else if (on) {
            EngineControl.start(ctx)
        }
        notice = if (on)
            L10n.str(
                "گوش‌دهی ۲۴ ساعته روشن شد؛ حالا ربات به دستورها جواب می‌دهد.",
                "24/7 listening is on; the bot now answers commands."
            )
        else
            L10n.str("گوش‌دهی ۲۴ ساعته خاموش شد.", "24/7 listening is off.")
        noticeIsError = false
    }

    fun setWelcome(enabled: Boolean, text: String) {
        cfg = cfg.copy(welcomeEnabled = enabled, welcomeText = text)
    }

    fun setUnknown(text: String) {
        cfg = cfg.copy(unknownText = text)
    }

    // ── Save ─────────────────────────────────────────────────────────────

    fun save() {
        val ctx = appCtx
        // Normalize: trim + lowercase commands, drop empty rows.
        val commands = cfg.commands
            .map { it.copy(command = it.command.trim().trimStart('/').lowercase(), reply = it.reply.trim()) }
            .filter { it.command.isNotBlank() && it.reply.isNotBlank() }
        val keywords = cfg.keywords
            .map { it.copy(keyword = it.keyword.trim(), reply = it.reply.trim()) }
            .filter { it.keyword.isNotBlank() && it.reply.isNotBlank() }
        cfg = cfg.copy(commands = commands, keywords = keywords)
        AutoReplyStore.get(ctx).save(cfg)

        val engineOn = EngineControl.running.value
        if (engineOn) {
            EngineControl.restart(ctx)
            notice = L10n.str(
                "ذخیره شد و موتور با تنظیمات جدید ری‌استارت شد ✓",
                "Saved; the engine restarted with the new rules ✓"
            )
        } else if (cfg.enabled) {
            EngineControl.start(ctx)
            notice = L10n.str("ذخیره شد و سرویس پاسخ‌گویی روشن شد ✓", "Saved; the answer service started ✓")
        } else {
            notice = L10n.str("ذخیره شد ✓ (برای اجرا، گوش‌دهی ۲۴ ساعته را روشن کنید)", "Saved ✓ (turn on 24/7 listening to run it)")
        }
        noticeIsError = false
    }

    /** Pushes the same command list to the Telegram menu (setMyCommands). */
    fun syncMenu() {
        val filled = cfg.commands.filter { it.command.isNotBlank() }
        if (filled.isEmpty()) {
            notice = L10n.str("هیچ دستوری برای ثبت در منو وجود ندارد", "No commands to publish to the menu")
            noticeIsError = true
            return
        }
        viewModelScope.launch {
            checking = true
            val arr = JSONArray()
            filled.forEach {
                arr.put(
                    JSONObject()
                        .put("command", it.command.trim().trimStart('/').lowercase())
                        .put("description", it.reply.trim().lineSequence().firstOrNull().orEmpty().take(60))
                )
            }
            when (val r = TelegramRepo.setMyCommands(bot.token, arr.toString())) {
                is TelegramRepo.ApiResult.Success -> {
                    notice = L10n.str("منوی تلگرام با همین فهرست همگام شد ✓", "Telegram menu synced with this list ✓")
                    noticeIsError = false
                }
                is TelegramRepo.ApiResult.Failure -> {
                    notice = "خطا: ${r.message}"
                    noticeIsError = true
                }
            }
            checking = false
        }
    }

    // ── Health check ─────────────────────────────────────────────────────

    fun runHealthCheck() {
        viewModelScope.launch {
            checking = true
            healthLines = emptyList()
            val lines = mutableListOf<HealthLine>()

            when (val r = TelegramRepo.getMe(bot.token)) {
                is TelegramRepo.ApiResult.Success -> {
                    val uname = r.data.username?.let { "@$it" } ?: (r.data.firstName ?: "—")
                    lines += HealthLine(
                        L10n.str("اتصال توکن: سالم — ", "Token connection: healthy — ") + uname,
                        true
                    )
                }
                is TelegramRepo.ApiResult.Failure ->
                    lines += HealthLine(L10n.str("اتصال توکن ناموفق: ", "Token connection failed: ") + r.message, false)
            }

            when (val w = TelegramRepo.getWebhookInfo(bot.token)) {
                is TelegramRepo.ApiResult.Success -> {
                    val url = w.data.url
                    if (url.isNullOrEmpty()) {
                        lines += HealthLine(L10n.str("وب‌هوک: خالی (برای دریافت پیام درست است)", "Webhook: empty (correct for polling)"), true)
                    } else {
                        TelegramRepo.deleteWebhook(bot.token, null)
                        lines += HealthLine(
                            L10n.str("وب‌هوک فعال پیدا شد و حذف شد تا دریافت پیام آزاد شود", "Active webhook found and removed to free message polling"),
                            false
                        )
                    }
                }
                is TelegramRepo.ApiResult.Failure ->
                    lines += HealthLine(L10n.str("بررسی وب‌هوک ناموفق: ", "Webhook check failed: ") + w.message, false)
            }

            if (cfg.enabled) {
                val live = BotEngine.isPolling(bot.id)
                lines += if (live) HealthLine(
                    L10n.str("گوش‌دهی زنده: فعال — ربات در حال پاسخ‌گویی است", "Live listening: active — the bot is answering"),
                    true
                ) else HealthLine(
                    L10n.str("گوش‌دهی زنده: هنوز راه نیفتاده؛ چند ثانیه صبر کنید یا دوباره ذخیره کنید", "Live listening: not up yet; wait a few seconds or save again"),
                    false
                )
            } else {
                lines += HealthLine(
                    L10n.str("گوش‌دهی ۲۴ ساعته خاموش است — کلید بالای صفحه را روشن کنید", "24/7 listening is off — enable the switch above"),
                    false
                )
            }

            healthLines = lines
            checking = false
        }
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun AutoReplyScreen(bot: Bot, onBack: () -> Unit) {
    val appCtx = LocalContext.current.applicationContext
    val vm: AutoReplyViewModel = viewModel(factory = BotVmFactory { AutoReplyViewModel(bot, appCtx) })

    val engineRunning by EngineControl.running.collectAsState()

    AppScaffold(
        title = st("پاسخگویی خودکار", "Auto responder"),
        subtitle = bot.name,
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Why / how card ───────────────────────────────────────────
            SectionCard(title = st("چرا ربات قبلاً جواب نمی‌داد؟", "Why didn't the bot answer before?")) {
                Text(
                    st(
                        "ربات تلگرام خودش برنامه‌ای ندارد؛ فقط وقتی جواب می‌دهد که یک برنامه (مثل همین اپ) پیام‌ها را دریافت و پاسخ دهد. تا پیش از این، اپ فقط وقتی صفحه چت‌ها باز بود پیام‌ها را می‌دید و هیچ‌وقت خودکار جواب نمی‌داد. با «گوش‌دهی ۲۴ ساعته»، موتور پاسخ‌گویی در پس‌زمینه فعال می‌ماند و به /start، دستورها، کلمات کلیدی و پیام‌های ناشناخته پاسخ می‌دهد.",
                        "A Telegram bot has no logic of its own; it only answers when an app (like this one) receives the messages and replies. Until now the app saw messages only while the Chats screen was open and never answered automatically. With 24/7 listening, the answer engine runs in the background and handles /start, commands, keywords and unknown messages."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Master switch ────────────────────────────────────────────
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            st("گوش‌دهی ۲۴ ساعته", "24/7 listening"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (engineRunning)
                                st("سرویس پاسخ‌گویی فعال است", "Answer service is running")
                            else
                                st("سرویس پاسخ‌گویی متوقف است", "Answer service is stopped"),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (engineRunning) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(checked = vm.cfg.enabled, onCheckedChange = { vm.setEnabled(it) })
                }
            }

            if (vm.notice != null) StatusMessage(vm.notice ?: "", vm.noticeIsError)

            // ── Welcome ──────────────────────────────────────────────────
            SectionCard(title = st("پیام خوش‌آمد /start", "/start welcome message")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        st("پاسخ خودکار به /start", "Auto-answer /start"),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = vm.cfg.welcomeEnabled,
                        onCheckedChange = { vm.setWelcome(it, vm.cfg.welcomeText) }
                    )
                }
                LabeledField(
                    value = vm.cfg.welcomeText,
                    onValueChange = { vm.setWelcome(vm.cfg.welcomeEnabled, it) },
                    label = st("متن خوش‌آمد", "Welcome text"),
                    singleLine = false,
                    minLines = 3
                )
                Text(
                    st("متغیرها: {name} نام فرستنده، {username} آیدی او، {chat_id} شناسه چت، {bot_name} نام ربات", "Variables: {name} sender name, {username} their @handle, {chat_id} chat id, {bot_name} bot name"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Commands with replies ────────────────────────────────────
            SectionCard(title = st("دستورها و پاسخ‌ها", "Commands & replies")) {
                Text(
                    st("هر دستور یک پاسخ متنی دارد؛ با «همگام‌سازی منو» همان فهرست در منوی تلگرام هم ثبت می‌شود. دستورهای start/help/id/about به‌صورت خودکار ساخته می‌شوند.", "Each command gets a text reply; \"Sync menu\" also publishes the list to Telegram's menu. start/help/id/about are built in."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                vm.cfg.commands.forEachIndexed { i, c ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    st("دستور ${i + 1}", "Command ${i + 1}"),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    vm.cfg = vm.cfg.copy(commands = vm.cfg.commands - c)
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = st("حذف", "Remove"), modifier = Modifier.size(18.dp))
                                }
                            }
                            LabeledField(
                                value = c.command,
                                onValueChange = { v ->
                                    vm.cfg = vm.cfg.copy(
                                        commands = vm.cfg.commands.map { if (it == c) c.copy(command = v) else it }
                                    )
                                },
                                label = st("فرمان (بدون /) — مثل: price", "Command (no /) — e.g. price")
                            )
                            LabeledField(
                                value = c.reply,
                                onValueChange = { v ->
                                    vm.cfg = vm.cfg.copy(
                                        commands = vm.cfg.commands.map { if (it == c) c.copy(reply = v) else it }
                                    )
                                },
                                label = st("پاسخ ربات", "Bot reply"),
                                singleLine = false,
                                minLines = 2
                            )
                        }
                    }
                }
                OutlinedButton(onClick = {
                    vm.cfg = vm.cfg.copy(
                        commands = vm.cfg.commands + BotAutoReply.CommandReply("", "")
                    )
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(st("افزودن دستور", "Add command"))
                }
                TextButton(onClick = { vm.syncMenu() }) {
                    Text(st("همگام‌سازی منوی تلگرام با همین فهرست", "Sync Telegram menu with this list"))
                }
            }

            // ── Keywords ─────────────────────────────────────────────────
            SectionCard(title = st("کلمات کلیدی", "Keywords")) {
                Text(
                    st("اگر متن پیام کاربر یکی از این کلمات را داشته باشد، ربات همان پاسخ را می‌فرستد (فقط در چت خصوصی).", "If the user's text contains one of these words, the bot sends the matching reply (private chats only)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                vm.cfg.keywords.forEachIndexed { i, k ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    st("کلمه ${i + 1}", "Keyword ${i + 1}"),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    vm.cfg = vm.cfg.copy(keywords = vm.cfg.keywords - k)
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = st("حذف", "Remove"), modifier = Modifier.size(18.dp))
                                }
                            }
                            LabeledField(
                                value = k.keyword,
                                onValueChange = { v ->
                                    vm.cfg = vm.cfg.copy(
                                        keywords = vm.cfg.keywords.map { if (it == k) k.copy(keyword = v) else it }
                                    )
                                },
                                label = st("کلمه کلیدی", "Keyword")
                            )
                            LabeledField(
                                value = k.reply,
                                onValueChange = { v ->
                                    vm.cfg = vm.cfg.copy(
                                        keywords = vm.cfg.keywords.map { if (it == k) k.copy(reply = v) else it }
                                    )
                                },
                                label = st("پاسخ ربات", "Bot reply"),
                                singleLine = false,
                                minLines = 2
                            )
                        }
                    }
                }
                OutlinedButton(onClick = {
                    vm.cfg = vm.cfg.copy(
                        keywords = vm.cfg.keywords + BotAutoReply.KeywordReply("", "")
                    )
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(st("افزودن کلمه کلیدی", "Add keyword"))
                }
            }

            // ── Unknown fallback ─────────────────────────────────────────
            SectionCard(title = st("پاسخ به دستور ناشناخته", "Unknown-command reply")) {
                LabeledField(
                    value = vm.cfg.unknownText,
                    onValueChange = { vm.setUnknown(it) },
                    label = st("اگر خالی بماند ربات سکوت می‌کند", "Leave empty to stay silent"),
                    singleLine = false,
                    minLines = 2
                )
            }

            // ── Health check ─────────────────────────────────────────────
            SectionCard(title = st("بررسی سلامت", "Health check")) {
                if (vm.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                vm.healthLines.forEach { line ->
                    StatusMessage(text = line.text, isError = !line.ok)
                }
                OutlinedButton(
                    onClick = { vm.runHealthCheck() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !vm.checking
                ) {
                    Icon(Icons.Filled.HealthAndSafety, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(st("اجرای بررسی", "Run check"))
                }
            }

            PrimaryAction(
                text = st("ذخیره تنظیمات", "Save settings"),
                onClick = { vm.save() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}
