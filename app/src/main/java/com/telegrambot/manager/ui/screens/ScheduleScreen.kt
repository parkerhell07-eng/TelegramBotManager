package com.telegrambot.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.data.api.ChatSync
import com.telegrambot.manager.data.api.Scheduler
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.ChatThread
import com.telegrambot.manager.data.model.ScheduledMessage
import com.telegrambot.manager.data.store.ChatStore
import com.telegrambot.manager.data.store.ScheduleStore
import com.telegrambot.manager.data.store.TemplateStore
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.EmptyHint
import com.telegrambot.manager.ui.components.LabeledField
import com.telegrambot.manager.ui.components.PrimaryAction
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.ui.components.TemplateManagerDialog
import com.telegrambot.manager.ui.theme.AccentGreen
import com.telegrambot.manager.ui.theme.AccentOrange
import com.telegrambot.manager.ui.theme.PremiumPurple
import com.telegrambot.manager.ui.theme.TgBlue
import com.telegrambot.manager.vm.BotVmFactory
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

class ScheduleViewModel(
    private val bot: Bot,
    private val chatStore: ChatStore,
    private val scheduleStore: ScheduleStore,
    private val appContext: android.content.Context
) : ViewModel() {
    var threads by mutableStateOf(listOf<ChatThread>())
        private set
    var items by mutableStateOf(listOf<ScheduledMessage>())
        private set
    var syncing by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { reload() }

    fun reload() {
        threads = chatStore.load(bot.id).threads.sortedByDescending { it.lastDate }
        items = scheduleStore.forBot(bot.id)
            .sortedWith(compareBy<ScheduledMessage> {
                if (it.status == ScheduledMessage.STATUS_PENDING) 0 else 1
            }.thenBy { it.scheduledAt })
    }

    /** Syncs chats once so new recipients appear as chips. */
    fun syncChats() {
        viewModelScope.launch {
            syncing = true
            error = null
            error = ChatSync.syncOnce(bot.token, bot.id, chatStore)
            threads = chatStore.load(bot.id).threads.sortedByDescending { it.lastDate }
            syncing = false
        }
    }

    fun create(
        targets: List<String>,
        targetNames: List<String>,
        text: String,
        silent: Boolean,
        at: Long,
        repeat: String
    ) {
        val msg = scheduleStore.add(bot.id, targets, targetNames, text, silent, at, repeat)
        Scheduler.schedule(appContext, msg)
        reload()
    }

    fun cancelPending(id: String) {
        scheduleStore.cancel(id)
        Scheduler.cancelJob(appContext, id)
        reload()
    }

    fun delete(id: String) {
        scheduleStore.delete(id)
        reload()
    }

    fun setNotice(n: String?) { notice = n }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(bot: Bot, onBack: () -> Unit) {
    val context = LocalContext.current

    val vm: ScheduleViewModel = viewModel(
        factory = BotVmFactory {
            ScheduleViewModel(
                bot, ChatStore.get(context), ScheduleStore.get(context),
                context.applicationContext
            )
        }
    )
    val templateStore = remember { TemplateStore.get(context) }
    var templates by remember { mutableStateOf(templateStore.load()) }
    var showTemplates by remember { mutableStateOf(false) }

    var selected by remember { mutableStateOf(setOf<Long>()) }
    var manualIds by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var silent by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(ScheduledMessage.REPEAT_NONE) }

    val cal = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    var chosenAt by remember { mutableStateOf(cal.timeInMillis) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val manualTargets = manualIds.split(Regex("[,\\s]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val targetCount = selected.size + manualTargets.size

    // 30s ticker so the "مانده تا ارسال" labels stay fresh.
    var tick by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            tick++
        }
    }
    // Reading tick here makes this scope recompose (and refresh countdowns).
    val now = remember(tick) { System.currentTimeMillis() }

    AppScaffold(
        title = "پیام‌های زمان‌بندی‌شده",
        subtitle = bot.name,
        onBack = onBack,
        actions = {
            IconButton(onClick = { vm.syncChats() }) {
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
            if (vm.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.notice?.let { StatusMessage(it, isError = false) }
            vm.error?.let { StatusMessage(it, isError = true) }

            // ── New schedule form ────────────────────────────────────────
            SectionCard(title = "پیام جدید زمان‌بندی کنید") {
                if (vm.threads.isEmpty()) {
                    EmptyHint(
                        "هنوز چتی شناخته نشده است؛ با دکمهٔ بالای صفحه چت‌ها را بازخوانی کنید یا chat_id را دستی وارد کنید."
                    )
                } else {
                    Text(
                        "مخاطبان (${faNum(selected.size)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        vm.threads.forEach { t ->
                            FilterChip(
                                selected = t.chatId in selected,
                                onClick = {
                                    selected = if (t.chatId in selected) selected - t.chatId
                                    else selected + t.chatId
                                },
                                label = {
                                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            )
                        }
                    }
                }

                LabeledField(
                    value = manualIds,
                    onValueChange = { manualIds = it },
                    label = "chat_id دستی (با ویرگول جدا کنید)"
                )

                Spacer(Modifier.size(4.dp))

                LabeledField(
                    value = text,
                    onValueChange = { text = it },
                    label = "متن پیام"
                )
                TextButton(onClick = { showTemplates = true }) {
                    Icon(Icons.Filled.Description, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("استفاده از قالب ذخیره‌شده")
                }

                // Date & time row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "زمان ارسال:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showDate = true }) {
                        Icon(Icons.Filled.Event, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(formatPersianDate(chosenAt), maxLines = 1)
                    }
                    TextButton(onClick = { showTime = true }) {
                        Icon(Icons.Filled.AccessTime, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(formatHm(chosenAt), maxLines = 1)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetChip("۱ ساعت بعد", 1, onPick = { chosenAt = it })
                    PresetChip("۱۲ ساعت بعد", 2, onPick = { chosenAt = it })
                    PresetChip("فردا ۰۹:۰۰", 3, onPick = { chosenAt = it })
                    PresetChip("هفتهٔ بعد", 4, onPick = { chosenAt = it })
                }

                Text(
                    "تکرار:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = repeat == ScheduledMessage.REPEAT_NONE,
                        onClick = { repeat = ScheduledMessage.REPEAT_NONE },
                        label = { Text("بدون تکرار") }
                    )
                    FilterChip(
                        selected = repeat == ScheduledMessage.REPEAT_DAILY,
                        onClick = { repeat = ScheduledMessage.REPEAT_DAILY },
                        label = { Text("روزانه") }
                    )
                    FilterChip(
                        selected = repeat == ScheduledMessage.REPEAT_WEEKLY,
                        onClick = { repeat = ScheduledMessage.REPEAT_WEEKLY },
                        label = { Text("هفتگی") }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ارسال بی‌صدا (بدون نوتیفیکیشن)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = silent, onCheckedChange = { silent = it })
                }

                PrimaryAction(
                    text = "ثبت زمان‌بندی",
                    enabled = text.isNotBlank() && targetCount > 0 && chosenAt > System.currentTimeMillis() + 59_999,
                    onClick = {
                        val ids = selected.map { it.toString() } + manualTargets
                        val names = vm.threads.filter { it.chatId in selected }.map { it.title } +
                            manualTargets.map { "چت $it" }
                        vm.create(ids, names, text.trim(), silent, chosenAt, repeat)
                        text = ""
                        selected = emptySet()
                        manualIds = ""
                        vm.setNotice("پیام زمان‌بندی شد ✓ در زمان مقرر ارسال می‌شود (اپ باید نصب بماند).")
                    }
                )
            }

            // ── Pending list ─────────────────────────────────────────────
            val pending = vm.items.filter { it.status == ScheduledMessage.STATUS_PENDING }
            if (pending.isNotEmpty()) {
                Text(
                    "در انتظار ارسال (${faNum(pending.size)})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                pending.forEach { msg ->
                    ScheduleCard(
                        msg = msg,
                        now = now,
                        onCancel = { vm.cancelPending(msg.id) },
                        onDelete = { vm.delete(msg.id) }
                    )
                }
            } else {
                EmptyHint("زمان‌بندی فعالی وجود ندارد.")
            }

            // ── History ──────────────────────────────────────────────────
            val history = vm.items.filter { it.status != ScheduledMessage.STATUS_PENDING }
            if (history.isNotEmpty()) {
                Text(
                    "تاریخچه",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
                history.sortedByDescending { it.sentAt }.forEach { msg ->
                    ScheduleCard(
                        msg = msg,
                        onCancel = null,
                        onDelete = { vm.delete(msg.id) }
                    )
                }
            }
        }
    }

    // ── Date picker ──────────────────────────────────────────────────────
    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = chosenAt)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        chosenAt = combineDate(chosenAt, millis)
                    }
                    showDate = false
                }) { Text("تأیید") }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text("انصراف") }
            }
        ) { DatePicker(state = state) }
    }

    // ── Time picker ──────────────────────────────────────────────────────
    if (showTime) {
        val c = Calendar.getInstance().apply { timeInMillis = chosenAt }
        val tState = rememberTimePickerState(
            initialHour = c.get(Calendar.HOUR_OF_DAY),
            initialMinute = c.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("ساعت ارسال") },
            text = { TimePicker(state = tState) },
            confirmButton = {
                TextButton(onClick = {
                    chosenAt = combineTime(chosenAt, tState.hour, tState.minute)
                    showTime = false
                }) { Text("تأیید") }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text("انصراف") }
            }
        )
    }

    // ── Template picker ──────────────────────────────────────────────────
    if (showTemplates) {
        TemplateManagerDialog(
            templates = templates,
            onPick = { t ->
                text = t.body
                showTemplates = false
            },
            onDelete = {
                templateStore.delete(it.id)
                templates = templateStore.load()
            },
            onAdd = { title, body ->
                templateStore.add(title, body)
                templates = templateStore.load()
            },
            onDismiss = { showTemplates = false }
        )
    }
}

