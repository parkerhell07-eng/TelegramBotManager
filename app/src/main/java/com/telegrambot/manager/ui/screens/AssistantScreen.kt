package com.telegrambot.manager.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.assistant.AssistantEngine
import com.telegrambot.manager.data.assistant.AssistantEngine.Goal
import com.telegrambot.manager.data.assistant.AssistantEngine.Type
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.store.TemplateStore
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.LabeledField
import com.telegrambot.manager.ui.components.PrimaryAction
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.ui.components.copyToClipboard
import com.telegrambot.manager.ui.theme.AccentGold
import com.telegrambot.manager.vm.BotVmFactory
import kotlinx.coroutines.launch

class AssistantViewModel(private val bot: Bot) : ViewModel() {
    var step by mutableStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var result by mutableStateOf<String?>(null)
    var failure by mutableStateOf<String?>(null)
        private set

    var pkg by mutableStateOf<AssistantEngine.Package?>(null)
        private set

    fun generate(businessName: String, type: Type, goals: Set<Goal>, notes: String) {
        pkg = AssistantEngine.build(businessName, type, goals, notes)
        step = 1
        result = null
        failure = null
    }

    fun backToForm() {
        step = 0
        result = null
        failure = null
    }

    /** Applies bot name + description + short description in one run. */
    fun applyIdentity(name: String, desc: String, short: String) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            result = null
            failure = null
            val problems = mutableListOf<String>()
            when (val r = TelegramRepo.setMyName(bot.token, name)) {
                is TelegramRepo.ApiResult.Failure -> problems += "نام: ${r.message}"
                else -> Unit
            }
            when (val r = TelegramRepo.setMyDescription(bot.token, desc)) {
                is TelegramRepo.ApiResult.Failure -> problems += "توضیحات: ${r.message}"
                else -> Unit
            }
            when (val r = TelegramRepo.setMyShortDescription(bot.token, short)) {
                is TelegramRepo.ApiResult.Failure -> problems += "توضیح کوتاه: ${r.message}"
                else -> Unit
            }
            busy = false
            if (problems.isEmpty()) result = "هویت ربات با موفقیت اعمال شد ✓ (در پروفایل ربات قابل مشاهده است)"
            else failure = problems.joinToString("\n")
        }
    }

    /** Registers the generated command menu via setMyCommands. */
    fun applyCommands(commands: List<Pair<String, String>>) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            result = null
            failure = null
            val json = Gson().toJson(commands.map { (c, d) ->
                mapOf("command" to c, "description" to d)
            })
            when (val r = TelegramRepo.setMyCommands(bot.token, json)) {
                is TelegramRepo.ApiResult.Success ->
                    result = "منوی دستورات ثبت شد ✓ (در ربات، / را تایپ کنید)"
                is TelegramRepo.ApiResult.Failure ->
                    failure = "ثبت دستورات ناموفق: ${r.message}"
            }
            busy = false
        }
    }

    fun clearMessages() {
        result = null
        failure = null
    }
}

