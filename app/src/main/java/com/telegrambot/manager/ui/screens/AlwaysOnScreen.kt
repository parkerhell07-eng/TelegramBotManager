package com.telegrambot.manager.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.EngineControl
import com.telegrambot.manager.data.engine.BotEngine
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.store.AlwaysOnPrefs
import com.telegrambot.manager.data.store.AutoReplyStore
import com.telegrambot.manager.data.util.L10n
import com.telegrambot.manager.data.util.st
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.vm.BotVmFactory

// ── ViewModel ────────────────────────────────────────────────────────────────

class AlwaysOnViewModel(private val bot: Bot, appContext: Context) : ViewModel() {

    private val appCtx: Context = appContext.applicationContext

    var enabled by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    init {
        enabled = AutoReplyStore.get(appCtx).load(bot.id).enabled
    }

    fun setEnabled(on: Boolean) {
        val cfg = AutoReplyStore.get(appCtx).load(bot.id)
        AutoReplyStore.get(appCtx).save(cfg.copy(enabled = on))
        enabled = on
        if (on) AlwaysOnPrefs.setPaused(appCtx, false)
        if (EngineControl.running.value) {
            EngineControl.restart(appCtx)
        } else if (on) {
            EngineControl.start(appCtx)
        }
        notice = if (on)
            L10n.str(
                "ربات همیشه فعال شد؛ از این لحظه ۲۴ ساعته به پیام‌ها جواب می‌دهد.",
                "Always-on is enabled; the bot now answers around the clock."
            )
        else
            L10n.str("ربات همیشه فعال خاموش شد.", "Always-on answering is off.")
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun AlwaysOnScreen(bot: Bot, onBack: () -> Unit, onOpenRules: () -> Unit) {
    val appCtx = LocalContext.current.applicationContext
    val vm: AlwaysOnViewModel = viewModel(factory = BotVmFactory { AlwaysOnViewModel(bot, appCtx) })
    val running by EngineControl.running.collectAsState()
    val pollingBots by BotEngine.polling.collectAsState()
    val listening = bot.id in pollingBots

    AppScaffold(
        title = st("ربات همیشه فعال", "Always-on bot"),
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
            // ── Status + master switch ───────────────────────────────
            SectionCard(title = st("وضعیت", "Status")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (running) st("سرویس ۲۴ ساعته فعال است", "24/7 service is running")
                            else st("سرویس ۲۴ ساعته متوقف است", "24/7 service is stopped"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (listening) st("این ربات در حال شنیدن و پاسخ‌گویی است", "This bot is listening and answering")
                            else st("این ربات در حال حاضر شنیده نمی‌شود", "This bot is not being listened to right now"),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (listening) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(checked = vm.enabled, onCheckedChange = { vm.setEnabled(it) })
                }
                vm.notice?.let { StatusMessage(it, false) }
                TextButton(onClick = onOpenRules) {
                    Text(
                        st(
                            "ویرایش قوانین پاسخ (دستورها، کلمات کلیدی، خوش‌آمد)",
                            "Edit reply rules (commands, keywords, welcome)"
                        )
                    )
                }
            }

            // ── How it works ─────────────────────────────────────────
            SectionCard(title = st("چطور کار می‌کند؟", "How it works")) {
                Text(
                    st(
                        "ربات روی همین گوشی اجرا می‌شود: موتور پاسخ‌گویی در پس‌زمینه ۲۴ ساعته به پیام‌های تلگرام گوش می‌دهد و بی‌درنگ به /start، دستورها، کلمات کلیدی و پیام‌های ناشناخته جواب می‌دهد. بدون ثبت‌نام، بدون سرور واسط و بدون توکن اضافه — فقط گوشی باید روشن و متصل به اینترنت باشد. همان لحظه‌ای که اپ را نصب کنید، همه‌چیز کار می‌کند.",
                        "The bot runs on this very phone: the background engine listens to Telegram around the clock and instantly answers /start, commands, keywords and unknown messages. No signup, no relay server, no extra tokens — the phone just needs to stay on and online. Everything works the moment the app is installed."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Reliability checklist ────────────────────────────────
            SectionCard(title = st("برای کارکرد ۲۴ ساعته بدون قطعی", "For uninterrupted 24/7 uptime")) {
                BatteryRow()
                Spacer(Modifier.height(10.dp))
                if (Build.VERSION.SDK_INT >= 33) {
                    NotificationRow()
                    Spacer(Modifier.height(10.dp))
                }
                InfoLine(st("پس از ری‌استارت گوشی، سرویس خودکار بالا می‌آید.", "The service auto-starts after a phone reboot."))
                InfoLine(st("نگه‌بان داخلی هر ۱۵ دقیقه سلامت سرویس را بررسی و در صورت نیاز احیا می‌کند.", "A built-in watchdog checks and revives the service every 15 minutes."))
                InfoLine(st("کشیدن اپ از صفحهٔ اخیرها سرویس را نمی‌کشد.", "Swiping the app away from Recents does not kill the service."))
            }

            // ── Manufacturer autostart guide ─────────────────────────
            OemSection()
        }
    }
}

// ── Checklist rows ───────────────────────────────────────────────────────────

/** Re-evaluates checks when the user comes back from system settings. */
@Composable
private fun onResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

@Composable
private fun ChecklistRow(
    done: Boolean,
    title: String,
    desc: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (done) "✓" else "!",
                fontWeight = FontWeight.Bold,
                color = if (done) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (actionText != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionText) }
            }
        }
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 22.dp)
        )
    }
}

