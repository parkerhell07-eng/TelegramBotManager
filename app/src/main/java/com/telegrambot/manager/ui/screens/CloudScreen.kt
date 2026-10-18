package com.telegrambot.manager.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.data.cloud.CloudDeployer
import com.telegrambot.manager.data.cloud.GitHubApi
import com.telegrambot.manager.data.cloud.WorkflowBuilder
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.store.CloudStore
import com.telegrambot.manager.data.util.L10n
import com.telegrambot.manager.data.util.st
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.KeyValueRow
import com.telegrambot.manager.ui.components.LabeledField
import com.telegrambot.manager.ui.components.PrimaryAction
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.vm.BotVmFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

// ── ViewModel ────────────────────────────────────────────────────────────────

class CloudViewModel(private val bot: Bot, appContext: Context) : ViewModel() {

    private val appCtx = appContext.applicationContext

    var cloud by mutableStateOf<com.telegrambot.manager.data.model.BotCloud?>(null)
        private set
    var patInput by mutableStateOf("")
    var patOwner by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    var deploying by mutableStateOf(false)
        private set
    var step by mutableStateOf(-1)
        private set
    var stepError by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var noticeIsError by mutableStateOf(false)
        private set
    var checkin by mutableStateOf<WorkflowBuilder.Checkin?>(null)
        private set
    var chats by mutableStateOf<List<WorkflowBuilder.Checkin.LastChat>>(emptyList())
        private set

    init {
        cloud = CloudStore.get(appCtx).get(bot.id)
        if (cloud != null) refreshHealth()
    }

    fun info(fa: String, en: String) {
        notice = L10n.str(fa, en)
        noticeIsError = false
    }

    fun error(fa: String, en: String) {
        notice = L10n.str(fa, en)
        noticeIsError = true
    }

    /** Validates + stores the GitHub PAT (classic, public_repo + workflow scopes). */
    fun savePat() {
        val p = patInput.trim()
        if (p.length < 20) {
            error(
                "توکن خیلی کوتاه به نظر می‌رسد؛ توکن کامل را بچسبانید.",
                "The token looks too short; paste the full token."
            )
            return
        }
        viewModelScope.launch {
            busy = true
            val (ok, login) = withContext(Dispatchers.IO) { GitHubApi(p).me() }
            if (ok) {
                CloudStore.get(appCtx).savePat(p)
                patOwner = login
                patInput = ""
                info(
                    "متصل شد به @$login — حالا ربات ابری را فعال کنید.",
                    "Connected to @$login — now enable the cloud bot."
                )
            } else {
                error(
                    "توکن رد شد ($login). دامنه‌های public_repo و workflow را تیک بزنید.",
                    "Token rejected ($login). Tick the public_repo and workflow scopes."
                )
            }
            busy = false
        }
    }

    /** One tap: repo + engine + rules + secret + first run. */
    fun deploy() {
        if (deploying) return
        viewModelScope.launch {
            deploying = true
            step = 0
            stepError = null
            notice = null
            try {
                val c = CloudDeployer.enable(appCtx, bot) { i, err ->
                    step = i
                    stepError = err
                }
                cloud = c
                step = CloudDeployer.STEPS.size
                refreshHealth()
                info(
                    "ربات ابری روشن شد! از این لحظه حتی با گوشی خاموش هم به دستورها جواب می‌دهد.",
                    "Cloud bot is live! It now answers around the clock, even with the phone off."
                )
            } catch (e: CloudDeployer.CloudException) {
                stepError = e.message
                error(
                    "استقرار در مرحله «${stepLabel(e.step)}» متوقف شد: ${e.message}",
                    "Deployment stopped at step ${e.step + 1}: ${e.message}"
                )
            } catch (e: Exception) {
                stepError = e.message
                error(
                    "خطای شبکه: ${e.message ?: "نامشخص"}",
                    "Network error: ${e.message ?: "unknown"}"
                )
            }
            deploying = false
        }
    }

    private fun stepLabel(i: Int): String =
        CloudDeployer.STEPS.getOrNull(i)?.label ?: "?"

    fun syncRules() {
        viewModelScope.launch {
            busy = true
            val ok = runCatching { CloudDeployer.syncRules(appCtx, bot) }.getOrDefault(false)
            if (ok) info(
                "قوانین در ابر به‌روز شد.",
                "Rules synced to the cloud."
            ) else error(
                "به‌روزرسانی ناموفق بود؛ اتصال اینترنت و توکن را بررسی کنید.",
                "Sync failed; check your connection and token."
            )
            busy = false
        }
    }

