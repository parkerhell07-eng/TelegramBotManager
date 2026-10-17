package com.telegrambot.manager.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.cloud.CloudDeployer
import com.telegrambot.manager.data.cloud.CloudflareApi
import com.telegrambot.manager.data.engine.BotEngine
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.BotCloud
import com.telegrambot.manager.data.model.CloudCredentials
import com.telegrambot.manager.data.store.CloudStore
import com.telegrambot.manager.data.util.Jalali
import com.telegrambot.manager.data.util.L10n
import com.telegrambot.manager.data.util.st
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.LabeledField
import com.telegrambot.manager.ui.components.PrimaryAction
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.ui.components.copyToClipboard
import com.telegrambot.manager.ui.theme.AccentGreen
import com.telegrambot.manager.vm.BotVmFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom

// ── ViewModel ────────────────────────────────────────────────────────────────

class CloudViewModel(private val bot: Bot, appContext: Context) : ViewModel() {

    private val appCtx: Context = appContext.applicationContext

    var cfg by mutableStateOf(BotCloud(botId = bot.id))
        private set
    var tokenField by mutableStateOf("")
    var accountField by mutableStateOf("")
    var adminManual by mutableStateOf("")
    var code by mutableStateOf("")
        private set
    var adminId by mutableStateOf(0L)
        private set

    var busy by mutableStateOf(false)
        private set
    var progress by mutableStateOf("")
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var noticeIsError by mutableStateOf(false)
        private set
    var checking by mutableStateOf(false)
        private set
    var healthLines by mutableStateOf(listOf<CloudLine>())
        private set
    var webhookMissing by mutableStateOf(false)
        private set
    var confirmDisable by mutableStateOf(false)

    data class CloudLine(val text: String, val ok: Boolean)

    init {
        reload()
        val creds = CloudStore.get(appCtx).creds()
        tokenField = creds.apiToken
        accountField = creds.accountId
        val rnd = SecureRandom()
        code = (rnd.nextInt(900000) + 100000).toString()
    }

    private fun reload() {
        cfg = CloudStore.get(appCtx).load(bot.id)
        adminId = cfg.adminChatId
    }

    private fun setNotice(text: String, isError: Boolean) {
        notice = text
        noticeIsError = isError
    }

    // ── Step 1: credentials ──────────────────────────────────────────────

    fun saveCreds() {
        val token = tokenField.trim()
        if (token.isEmpty()) {
            setNotice(L10n.str("توکن API کلادفلر را وارد کنید.", "Enter your Cloudflare API token."), true)
            return
        }
        CloudStore.get(appCtx).saveCreds(CloudCredentials(apiToken = token, accountId = accountField.trim()))
        setNotice(L10n.str("اعتبار کلادفلر ذخیره شد ✓", "Cloudflare credentials saved ✓"), false)
    }

    // ── Step 2: owner chat id ────────────────────────────────────────────

    /** Scans recent updates for the verification code the owner sends. */
    fun checkCode() {
        viewModelScope.launch {
            checking = true
            if (BotEngine.isPolling(bot.id)) {
                setNotice(
                    L10n.str(
                        "موتور گوش‌دهی این ربات فعال است و دریافت همزمان ممکن نیست؛ «پاسخگویی خودکار» را موقتاً خاموش کنید یا شناسه را دستی وارد کنید.",
                        "The listening engine owns getUpdates right now; turn off 24/7 listening temporarily or enter the id manually."
                    ),
                    true
                )
                checking = false
                return@launch
            }
            when (val r = TelegramRepo.getUpdates(bot.token, null, 0)) {
                is TelegramRepo.ApiResult.Success -> {
                    val hit = r.data
                        .mapNotNull { it.message }
                        .filter { it.chat?.type == "private" && it.text?.trim() == code }
                        .maxByOrNull { it.messageId }
                    val chatId = hit?.chat?.id
                    if (chatId != null) {
                        adminId = chatId
                        setNotice(
                            L10n.str("شناسه شما ثبت شد: ", "Your chat id captured: ") + chatId,
                            false
                        )
                    } else {
                        setNotice(
                            L10n.str(
                                "هنوز پیامی با این کد نرسیده؛ کد را در تلگرام برای ربات بفرستید و دوباره «بررسی» را بزنید.",
                                "No message with this code yet; send the code to the bot in Telegram and check again."
                            ),
                            true
                        )
                    }
                }
                is TelegramRepo.ApiResult.Failure -> setNotice(
                    if (r.code == 409)
                        L10n.str(
                            "دریافت خودکار ممکن نیست (وب‌هوک فعال است)؛ شناسه را دستی وارد کنید.",
                            "Auto-detect unavailable (a webhook is active); enter the id manually."
                        )
                    else r.message,
                    true
                )
            }
            checking = false
        }
    }

