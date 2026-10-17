package com.telegrambot.manager.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.BuildConfig
import com.telegrambot.manager.EngineControl
import com.telegrambot.manager.data.api.Scheduler
import com.telegrambot.manager.data.backup.BackupManager
import com.telegrambot.manager.data.store.SettingsStore
import com.telegrambot.manager.data.store.ThemeMode
import com.telegrambot.manager.data.update.UpdateChecker
import com.telegrambot.manager.data.util.L10n
import com.telegrambot.manager.data.util.st
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.ui.components.UpdateOfferDialog
import com.telegrambot.manager.vm.BotsViewModel
import com.telegrambot.manager.vm.BotsViewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── ViewModel ────────────────────────────────────────────────────────────────

class SettingsViewModel(private val appContext: android.content.Context) : ViewModel() {

    private val settings = SettingsStore.get(appContext)

    var theme by mutableStateOf(settings.themeMode())
        private set
    var lang by mutableStateOf(settings.lang.value)
        private set

    var busy by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)
    var noticeIsError by mutableStateOf(false)

    var updateInfo by mutableStateOf<UpdateChecker.UpdateInfo?>(null)
        private set
    var updateBusy by mutableStateOf(false)
        private set
    var updateNote by mutableStateOf<String?>(null)
        private set

    fun setTheme(mode: ThemeMode) {
        theme = mode
        settings.setTheme(mode)
    }

    fun setLang(l: L10n.Lang) {
        lang = l
        settings.setLang(l) // also pushes into L10n for engine/notifications
    }

    fun clearNotice() {
        notice = null
    }

    /** Manual update check — always reports the outcome. */
    fun checkUpdate() {
        viewModelScope.launch {
            updateBusy = true
            updateNote = null
            val info = withContext(Dispatchers.IO) { UpdateChecker.latest() }
            updateBusy = false
            if (info == null) {
                notice = L10n.str("بررسی آپدیت ناموفق بود؛ اینترنت را بررسی کنید.", "Update check failed; check your connection.")
                noticeIsError = true
            } else if (UpdateChecker.isNewer(info.version)) {
                updateInfo = info
            } else {
                notice = L10n.str("اپ به‌روز است (نسخه ", "App is up to date (v") + BuildConfig.VERSION_NAME + L10n.str(") است.", " is installed).")
                noticeIsError = false
            }
        }
    }

    fun dismissUpdate() {
        updateInfo = null
        updateNote = null
    }

    fun skipUpdate() {
        updateInfo?.let { settings.setSkippedVersion(it.version) }
        updateInfo = null
        updateNote = null
    }

    fun installUpdate(appCtx: android.content.Context) {
        val info = updateInfo ?: return
        updateBusy = true
        updateNote = null
        viewModelScope.launch {
            try {
                val id = withContext(Dispatchers.IO) { UpdateChecker.enqueueDownload(appCtx, info) }
                withContext(Dispatchers.IO) { UpdateChecker.awaitDownloadAndInstall(appCtx, id, info) }
                updateNote = L10n.str(
                    "اگر صفحه نصب باز نشد، فایل APK را از پوشه Downloads اجرا کنید.",
                    "If the installer didn't open, run the APK from the Downloads folder."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateNote = "خطا: ${e.message ?: "نامشخص"}"
            }
            updateBusy = false
        }
    }

    // ── Backup / restore ─────────────────────────────────────────────────

    /** Builds the encrypted backup bytes (heavy: PBKDF2 runs on Default). */
    suspend fun buildBackup(passphrase: CharArray): ByteArray =
        withContext(Dispatchers.Default) { BackupManager.export(appContext, passphrase) }

    suspend fun writeBackup(uri: Uri, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: throw IllegalStateException("خروجی فایل باز نشد")
        }
    }

    /** Restores everything; returns null on success else an error message. */
    suspend fun restore(uri: Uri, passphrase: CharArray): String? = withContext(Dispatchers.Default) {
        val bytes = withContext(Dispatchers.IO) {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("ورودی فایل باز نشد")
        }
        val err = BackupManager.import(appContext, bytes, passphrase)
        if (err == null) {
            // Reload every long-running subsystem with the restored data.
            Scheduler.reschedulePending(appContext)
            EngineControl.restart(appContext)
        }
        err
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    botsVm: BotsViewModel,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val vm: SettingsViewModel = viewModel(factory = com.telegrambot.manager.vm.BotVmFactory { SettingsViewModel(appCtx) })
    val scope = rememberCoroutineScope()

    // ── dialog state ─────────────────────────────────────────────────────
    var showExportPass by remember { mutableStateOf(false) }
    var showImportPass by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var passText by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                vm.busy = true
                try {
                    val bytes = vm.buildBackup(passText.toCharArray())
                    vm.writeBackup(uri, bytes)
                    vm.notice = L10n.str("پشتیبان رمزنگاری‌شده ساخته شد ✓", "Encrypted backup created ✓")
                    vm.noticeIsError = false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    vm.notice = "خطا: ${e.message ?: "نامشخص"}"
                    vm.noticeIsError = true
                }
                passText = ""
                vm.busy = false
            }
        }
    }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportPass = true
        }
    }

    val themeLabels = mapOf(
        ThemeMode.SYSTEM to st("سیستمی", "System"),
        ThemeMode.LIGHT to st("روشن", "Light"),
        ThemeMode.DARK to st("تیره", "Dark")
    )
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    AppScaffold(title = st("تنظیمات", "Settings"), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.notice != null) StatusMessage(vm.notice ?: "", vm.noticeIsError)

            // ── Theme ────────────────────────────────────────────────────
            SectionCard(title = st("نمایش (تم)", "Appearance (theme)")) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = vm.theme == mode,
                            onClick = { vm.setTheme(mode) },
                            label = { Text(themeLabels[mode] ?: "") }
                        )
                    }
                }
                Text(
                    st(
                        "تم فوراً روی کل اپ اعمال می‌شود و ذخیره می‌ماند.",
                        "The theme applies instantly across the app and is remembered."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Language ─────────────────────────────────────────────────
            SectionCard(title = st("زبان / Language", "Language / زبان")) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = vm.lang == L10n.Lang.FA,
                        onClick = { vm.setLang(L10n.Lang.FA) },
                        label = { Text("فارسی") }
                    )
                    FilterChip(
                        selected = vm.lang == L10n.Lang.EN,
                        onClick = { vm.setLang(L10n.Lang.EN) },
                        label = { Text("English") }
                    )
                }
                Text(
                    st(
                        "دوزبانه‌سازی در نسخه ۲ روی چارچوب اصلی اپ اعمال شده؛ ابزارهای تخصصی هر ربات در نسخه‌های بعد کاملاً انگلیسی می‌شوند.",
                        "Bilingual support covers the app frame in v2; per-bot tool screens are being translated for upcoming releases."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Update ───────────────────────────────────────────────────
            SectionCard(title = st("بروزرسانی برنامه", "App updates")) {
                Text(
                    st("نسخه نصب‌شده: ", "Installed version: ") + "v" + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    st(
                        "با هر انتشار جدید، اپ هنگام باز شدن پیشنهاد آپدیت می‌دهد و می‌توانید بدون خروج از برنامه، APK را دانلود و نصب کنید.",
                        "On every new release the app offers the update at launch; you can download and install the APK without leaving the app."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { vm.checkUpdate() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !vm.updateBusy
                ) {
                    Text(st("بررسی نسخه جدید", "Check for updates"))
                }
            }

            // ── Backup ───────────────────────────────────────────────────
            SectionCard(title = st("پشتیبان‌گیری رمزنگاری‌شده", "Encrypted backup")) {
                Text(
                    st(
                        "بکاپ شامل همه چیز است: ربات‌ها و توکن‌ها، پاسخ‌گویی خودکار، پیام‌های زمان‌بندی‌شده، قالب‌ها، تاریخچه چت‌ها و تنظیمات. فایل با AES-256-GCM و کلید برگرفته از رمز عبور شما رمزنگاری می‌شود؛ بدون رمز، هیچ راه بازیابی وجود ندارد.",
                        "The backup contains everything: bots and tokens, auto-reply rules, scheduled messages, templates, chat history and settings. It is encrypted with AES-256-GCM using a key derived from your passphrase; without it there is NO recovery."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { passText = ""; showExportPass = true },
                        modifier = Modifier.weight(1f),
                        enabled = !vm.busy
                    ) { Text(st("ساخت پشتیبان", "Create backup")) }
                    OutlinedButton(
                        onClick = { passText = ""; openBackup.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f),
                        enabled = !vm.busy
                    ) { Text(st("بازیابی", "Restore")) }
                }
            }

            // ── About ────────────────────────────────────────────────────
            SectionCard(title = st("درباره", "About")) {
                Text(
                    st(
                        "مدیر ربات تلگرام — مدیریت کامل ربات‌های تلگرام روی گوشی خودتان: پاسخ‌گویی خودکار ۲۴ ساعته، ارسال همگانی، زمان‌بندی شمسی، دستیار هوشمند و آمار.",
                        "Telegram Bot Manager — run your Telegram bots from your phone: 24/7 auto-answers, broadcasting, Jalali scheduling, smart assistant and statistics."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "github.com/parkerhell07-eng/TelegramBotManager",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.padding(bottom = 10.dp))
        }
    }

    // ── dialogs ──────────────────────────────────────────────────────────

    if (showExportPass) {
        AlertDialog(
            onDismissRequest = { showExportPass = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(st("رمز عبور پشتیبان", "Backup passphrase")) },
            text = {
                Column {
                    Text(
                        st(
                            "حداقل ۸ کاراکتر؛ توکن ربات‌ها داخل فایل است، پس رمز قوی انتخاب کنید. اگر رمز را فراموش کنید فایل بازیابی‌پذیر نیست.",
                            "At least 8 characters; bot tokens are inside, so choose a strong one. If you forget it the file is unrecoverable."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(
                        value = passText,
                        onValueChange = { passText = it },
                        label = { Text(st("رمز عبور", "Passphrase")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passText.length >= 8,
                    onClick = {
                        showExportPass = false
                        createBackup.launch("tbm-backup-${dateFmt.format(Date())}.tbm2")
                    }
                ) { Text(st("ادامه", "Continue")) }
            },
            dismissButton = {
                TextButton(onClick = { showExportPass = false }) { Text(st("انصراف", "Cancel")) }
            }
        )
    }

    if (showImportPass) {
        AlertDialog(
            onDismissRequest = { showImportPass = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(st("بازیابی پشتیبان", "Restore backup")) },
            text = {
                Column {
                    Text(
                        st(
                            "همه داده‌های فعلی با محتوای فایل پشتیبان جایگزین می‌شود.",
                            "All current data will be REPLACED by the backup file."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(
                        value = passText,
                        onValueChange = { passText = it },
                        label = { Text(st("رمز عبور فایل", "File passphrase")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passText.length >= 8,
                    onClick = {
                        showImportPass = false
                        val uri = pendingImportUri
                        if (uri != null) {
                            showImportConfirm = true
                        }
                    }
                ) { Text(st("ادامه", "Continue")) }
            },
            dismissButton = {
                TextButton(onClick = { showImportPass = false }) { Text(st("انصراف", "Cancel")) }
            }
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(st("جایگزینی داده‌ها؟", "Replace all data?")) },
            text = {
                Text(
                    st(
                        "ربات‌ها، چت‌ها، زمان‌بندی‌ها و تنظیمات فعلی پاک و با پشتیبان جایگزین می‌شوند. این کار قابل بازگشت نیست.",
                        "Current bots, chats, schedules and settings will be wiped and replaced by the backup. This cannot be undone."
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    val uri = pendingImportUri
                    if (uri != null) {
                        scope.launch {
                            vm.busy = true
                            val err = vm.restore(uri, passText.toCharArray())
                            passText = ""
                            if (err == null) {
                                vm.notice = L10n.str("بازیابی کامل شد ✓", "Restore completed ✓")
                                vm.noticeIsError = false
                                botsVm.reload()
                            } else {
                                vm.notice = err
                                vm.noticeIsError = true
                            }
                            vm.busy = false
                        }
                    }
                }) { Text(st("بازیابی کن", "Restore now"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text(st("انصراف", "Cancel")) }
            }
        )
    }

    vm.updateInfo?.let { info ->
        UpdateOfferDialog(
            info = info,
            busy = vm.updateBusy,
            note = vm.updateNote,
            onDismiss = { vm.dismissUpdate() },
            onSkip = { vm.skipUpdate() },
            onInstall = { vm.installUpdate(appCtx) }
        )
    }
}