    fun setAdmin(adminId: Long) {
        viewModelScope.launch {
            busy = true
            val ok = runCatching { CloudDeployer.setAdmin(appCtx, bot, adminId) }.getOrDefault(false)
            if (ok) {
                cloud = CloudStore.get(appCtx).get(bot.id)
                info(
                    "این چت مدیر (صندوق پیام) شد؛ پیام کاربران به آن فوروارد می‌شود.",
                    "This chat is now the owner inbox; user messages are forwarded there."
                )
            } else error(
                "ثبت مدیر ناموفق بود.",
                "Could not set the admin chat."
            )
            busy = false
        }
    }

    fun refreshHealth() {
        viewModelScope.launch {
            checkin = runCatching { CloudDeployer.checkin(appCtx, bot) }.getOrNull()
            chats = runCatching { CloudDeployer.seenChats(appCtx, bot) }.getOrDefault(emptyList())
        }
    }

    fun disable() {
        viewModelScope.launch {
            busy = true
            runCatching { CloudDeployer.disable(appCtx, bot) }
            cloud = null
            checkin = null
            chats = emptyList()
            step = -1
            info(
                "ربات ابری خاموش شد؛ مخزن گیت‌هاب حذف شد و پاسخ‌گویی به حالت گوشی برگشت.",
                "Cloud bot disabled; the GitHub repo was deleted and phone answering is back."
            )
            busy = false
        }
    }