    fun setAdminManual() {
        val v = adminManual.trim().toLongOrNull()
        if (v == null || v <= 0L) {
            setNotice(
                L10n.str("شناسه باید یک عدد مثبت باشد (مثلاً ۱۲۳۴۵۶۷۸۹).", "The id must be a positive number (e.g. 123456789)."),
                true
            )
            return
        }
        adminId = v
        setNotice(L10n.str("شناسه ادمین ثبت شد ✓", "Admin id saved ✓"), false)
    }

    // ── Step 3: deploy / manage ──────────────────────────────────────────

    fun deploy() {
        if (adminId <= 0L) {
            setNotice(
                L10n.str(
                    "اول شناسه ادمین را در گام ۲ ثبت کنید تا فوروارد پیام‌ها کار کند.",
                    "Capture the admin id in step 2 first so message forwarding works."
                ),
                true
            )
            return
        }
        viewModelScope.launch {
            busy = true
            notice = null
            when (val r = CloudDeployer.deploy(appCtx, bot, adminId) { progress = it }) {
                is CloudDeployer.Result.Ok -> setNotice(
                    L10n.str("استقرار ابری موفق بود ✓ آدرس: ", "Cloud deploy succeeded ✓ URL: ") + r.url,
                    false
                )
                is CloudDeployer.Result.Err -> setNotice(r.message, true)
            }
            progress = ""
            reload()
            busy = false
        }
    }

    fun syncRules() {
        viewModelScope.launch {
            busy = true
            notice = null
            when (val r = CloudDeployer.syncRules(appCtx, bot) { progress = it }) {
                is CloudDeployer.Result.Ok -> setNotice(
                    L10n.str("قوانین روی ورکر ابری به‌روز شد ✓", "Rules updated on the cloud worker ✓"),
                    false
                )
                is CloudDeployer.Result.Err -> setNotice(r.message, true)
            }
            progress = ""
            reload()
            busy = false
        }
    }

    fun disable() {
        confirmDisable = false
        viewModelScope.launch {
            busy = true
            val err = CloudDeployer.disable(appCtx, bot)
            if (err == null) {
                setNotice(
                    L10n.str("حالت ابری خاموش شد؛ گوش‌دهی گوشی دوباره فعال است.", "Cloud mode is off; phone listening is back."),
                    false
                )
            } else {
                setNotice(err, true)
            }
            reload()
            busy = false
        }
    }

    fun reattach() {
        viewModelScope.launch {
            busy = true
            val err = CloudDeployer.reattachWebhook(appCtx, bot)
            if (err == null) {
                setNotice(L10n.str("وب‌هوک ابر دوباره وصل شد ✓", "Cloud webhook re-attached ✓"), false)
            } else {
                setNotice(err, true)
            }
            reload()
            busy = false
        }
    }