@Composable
fun AssistantScreen(bot: Bot, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: AssistantViewModel = viewModel(
        factory = BotVmFactory { AssistantViewModel(bot) }
    )
    val templateStore = remember { TemplateStore.get(context) }
    val savedTemplates = remember { mutableStateOf(setOf<String>()) }

    var businessName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(Type.SHOP) }
    var selectedGoals by remember { mutableStateOf(setOf(Goal.SALES)) }

    AppScaffold(
        title = "دستیار هوشمند",
        subtitle = "ربات را متناسب با کسب‌وکارت بساز",
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
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.result?.let { StatusMessage(it, isError = false) }
            vm.failure?.let { StatusMessage(it, isError = true) }

            // ── Step 0: business form ────────────────────────────────────
            if (vm.step == 0) {
                SectionCard(title = "کسب‌وکار خودت را معرفی کن") {
                    Text(
                        "دستیار بر اساس این اطلاعات، هویت ربات، منوی دستورات، پیام‌ها و برنامهٔ پیشنهادی را کامل برایت می‌سازد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    LabeledField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = "نام کسب‌وکار (مثلاً: کافه لمیز)"
                    )
                    Text(
                        "نوع فعالیت:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val typeChunks = Type.entries.chunked(2)
                    typeChunks.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { t ->
                                FilterChip(
                                    selected = selectedType == t,
                                    onClick = { selectedType = t },
                                    label = { Text(t.label, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(
                        "هدف اصلی ربات (چند گزینه مجاز است):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Goal.entries.forEach { g ->
                            FilterChip(
                                selected = g in selectedGoals,
                                onClick = {
                                    selectedGoals = if (g in selectedGoals) selectedGoals - g
                                    else selectedGoals + g
                                },
                                label = { Text(g.label, maxLines = 1) }
                            )
                        }
                    }
                    LabeledField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = "توضیح دلخواه (محصولات، لحن پیام‌ها و…) — اختیاری",
                        singleLine = false
                    )
                    PrimaryAction(
                        text = "ساخت پکیج کامل ربات",
                        enabled = businessName.isNotBlank() && selectedGoals.isNotEmpty(),
                        onClick = {
                            vm.generate(businessName.trim(), selectedType, selectedGoals, notes)
                        }
                    )
                }
            }

            // ── Step 1: generated package ────────────────────────────────
            val p = vm.pkg
            if (vm.step == 1 && p != null) {
                SectionCard(title = "۱. هویت ربات") {
                    Text(p.botName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(p.botDescription, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "توضیح کوتاه: ${p.botShortDescription}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryAction(
                        text = "اعمال روی ربات",
                        onClick = { vm.applyIdentity(p.botName, p.botDescription, p.botShortDescription) }
                    )
                    Text(
                        "با setMyName / setMyDescription روی ربات واقعی ثبت می‌شود.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SectionCard(title = "۲. منوی دستورات") {
                    p.commands.forEach { (c, d) ->
                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                            Text(
                                "/$c",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentGold
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(d, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PrimaryAction(
                        text = "ثبت منوی دستورات روی ربات",
                        onClick = { vm.applyCommands(p.commands) }
                    )
                }

                SectionCard(title = "۳. پیام خوش‌آمد (پاسخ /start)") {
                    Text(p.welcome, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        TextButton(onClick = {
                            templateStore.add("خوش‌آمد — ${p.botName}", p.welcome)
                            savedTemplates.value = savedTemplates.value + p.welcome
                            vm.result = "در قالب‌ها ذخیره شد ✓"
                        }) {
                            Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ذخیره به قالب‌ها")
                        }
                        TextButton(onClick = {
                            copyToClipboard(context, "welcome", p.welcome)
                        }) {
                            Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("کپی")
                        }
                    }
                }

                SectionCard(title = "۴. قالب‌های پیام پیشنهادی") {
                    p.templates.forEach { (title, body) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    body.take(90) + if (body.length > 90) "…" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                templateStore.add(title, body)
                                savedTemplates.value = savedTemplates.value + body
                                vm.result = "«$title» در قالب‌ها ذخیره شد ✓"
                            }) {
                                Icon(
                                    Icons.Filled.Save,
                                    contentDescription = "ذخیره قالب",
                                    tint = if (body in savedTemplates.value) AccentGold
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        "متغیرهای {first_name} و {username} هنگام ارسال همگانی خودکار جایگزین می‌شوند.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SectionCard(title = "۵. نظرسنجی پیشنهادی") {
                    Text(
                        p.pollQuestion,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    p.pollOptions.forEachIndexed { i, o ->
                        Text(
                            "${'①' + i} $o",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        copyToClipboard(
                            context, "poll",
                            p.pollQuestion + "\n" + p.pollOptions.joinToString("\n") { "• $it" }
                        )
                    }) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("کپی (برای ابزار نظرسنجی)")
                    }
                }

                SectionCard(title = "۶. برنامهٔ پیشنهادی هفتگی") {
                    Text(p.weeklyIdea, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        TextButton(onClick = {
                            val body = p.weeklyIdea.substringAfter("\n\n", p.weeklyIdea)
                            templateStore.add("کمپین هفتگی — ${p.botName}", body)
                            savedTemplates.value = savedTemplates.value + body
                            vm.result = "متن کمپین در قالب‌ها ذخیره شد ✓ حالا در «زمان‌بندی پیام» تنظیمش کن."
                        }) {
                            Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ذخیره متن کمپین")
                        }
                        TextButton(onClick = { copyToClipboard(context, "weekly", p.weeklyIdea) }) {
                            Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("کپی")
                        }
                    }
                }

                SectionCard(title = "۷. نکات دستیار") {
                    p.tips.forEach { t ->
                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                            Text("• ", color = AccentGold, fontWeight = FontWeight.Bold)
                            Text(t, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryAction(
                        text = "ویرایش اطلاعات کسب‌وکار",
                        onClick = { vm.backToForm() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "دستیار هوشمند به‌صورت کامل روی همین دستگاه اجرا می‌شود؛ هیچ اطلاعاتی جایی جز ربات تلگرام شما ارسال نمی‌شود.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