    fun patSaved(): Boolean = CloudStore.get(appCtx).pat().isNotBlank()
}

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun CloudScreen(bot: Bot, onBack: () -> Unit) {
    val appCtx = LocalContext.current.applicationContext
    val vm: CloudViewModel = viewModel(factory = BotVmFactory { CloudViewModel(bot, appCtx) })
    val context = LocalContext.current
    var confirmDisable by remember { mutableStateOf(false) }

    AppScaffold(
        title = st("ربات ابری ۲۴/۷", "24/7 cloud bot"),
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
            val enabled = vm.cloud?.enabled == true

            // ── Status ───────────────────────────────────────────────
            SectionCard(title = st("وضعیت", "Status")) {
                Text(
                    if (enabled)
                        st(
                            "ربات ابری فعال است — روی سرورهای رایگان گیت‌هاب ۲۴ ساعته به دستورها جواب می‌دهد؛ حتی وقتی گوشی خاموش یا بی‌اینترنت است.",
                            "Cloud bot is live — answering around the clock from GitHub's free runners, even when your phone is off."
                        )
                    else
                        st(
                            "ربات ابری فعال نیست. تا وقتی فعال نشود، ربات فقط وقتی گوشی روشن و اپ در حال اجراست جواب می‌دهد.",
                            "Cloud bot is off. Until it is enabled, the bot only answers while your phone is on and the app is running."
                        ),
                    style = MaterialTheme.typography.bodyMedium
                )
                vm.cloud?.let { c ->
                    Spacer(Modifier.height(6.dp))
                    KeyValueRow(st("مخزن ابر", "Cloud repo"), c.repoFull)
                    c.adminId.takeIf { it > 0 }?.let {
                        KeyValueRow(st("چت مدیر", "Owner inbox"), it.toString())
                    }
                }
            }

            vm.notice?.let {
                StatusMessage(it, vm.noticeIsError)
            }

            if (!enabled) {
                // ── How it works ─────────────────────────────────────
                SectionCard(title = st("این روش چطور کار می‌کند؟", "How this works")) {
                    Text(
                        st(
                            "۱. اپ با توکن گیت‌هاب شما یک مخزن عمومی کوچک می‌سازد و موتور پاسخ‌گو را در آن نصب می‌کند.",
                            "1. The app creates a small public repo with your GitHub token and installs the answering engine in it."
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        st(
                            "۲. توکن ربات با رمزنگاری libsodium به‌صورت Secret مخزن ذخیره می‌شود؛ هیچ‌کس آن را نمی‌بیند.",
                            "2. Your bot token is stored as an encrypted (libsodium sealed) repo secret; nobody can read it."
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        st(
                            "۳. گیت‌هاب هر دقیقه موتور را اجرا می‌کند و ربات بدون گوشی شما، ۲۴ ساعته پاسخ می‌دهد.",
                            "3. GitHub runs the engine every minute and your bot answers 24/7 without your phone."
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        st(
                            "۴. هر پیام کاربر به «چت مدیر» فوروارد می‌شود؛ با ریپلای روی آن، جواب کاربر را می‌دهید.",
                            "4. Every user message is forwarded to your owner inbox; reply to it to answer the user."
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // ── Step 1 — PAT ─────────────────────────────────────
                SectionCard(title = st("گام ۱ — توکن گیت‌هاب", "Step 1 — GitHub token")) {
                    Text(
                        st(
                            "یک بار در مرورگر: وارد گیت‌هاب شوید، صفحه ساخت توکن را باز کنید، دو تیک public_repo و workflow را بزنید و توکن ساخته‌شده را اینجا بچسبانید. (گیت‌هاب همان جایی است که آپدیت‌های اپ را از آن می‌گیرید؛ در ایران باز است.)",
                            "Once in your browser: sign in to GitHub, open the token page, tick public_repo and workflow, and paste the generated token here. (GitHub is the same site your app updates come from; it works in Iran.)"
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://github.com/settings/tokens/new?scopes=public_repo,workflow&description=TelegramBotManager"
                                )
                            )
                        )
                    }) {
                        Text(st("باز کردن صفحه ساخت توکن ↗", "Open the token page ↗"))
                    }
                    if (vm.patSaved()) {
                        StatusMessage(
                            st("توکن ذخیره شده است ✓ — می‌توانید مستقیم بروید سراغ گام ۲.", "Token saved ✓ — go straight to step 2."),
                            false
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LabeledField(
                        value = vm.patInput,
                        onValueChange = { vm.patInput = it },
                        label = st("توکن (ghp_...)", "Token (ghp_...)"),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryAction(
                        text = st("تایید و ذخیره توکن", "Verify and save token"),
                        loading = vm.busy,
                        onClick = { vm.savePat() }
                    )
                }

                // ── Step 2 — deploy ──────────────────────────────────
                SectionCard(title = st("گام ۲ — فعال‌سازی یک‌ضربه‌ای", "Step 2 — One-tap enable")) {
                    if (vm.step >= 0) {
                        CloudDeployer.STEPS.forEachIndexed { i, s ->
                            val color = when {
                                vm.stepError != null && i == vm.step -> MaterialTheme.colorScheme.error
                                i < vm.step -> Color(0xFF2E9E5B)
                                i == vm.step -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val mark = when {
                                vm.stepError != null && i == vm.step -> "✕"
                                i < vm.step -> "✓"
                                i == vm.step -> "…"
                                else -> "○"
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(mark, color = color, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(0.dp))
                                Text(
                                    "  " + st(s.label.split(" | ").firstOrNull() ?: s.label,
                                        s.label.split(" | ").lastOrNull() ?: s.label),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    PrimaryAction(
                        text = st("فعال‌سازی ربات ابری ۲۴/۷", "Enable the 24/7 cloud bot"),
                        loading = vm.deploying,
                        onClick = { vm.deploy() }
                    )
                    Text(
                        st(
                            "حدود یک دقیقه طول می‌کشد؛ اولین اجرای گیت‌هاب را صبر می‌کنیم.",
                            "Takes about a minute; we wait for GitHub's first run."
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                // ── Manage — health ──────────────────────────────────
                SectionCard(title = st("سلامت موتور ابری", "Cloud engine health")) {
                    vm.checkin?.let { ci ->
                        val ageMin = (System.currentTimeMillis() / 1000 - ci.time) / 60
                        val fresh = ageMin < 30
                        StatusMessage(
                            if (fresh)
                                st("زنده است ✓ — آخرین فعالیت $ageMin دقیقه پیش", "Alive ✓ — last activity ${ageMin}m ago")
                            else
                                st("بی‌خبر — آخرین فعالیت $ageMin دقیقه پیش؛ اگر اینترنت قطع بوده نگران نباشید، خودش برمی‌گردد.", "No news — last activity ${ageMin}m ago; it self-recovers."),
                            !fresh
                        )
                        Spacer(Modifier.height(8.dp))
                        KeyValueRow(st("ربات", "Bot"), "@" + (ci.me?.username ?: "?"))
                        KeyValueRow(st("دفعات اجرا", "Runs"), ci.runs.toString())
                        KeyValueRow(st("کل پاسخ‌ها", "Total replies"), ci.handled_total.toString())
                        if (ci.last_error.isNotBlank()) {
                            KeyValueRow(st("خطای آخر", "Last error"), ci.last_error)
                        }
                    } ?: Text(
                        st("هنوز خبری نیست — چند لحظه بعد دکمه را دوباره بزنید.", "No check-in yet — tap again in a few seconds."),
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { vm.refreshHealth() }) {
                        Text(st("بررسی دوباره", "Refresh"))
                    }
                }

                // ── Manage — owner inbox ─────────────────────────────
                SectionCard(title = st("صندوق پیام (چت مدیر)", "Owner inbox (admin chat)")) {
                    Text(
                        st(
                            "پیام کاربران به چت شما فوروارد می‌شود؛ کافی است روی پیام ریپلای کنید تا جواب همان کاربر برود. اول به ربات یک پیام بدهید تا چت شما در فهرست ظاهر شود.",
                            "User messages are forwarded to your chat; reply to any forwarded message to answer that user. Send the bot a message once so your chat shows up below."
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    if (vm.chats.isEmpty()) {
                        Text(
                            st("هنوز چتی دیده نشده.", "No chats seen yet."),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        vm.chats.take(5).forEach { c ->
                            val isCur = vm.cloud?.adminId == c.id
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        (if (c.username.isNotBlank()) "@" + c.username + " · " else "") + c.id,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { vm.setAdmin(c.id) }) {
                                    Text(
                                        if (isCur) st("مدیر ✓", "Admin ✓")
                                        else st("انتخاب", "Select")
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = { vm.refreshHealth() }) {
                        Text(st("به‌روزرسانی فهرست چت‌ها", "Refresh chat list"))
                    }
                }

                // ── Manage — actions ─────────────────────────────────
                SectionCard(title = st("مدیریت", "Manage")) {
                    Text(
                        st(
                            "قوانین (دستورها، کلیدواژه‌ها، خوش‌آمد) را از ابزار «پاسخگویی خودکار» تغییر دهید و بعد اینجا همگام‌سازی کنید.",
                            "Edit rules (commands, keywords, welcome) in the Auto responder tool, then sync here."
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryAction(
                        text = st("همگام‌سازی قوانین با ابر", "Sync rules to the cloud"),
                        loading = vm.busy,
                        onClick = { vm.syncRules() }
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/" + (vm.cloud?.repoFull ?: ""))
                            )
                        )
                    }) {
                        Text(st("دیدن مخزن در گیت‌هاب ↗", "Open the repo on GitHub ↗"))
                    }
                    TextButton(onClick = { confirmDisable = true }) {
                        Text(
                            st("خاموش‌کردن ربات ابری و حذف مخزن", "Disable cloud bot and delete the repo"),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ── Limits note ──────────────────────────────────────────
            SectionCard(title = st("نکته‌ها", "Notes")) {
                Text(
                    st(
                        "• پاسخ‌ها معمولاً چند‌ثانیه‌ای می‌رسند؛ گاهی زیر بار گیت‌هاب تا چند دقیقه طول می‌کشد.",
                        "• Replies usually arrive in seconds; under GitHub load they can take a few minutes."
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    st(
                        "• مخزن عمومی است ولی فقط موتور و قوانین را می‌بیند؛ توکن ربات Secret رمزنگاری‌شده است.",
                        "• The repo is public but contains only the engine and rules; the bot token is an encrypted secret."
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    st(
                        "• برای ارسال زمان‌بندی‌شده و همگانی هم می‌توانید از همان ابزارهای اپ استفاده کنید (وقتی اپ باز است).",
                        "• Scheduled and broadcast sends still work from the app tools (while the app is open)."
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (confirmDisable) {
            AlertDialog(
                onDismissRequest = { confirmDisable = false },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text(st("خاموش‌کردن ربات ابری", "Disable the cloud bot")) },
                text = {
                    Text(
                        st(
                            "مخزن گیت‌هاب حذف می‌شود و پاسخ‌گویی ۲۴/۷ متوقف می‌شود (ربات فقط وقتی گوشی روشن است جواب می‌دهد). ادامه می‌دهید؟",
                            "The GitHub repo will be deleted and 24/7 answering stops (the bot answers only while your phone is on). Continue?"
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDisable = false
                        vm.disable()
                    }) {
                        Text(st("حذف مخزن", "Delete repo"), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDisable = false }) {
                        Text(st("انصراف", "Cancel"))
                    }
                }
            )
        }
    }
}