@Composable
private fun ScheduleCard(
    msg: ScheduledMessage,
    now: Long,
    onCancel: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val (statusLabel, statusColor) = statusInfo(msg.status)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(statusColor.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
            Spacer(Modifier.width(8.dp))
            if (msg.repeat != ScheduledMessage.REPEAT_NONE) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PremiumPurple.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (msg.repeat == ScheduledMessage.REPEAT_DAILY) "روزانه" else "هفتگی",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumPurple
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (onCancel != null) {
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Filled.Cancel,
                        contentDescription = "لغو زمان‌بندی",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "حذف",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            msg.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.size(6.dp))

        Text(
            "مخاطبان: ${faNum(msg.targets.size)} چت",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (msg.status == ScheduledMessage.STATUS_PENDING) {
            val remaining = msg.scheduledAt - now
            Text(
                if (remaining > 0) "ارسال در: ${formatPersianDateTime(msg.scheduledAt)} — ${formatRemaining(remaining)}"
                else "در صف ارسال…",
                style = MaterialTheme.typography.bodySmall,
                color = TgBlue,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            if (msg.sentAt > 0) {
                Text(
                    "${statusLabel} در: ${formatPersianDateTime(msg.sentAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (msg.resultNote.isNotBlank()) {
                Text(
                    msg.resultNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (msg.status == ScheduledMessage.STATUS_SENT) AccentGreen
                            else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────

private fun statusInfo(status: String): Pair<String, Color> = when (status) {
    ScheduledMessage.STATUS_PENDING -> "در انتظار" to TgBlue
    ScheduledMessage.STATUS_SENT -> "ارسال شد" to AccentGreen
    ScheduledMessage.STATUS_PARTIAL -> "ناقص" to AccentOrange
    ScheduledMessage.STATUS_FAILED -> "ناموفق" to Color(0xFFFF5A5A)
    else -> "لغو شده" to Color.Gray
}

/** Quick preset chips; id picks the offset rule. */
@Composable
private fun PresetChip(label: String, id: Int, onPick: (Long) -> Unit) {
    FilterChip(
        selected = false,
        onClick = {
            val c = Calendar.getInstance()
            when (id) {
                1 -> c.add(Calendar.HOUR_OF_DAY, 1)
                2 -> c.add(Calendar.HOUR_OF_DAY, 12)
                3 -> {
                    c.add(Calendar.DAY_OF_YEAR, 1)
                    c.set(Calendar.HOUR_OF_DAY, 9)
                    c.set(Calendar.MINUTE, 0)
                    c.set(Calendar.SECOND, 0)
                }
                else -> c.add(Calendar.WEEK_OF_YEAR, 1)
            }
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            onPick(c.timeInMillis)
        },
        label = { Text(label) }
    )
}

private fun combineDate(currentAt: Long, dateMillis: Long): Long {
    // DatePicker returns UTC midnight; convert through a local Calendar.
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = dateMillis
    }
    val cur = Calendar.getInstance().apply { timeInMillis = currentAt }
    val out = Calendar.getInstance().apply {
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, cur.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, cur.get(Calendar.MINUTE))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return out.timeInMillis
}

private fun combineTime(currentAt: Long, hour: Int, minute: Int): Long {
    val cur = Calendar.getInstance().apply { timeInMillis = currentAt }
    val out = Calendar.getInstance().apply {
        set(
            cur.get(Calendar.YEAR), cur.get(Calendar.MONTH), cur.get(Calendar.DAY_OF_YEAR)
        )
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return out.timeInMillis
}

private fun faNum(n: Long): String =
    NumberFormat.getInstance(Locale("fa")).format(n)

private fun formatPersianDate(millis: Long): String = try {
    val pc = android.icu.util.PersianCalendar()
    pc.timeInMillis = millis
    val fmt = android.icu.text.SimpleDateFormat("d MMMM y", android.icu.util.ULocale("fa"))
    fmt.calendar = pc
    fmt.format(pc.time)
} catch (e: Exception) {
    java.text.SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(java.util.Date(millis))
}

private fun formatPersianDateTime(millis: Long): String = try {
    val pc = android.icu.util.PersianCalendar()
    pc.timeInMillis = millis
    val fmt = android.icu.text.SimpleDateFormat("EEEE d MMMM y، ساعت HH:mm", android.icu.util.ULocale("fa"))
    fmt.calendar = pc
    fmt.format(pc.time)
} catch (e: Exception) {
    java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(java.util.Date(millis))
}

private fun formatHm(millis: Long): String =
    java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(millis))

private fun formatRemaining(ms: Long): String {
    val totalMin = ms / 60_000
    val days = totalMin / (60 * 24)
    val hours = (totalMin % (60 * 24)) / 60
    val mins = totalMin % 60
    return when {
        days > 0 -> "${faNum(days)} روز و ${faNum(hours)} ساعت مانده"
        hours > 0 -> "${faNum(hours)} ساعت و ${faNum(mins)} دقیقه مانده"
        else -> "${faNum(mins)} دقیقه مانده"
    }
}
