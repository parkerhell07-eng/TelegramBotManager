package com.telegrambot.manager.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.TgPoll
import com.telegrambot.manager.data.store.ChatStore
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.ChatIdPicker
import com.telegrambot.manager.ui.components.LabeledField
import com.telegrambot.manager.ui.components.PrimaryAction
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.vm.BotVmFactory
import kotlinx.coroutines.launch
import org.json.JSONArray

class PollViewModel(private val bot: Bot, private val chatStore: ChatStore) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var result by mutableStateOf<String?>(null)
        private set
    var isError by mutableStateOf(false)
        private set
    var lastPoll by mutableStateOf<TgPoll?>(null)
        private set
    var lastPollChatId by mutableStateOf("")
        private set
    var lastPollMessageId by mutableStateOf(0L)
        private set

    fun knownChats() = chatStore.load(bot.id).threads.sortedByDescending { it.lastDate }

    fun send(
        chatIdRaw: String,
        question: String,
        options: List<String>,
        anonymous: Boolean,
        isQuiz: Boolean,
        multipleAnswers: Boolean,
        correctIndex: Int?
    ) {
        val chatId = chatIdRaw.trim()
        val cleanOptions = options.map { it.trim() }.filter { it.isNotBlank() }
        when {
            chatId.isEmpty() -> { fail("chat_id را وارد کنید"); return }
            question.isBlank() -> { fail("سوال را وارد کنید"); return }
            cleanOptions.size < 2 -> { fail("حداقل ۲ گزینه لازم است"); return }
            cleanOptions.size > 10 -> { fail("حداکثر ۱۰ گزینه مجاز است"); return }
            isQuiz && (correctIndex == null || correctIndex !in cleanOptions.indices) -> {
                fail("برای کوئیز، گزینه درست را انتخاب کنید"); return
            }
        }
        viewModelScope.launch {
            loading = true
            result = null
            val optionsJson = JSONArray().apply {
                cleanOptions.forEach { put(it) }
            }.toString()
            when (
                val r = TelegramRepo.sendPoll(
                    bot.token, chatId, question.trim(), optionsJson,
                    anonymous, if (isQuiz) "quiz" else "regular",
                    if (!isQuiz && multipleAnswers) true else null,
                    if (isQuiz) correctIndex else null
                )
            ) {
                is TelegramRepo.ApiResult.Success -> {
                    val m = r.data
                    lastPollChatId = chatId
                    lastPollMessageId = m.messageId
                    lastPoll = null
                    result = "نظرسنجی ارسال شد ✓ (message_id: ${m.messageId})"
                    isError = false
                }
                is TelegramRepo.ApiResult.Failure -> fail(r.message)
            }
            loading = false
        }
    }

    fun stop(chatIdRaw: String, messageIdRaw: String) {
        val chatId = chatIdRaw.trim()
        val messageId = messageIdRaw.trim().toLongOrNull()
        if (chatId.isEmpty() || messageId == null) {
            fail("chat_id و message_id معتبر وارد کنید")
            return
        }
        viewModelScope.launch {
            loading = true
            result = null
            when (val r = TelegramRepo.stopPoll(bot.token, chatId, messageId)) {
                is TelegramRepo.ApiResult.Success -> {
                    lastPoll = r.data
                    lastPollChatId = chatId
                    lastPollMessageId = messageId
                    result = null
                    isError = false
                }
                is TelegramRepo.ApiResult.Failure -> fail(r.message)
            }
            loading = false
        }
    }

    private fun fail(msg: String) {
        result = "خطا: $msg"
        isError = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollScreen(bot: Bot, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: PollViewModel = viewModel(
        factory = BotVmFactory { PollViewModel(bot, ChatStore.get(context)) }
    )
    val knownChats = remember { vm.knownChats() }

    var chatId by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var anonymous by remember { mutableStateOf(true) }
    var isQuiz by remember { mutableStateOf(false) }
    var multipleAnswers by remember { mutableStateOf(false) }
    var correctIndex by remember { mutableStateOf<Int?>(null) }

    var stopChatId by remember { mutableStateOf("") }
    var stopMessageId by remember { mutableStateOf("") }

    AppScaffold(
        title = "نظرسنجی",
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
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.result?.let { StatusMessage(it, vm.isError) }

            SectionCard(title = "ساخت نظرسنجی") {
                ChatIdPicker(
                    threads = knownChats,
                    value = chatId,
                    onValueChange = { chatId = it },
                    label = "chat_id مقصد"
                )
                LabeledField(
                    value = question,
                    onValueChange = { question = it },
                    label = "سوال"
                )
                options.forEachIndexed { i, opt ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}.",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        LabeledField(
                            value = opt,
                            onValueChange = { nv ->
                                options = options.mapIndexed { j, o -> if (j == i) nv else o }
                            },
                            label = "گزینه ${i + 1}",
                            modifier = Modifier.weight(1f)
                        )
                        if (options.size > 2) {
                            IconButton(
                                onClick = {
                                    options = options.filterIndexed { j, _ -> j != i }
                                    if (correctIndex != null && correctIndex == i) correctIndex = null
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "حذف گزینه",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (isQuiz) {
                            FilterChip(
                                selected = correctIndex == i,
                                onClick = { correctIndex = if (correctIndex == i) null else i },
                                label = { Text("درست") }
                            )
                        }
                    }
                }
                if (options.size < 10) {
                    OutlinedButton(
                        onClick = { options = options + "" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("افزودن گزینه")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ناشناس (بدون نمایش رأی‌دهنده)",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = anonymous, onCheckedChange = { anonymous = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "چند انتخابی (فقط نظرسنجی)",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isQuiz) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = multipleAnswers && !isQuiz,
                        onCheckedChange = { multipleAnswers = it },
                        enabled = !isQuiz
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isQuiz,
                        onClick = { isQuiz = false; correctIndex = null },
                        label = { Text("نظرسنجی معمولی") }
                    )
                    FilterChip(
                        selected = isQuiz,
                        onClick = { isQuiz = true; multipleAnswers = false },
                        label = { Text("کوئیز (با جواب درست)") }
                    )
                }
                PrimaryAction(
                    text = "ارسال نظرسنجی",
                    enabled = chatId.isNotBlank() && question.isNotBlank() && !vm.loading,
                    loading = vm.loading,
                    onClick = {
                        vm.send(chatId, question, options, anonymous, isQuiz, multipleAnswers, correctIndex)
                    }
                )
            }

            SectionCard(title = "بستن نظرسنجی و مشاهده نتایج") {
                Text(
                    "message_id نظرسنجی را از خروجی ارسال کپی کنید یا دستی وارد کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LabeledField(
                    value = stopChatId,
                    onValueChange = { stopChatId = it },
                    label = "chat_id"
                )
                LabeledField(
                    value = stopMessageId,
                    onValueChange = { stopMessageId = it },
                    label = "message_id نظرسنجی",
                )
                OutlinedButton(
                    onClick = {
                        vm.stop(stopChatId.ifBlank { chatId }, stopMessageId)
                    },
                    enabled = (stopChatId.isNotBlank() || chatId.isNotBlank()) &&
                        stopMessageId.isNotBlank() && !vm.loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("بستن و دریافت نتایج") }

                vm.lastPoll?.let { poll ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                poll.question,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (poll.isClosed) "وضعیت: بسته شده" else "وضعیت: باز",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "کل آرا: ${poll.totalVoterCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            poll.options.forEachIndexed { i, o ->
                                val pct = if (poll.totalVoterCount > 0)
                                    o.voterCount * 100 / poll.totalVoterCount else 0
                                Column {
                                    Text(
                                        "${i + 1}. ${o.text} — ${o.voterCount} رأی ($pct٪)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    LinearProgressIndicator(
                                        progress = { pct / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