@Composable
private fun BatteryRow() {
    val ctx = LocalContext.current
    val tick = onResumeTick()
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    val ignoring = remember(tick) { pm.isIgnoringBatteryOptimizations(ctx.packageName) }
    ChecklistRow(
        done = ignoring,
        title = st("معافیت از بهینه‌سازی باتری", "Battery-optimization exemption"),
        desc = st(
            "بدون این مجوز، اندروید بعد از چند ساعت اپ را می‌خواباند و ربات ساکت می‌شود.",
            "Without this, Android may doze the app after a few hours and the bot goes silent."
        ),
        actionText = if (ignoring) null else st("فعال‌سازی", "Enable")
    ) {
        try {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + ctx.packageName)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
        }
    }
}

@Composable
private fun NotificationRow() {
    val ctx = LocalContext.current
    val tick = onResumeTick()
    val granted = remember(tick) {
        ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    ChecklistRow(
        done = granted,
        title = st("اجازه اعلان", "Notification permission"),
        desc = st(
            "اعلان دائمی نشان می‌دهد ربات زنده است؛ بدون آن اندروید سرویس را کم‌اهمیت فرض می‌کند.",
            "The persistent notification proves the bot is alive; Android deprioritizes the service without it."
        ),
        actionText = if (granted) null else st("اجازه دادن", "Grant")
    ) {
        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun InfoLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("✓", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── OEM autostart guide ──────────────────────────────────────────────────────

private class OemGuide(
    val titleFa: String,
    val titleEn: String,
    val stepsFa: List<String>,
    val stepsEn: List<String>,
    val component: String?
)

private fun oemGuide(): OemGuide {
    val m = Build.MANUFACTURER.lowercase()
    return when {
        listOf("xiaomi", "redmi", "poco").any { m.contains(it) } -> OemGuide(
            "شیائومی / ردمی / پوکو", "Xiaomi / Redmi / POCO",
            listOf(
                "تنظیمات → برنامه‌ها → مجوزها → «شروع خودکار» (Autostart) را برای این اپ روشن کنید.",
                "اطلاعات برنامه → صرفه‌جویی باتری → «بدون محدودیت» را انتخاب کنید.",
                "در صفحهٔ اخیرها اپ را قفل کنید (لمس طولانی روی کارت اپ → قفل)."
            ),
            listOf(
                "Settings → Apps → Permissions → enable \"Autostart\" for this app.",
                "App info → Battery saver → choose \"No restrictions\".",
                "Pin/lock the app card in the Recents screen."
            ),
            "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
        m.contains("samsung") -> OemGuide(
            "سامسونگ", "Samsung",
            listOf(
                "تنظیمات → نگهداری باتری و دستگاه → باتری → محدودیت مصرف پس‌زمینه → اپ را در «اپ‌های هرگز در حال خواب» بگذارید.",
                "تنظیمات → برنامه‌ها → این اپ → باتری → «محدودنشده»."
            ),
            listOf(
                "Settings → Battery and device care → Battery → Background usage limits → add the app to \"Never sleeping apps\".",
                "Settings → Apps → this app → Battery → \"Unrestricted\"."
            ),
            "com.samsung.android.lool/com.samsung.android.sm.battery.ui.BatteryActivity"
        )
        m.contains("huawei") || m.contains("honor") -> OemGuide(
            "هواوی / آنر", "Huawei / Honor",
            listOf(
                "تنظیمات → باتری → راه‌اندازی برنامه‌ها → این اپ → «مدیریت خودکار» را خاموش کنید.",
                "هر سه کلید «راه‌اندازی خودکار»، «راه‌اندازی ثانویه» و «اجرای پس‌زمینه» را روشن کنید."
            ),
            listOf(
                "Settings → Battery → App launch → this app → turn off \"Manage automatically\".",
                "Enable \"Auto-launch\", \"Secondary launch\" and \"Run in background\"."
            ),
            "com.huawei.systemmanager/.startupmgmt.ui.StartupNormalAppListActivity"
        )
        m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> OemGuide(
            "اوپو / ریلمی / وان‌پلاس", "OPPO / Realme / OnePlus",
            listOf(
                "تنظیمات → باتری → این اپ → «اجازه فعالیت پس‌زمینه» را روشن کنید.",
                "تنظیمات → مدیریت مجوزها → شروع خودکار → این اپ را روشن کنید."
            ),
            listOf(
                "Settings → Battery → this app → enable \"Allow background activity\".",
                "Settings → Permission management → Autostart → enable this app."
            ),
            "com.coloros.safecenter/.permission.startup.StartupAppListActivity"
        )
        m.contains("vivo") || m.contains("iqoo") -> OemGuide(
            "ویوو", "Vivo",
            listOf(
                "تنظیمات → باتری → «مصرف برق بالای پس‌زمینه» را برای این اپ روشن کنید.",
                "i Manager → App manager → Autostart → این اپ را روشن کنید."
            ),
            listOf(
                "Settings → Battery → enable \"High background power consumption\" for this app.",
                "i Manager → App manager → Autostart → enable this app."
            ),
            "com.vivo.permissionmanager/.activity.BgStartUpManagerActivity"
        )
        else -> OemGuide(
            "دیگر گوشی‌ها", "Other devices",
            listOf(
                "تنظیمات → برنامه‌ها → این اپ → باتری → «محدودنشده» یا «بهینه‌سازی نشده».",
                "اگر گزینهٔ «شروع خودکار» در گوشی شما هست، آن را روشن کنید."
            ),
            listOf(
                "Settings → Apps → this app → Battery → \"Unrestricted\" / \"Not optimized\".",
                "If your phone has an \"Autostart\" option, enable it."
            ),
            null
        )
    }
}

private fun openOemSettings(ctx: Context, guide: OemGuide) {
    guide.component?.let { c ->
        try {
            ctx.startActivity(
                Intent().setComponent(ComponentName.unflattenFromString(c))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (e: Exception) {
        }
    }
    try {
        ctx.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + ctx.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
    }
}

@Composable
private fun OemSection() {
    val ctx = LocalContext.current
    val guide = remember { oemGuide() }
    SectionCard(title = st("تنظیمات سازندهٔ گوشی (شروع خودکار)", "Manufacturer settings (Autostart)")) {
        Text(
            st("گوشی شما: ${guide.titleFa}", "Your phone: ${guide.titleEn}"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        guide.stepsFa.forEachIndexed { i, _ ->
            Text(
                "${i + 1}. " + st(guide.stepsFa[i], guide.stepsEn[i]),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = { openOemSettings(ctx, guide) }) {
            Text(st("باز کردن تنظیمات", "Open settings"))
        }
        Text(
            st(
                "اگر صفحه باز نشد، مسیرها را دستی در تنظیمات دنبال کنید.",
                "If the page does not open, follow the steps manually in Settings."
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