    fun runHealth() {
        viewModelScope.launch {
            checking = true
            healthLines = emptyList()
            webhookMissing = false
            val lines = mutableListOf<CloudLine>()

            if (!cfg.enabled) {
                lines += CloudLine(
                    L10n.str("حالت ابری خاموش است.", "Cloud mode is off."),
                    false
                )
            } else {
                // OkHttp calls are blocking — keep them off the main thread.
                val p = withContext(Dispatchers.IO) { CloudflareApi.ping(cfg.url) }
                lines += if (p.ok) CloudLine(
                    L10n.str("ورکر کلادفلر: زنده و پاسخ‌گو", "Cloudflare worker: live and answering"),
                    true
                ) else CloudLine(
                    L10n.str("ورکر کلادفلر پاسخ نداد: ", "Cloudflare worker did not respond: ") + (p.message ?: ""),
                    false
                )

                when (val w = TelegramRepo.getWebhookInfo(bot.token)) {
                    is TelegramRepo.ApiResult.Success -> {
                        val expected = CloudDeployer.webhookUrl(cfg)
                        if (w.data.url == expected) {
                            lines += CloudLine(
                                L10n.str("وب‌هوک تلگرام: متصل به ورکر ابری", "Telegram webhook: connected to the cloud worker"),
                                true
                            )
                        } else {
                            webhookMissing = true
                            lines += CloudLine(
                                L10n.str(
                                    "وب‌هوک تلگرام به ورکر ابری اشاره نمی‌کند؛ با «بازگردانی وب‌هوک» درستش کنید.",
                                    "The Telegram webhook does not point at the cloud worker; use \"Re-attach webhook\"."
                                ),
                                false
                            )
                        }
                        if (w.data.pendingUpdateCount > 0) lines += CloudLine(
                            L10n.str("پیام در صف انتظار: ", "Updates pending: ") + w.data.pendingUpdateCount,
                            true
                        )
                        w.data.lastErrorMessage?.let {
                            lines += CloudLine(
                                L10n.str("آخرین خطای تحویل: ", "Last delivery error: ") + it,
                                false
                            )
                        }
                    }
                    is TelegramRepo.ApiResult.Failure ->
                        lines += CloudLine(L10n.str("بررسی وب‌هوک ناموفق: ", "Webhook check failed: ") + w.message, false)
                }

                if (cfg.adminChatId > 0L) lines += CloudLine(
                    L10n.str("فوروارد پیام‌ها به چت خصوصی: فعال", "Forwarding to your private chat: on"),
                    true
                )
            }

            healthLines = lines
            checking = false
        }
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun CloudScreen(bot: Bot, onBack: () -> Unit) {
    val appCtx = LocalContext.current.applicationContext
    val vm: CloudViewModel = viewModel(factory = BotVmFactory { CloudViewModel(bot, appCtx) })

    AppScaffold(
        title = st("ربات ابری ۲۴/۷", "Cloud bot 24/7"),
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
            // ── Status ───────────────────────────────────────────────────
            SectionCard(title = st("وضعیت", "Status")) {
                if (vm.cfg.enabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            st("ربات ابری فعال است — پاسخ‌گویی ۲۴/۷ حتی با گوشی خاموش", "Cloud bot is live — answering 24/7 even with the phone off"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.padding(top = 6.dp))
                    Text(
                        st("آدرس ورکر: ", "Worker URL: ") + vm.cfg.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        st("آخرین استقرار: ", "Last deploy: ") + Jalali.formatDateTime(vm.cfg.deployedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        st("شناسه ادمین (فوروارد پیام‌ها): ", "Admin id (message forwarding): ") + vm.cfg.adminChatId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        st(
                            "غیرفعال — با استقرار روی Cloudflare، ربات حتی وقتی گوشی روشن نیست به همه دستورها جواب می‌دهد.",
                            "Off — deployed on Cloudflare, the bot answers every command even when the phone is off."
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // ── How it works ────────────────────────────────────────────
            SectionCard(title = st("چطور کار می‌کند؟", "How it works")) {
                Text(
                    st(
                        "اپ یک تابع کوچک (Cloudflare Worker) روی شبکه کلادفلر مستقر می‌کند که پیام‌های تلگرام را می‌گیرد و طبق همان قوانین «پاسخگویی خودکار» جواب می‌دهد: خوش‌آمد، دستورها، کلمات کلیدی و پاسخ ناشناخته. هر پیام کاربر به چت خصوصی شما با ربات هم فوروارد می‌شود و برای پاسخ کافی است روی پیام فورواردشده ریپلای کنید تا از طرف ربات به کاربر برسد. هزینه: رایگان تا ۱۰۰هزار پیام در روز.",
                        "The app deploys a tiny Cloudflare Worker that receives Telegram updates and answers them with the same rules as the auto-responder: welcome, commands, keywords and the unknown fallback. Every user message is also forwarded to your private chat with the bot; reply to a forwarded message to answer the user as the bot. Cost: free up to 100k messages per day."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (vm.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (vm.progress.isNotEmpty()) {
                    Text(
                        vm.progress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (vm.notice != null) StatusMessage(vm.notice ?: "", vm.noticeIsError)

            // ── Step 1: Cloudflare credentials ──────────────────────────
            SectionCard(title = st("گام ۱ — اعتبار کلادفلر", "Step 1 — Cloudflare credentials")) {
                Text(
                    st(
                        "در dash.cloudflare.com وارد شوید ← My Profile ← API Tokens ← Create Token ← قالب «Edit Cloudflare Workers». توکن ساخته‌شده را اینجا وارد کنید. Account ID اختیاری است (خودکار پیدا می‌شود).",
                        "Sign in at dash.cloudflare.com → My Profile → API Tokens → Create Token → the \"Edit Cloudflare Workers\" template. Paste the token here. Account ID is optional (auto-detected)."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "dash.cloudflare.com/profile/api-tokens",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        copyToClipboard(appCtx, "cf_tokens", "https://dash.cloudflare.com/profile/api-tokens")
                    }) {
                        Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(st("کپی لینک", "Copy link"))
                    }
                }
                LabeledField(
                    value = vm.tokenField,
                    onValueChange = { vm.tokenField = it },
                    label = st("توکن API کلادفلر", "Cloudflare API token")
                )
                LabeledField(
                    value = vm.accountField,
                    onValueChange = { vm.accountField = it },
                    label = st("Account ID (اختیاری)", "Account ID (optional)")
                )
                OutlinedButton(onClick = { vm.saveCreds() }, modifier = Modifier.fillMaxWidth()) {
                    Text(st("ذخیره اعتبار", "Save credentials"))
                }
            }

            // ── Step 2: admin chat id ───────────────────────────────────
            SectionCard(title = st("گام ۲ — شناسه چت شما (ادمین)", "Step 2 — your chat id (admin)")) {
                Text(
                    st(
                        "برای اینکه پیام کاربران به شما فوروارد شود، اپ باید شناسه چت خصوصی شما را بداند. ساده‌ترین راه: کد زیر را در تلگرام به ربات بفرستید.",
                        "So user messages can be forwarded to you, the app needs your private chat id. Easiest way: send the code below to the bot in Telegram."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    vm.code,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .padding(vertical = 6.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        st("۱. کد را در تلگرام به ", "1. Send the code to ") +
                            (if (bot.username.isNotEmpty()) "@" + bot.username else bot.name) +
                            st(" بفرستید، بعد ۲. دکمه «بررسی» را بزنید:", " in Telegram, then 2. press Check:"),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { vm.checkCode() }, enabled = !vm.checking) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(st("بررسی", "Check"))
                    }
                }
                Text(
                    st("یا وارد کردن دستی شناسه (اگر آن را می‌دانید):", "Or enter the id manually if you know it:"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledField(
                        value = vm.adminManual,
                        onValueChange = { vm.adminManual = it },
                        label = st("مثلاً ۱۲۳۴۵۶۷۸۹", "e.g. 123456789"),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { vm.setAdminManual() }) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(st("ثبت", "Set"))
                    }
                }
                if (vm.adminId > 0L) {
                    StatusMessage(
                        st("شناسه ادمین آماده است: ", "Admin id ready: ") + vm.adminId,
                        isError = false
                    )
                }
            }

            // ── Step 3: deploy ──────────────────────────────────────────
            if (!vm.cfg.enabled) {
                SectionCard(title = st("گام ۳ — استقرار در ابر", "Step 3 — deploy to the cloud")) {
                    Text(
                        st(
                            "با زدن دکمه، کد Worker ساخته و آپلود می‌شود، وب‌هوک با رمز مخفی تنظیم و منوی دستورها ثبت می‌شود. مدت کار: چند ثانیه.",
                            "Pressing deploy builds and uploads the Worker, sets the webhook with a secret token and publishes the command menu. Takes a few seconds."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PrimaryAction(
                        text = st("استقرار ابری ربات", "Deploy bot to the cloud"),
                        onClick = { vm.deploy() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // ── Manage ──────────────────────────────────────────────
                SectionCard(title = st("مدیریت ربات ابری", "Manage the cloud bot")) {
                    Text(
                        st(
                            "پس از تغییر قوانین در «پاسخگویی خودکار»، با همگام‌سازی همان قوانین روی ورکر ابر اعمال می‌شود.",
                            "After editing rules in the auto-responder, sync pushes them to the cloud worker."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { vm.syncRules() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(st("همگام‌سازی قوانین با ابر", "Sync rules to the cloud"))
                    }
                    if (vm.webhookMissing) {
                        OutlinedButton(onClick = { vm.reattach() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(st("بازگردانی وب‌هوک ابر", "Re-attach cloud webhook"))
                        }
                    }
                    TextButton(
                        onClick = { vm.confirmDisable = true },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            st("غیرفعال‌سازی حالت ابری", "Turn off cloud mode"),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // ── Health ──────────────────────────────────────────────
                SectionCard(title = st("سلامت اتصال ابر", "Cloud health")) {
                    if (vm.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                    vm.healthLines.forEach { line ->
                        StatusMessage(text = line.text, isError = !line.ok)
                    }
                    OutlinedButton(onClick = { vm.runHealth() }, enabled = !vm.checking, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.HealthAndSafety, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(st("اجرای بررسی", "Run check"))
                    }
                }
            }

            SectionCard(title = st("نکته درباره «چت‌ها»", "Note about Chats")) {
                Text(
                    st(
                        "وقتی ربات ابری فعال است، تب «چت‌ها» برای این ربات پیام جدید نمی‌گیرد (دریافت همزمان با وب‌هوک ممکن نیست)؛ گفتگوها را داخل خود تلگرام در چت خصوصی با ربات دنبال کنید.",
                        "While cloud mode is on, the Chats tab stops pulling new messages for this bot (polling conflicts with the webhook); follow conversations inside Telegram in your private chat with the bot."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.padding(bottom = 12.dp))
        }

        if (vm.serverDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { vm.serverDialog = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text(st("آدرس کلود آماده", "Ready cloud address")) },
                text = {
                    Column {
                        Text(
                            st(
                                "پیش‌فرض را دست نزنید مگر اینکه سرور خودتان را مستقر کرده باشید یا اپ‌ساز آدرس جدیدی اعلام کرده باشد. آدرس باید با https:// شروع شود.",
                                "Keep the default unless you deployed your own hub or the app owner published a new address. The address must start with https://."
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.padding(top = 8.dp))
                        LabeledField(
                            value = vm.hubUrlField,
                            onValueChange = { vm.hubUrlField = it },
                            label = st("مثلاً https://tbm-hub.example.workers.dev", "e.g. https://tbm-hub.example.workers.dev")
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.saveHubUrl() }) { Text(st("ذخیره", "Save")) }
                },
                dismissButton = {
                    TextButton(onClick = { vm.serverDialog = false }) { Text(st("انصراف", "Cancel")) }
                }
            )
        }

        if (vm.confirmDisable) {
            AlertDialogDisable(
                botName = bot.name,
                onConfirm = { vm.disable() },
                onDismiss = { vm.confirmDisable = false }
            )
        }
    }
}

@Composable
private fun AlertDialogDisable(
    botName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(st("خاموش‌کردن حالت ابری", "Turn off cloud mode")) },
        text = {
            Text(
                st(
                    "برای «" + botName + "» وب‌هوک حذف می‌شود و پاسخ‌گویی به موتور گوشی برمی‌گردد (فقط وقتی اپ یا سرویس فعال باشد). ادامه می‌دهید؟",
                    "For \"" + botName + "\" the webhook is removed and answering falls back to the phone engine (only while the app or service is alive). Continue?"
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(st("بله، خاموش کن", "Yes, turn off"), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(st("انصراف", "Cancel")) }
        }
    )
}
