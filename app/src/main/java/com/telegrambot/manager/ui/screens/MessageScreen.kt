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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.LabeledField
import com.telegrambot.manager.ui.components.PrimaryAction
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.vm.BotVmFactory
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** One inline keyboard button in the editor UI. */
data class InlineBtnUi(
    val label: String = "",
    val isUrl: Boolean = false,
    val value: String = ""
)

class MessageViewModel(private val bot: Bot) : ViewModel() {
    var sending by mutableStateOf(false)
        private set
    var result by mutableStateOf<String?>(null)
        private set
    var isError by mutableStateOf(false)
        private set

    fun send(chatId: String, text: String, parseMode: String?, silent: Boolean, keyboardJson: String?) {
        viewModelScope.launch {
            sending = true
            when (
                val r = TelegramRepo.sendMessage(bot.token, chatId.trim(), text, parseMode, keyboardJson, silent)
            ) {
                is TelegramRepo.ApiResult.Success -> {
                    result = "پیام با موفقیت ارسال شد ✓"
                    isError = false
                }
                is TelegramRepo.ApiResult.Failure -> {
                    result = "خطا: ${r.message}"
                    isError = true
                }
            }
            sending = false
        }
    }
}

private fun keyboardJson(rows: List<List<InlineBtnUi>>): String? {
    val validRows = rows
        .map { r -> r.filter { it.label.isNotBlank() && it.value.isNotBlank() } }
        .filter { it.isNotEmpty() }
    if (validRows.isEmpty()) return null
    val arr = JSONArray()
    validRows.forEach { r ->
        val rowArr = JSONArray()
        r.forEach { b ->
            val o = JSONObject()
            o.put("text", b.label.trim())
            o.put(if (b.isUrl) "url" else "callback_data", b.value.trim())
            rowArr.put(o)
        }
        arr.put(rowArr)
    }
    return JSONObject().put("inline_keyboard", arr).toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(bot: Bot, onBack: () -> Unit) {
    val vm: MessageViewModel = viewModel(factory = BotVmFactory { MessageViewModel(bot) })
    var chatId by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var parseMode by remember { mutableStateOf<String?>(null) }
    var silent by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf(listOf(listOf<InlineBtnUi>())) }

    AppScaffold(title = "ارسال پیام", subtitle = bot.name, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionCard(title = "متن پیام") {
                LabeledField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    label = "شناسه چت (chat_id یا @نام‌کانال)"
                )
                LabeledField(
                    value = text,
                    onValueChange = { text = it },
                    label = "متن پیام",
                    singleLine = false,
                    minLines = 3
                )
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

            SectionCard(title = "کیبورد شیشه‌ای (اختیاری)") {
                if (rows.size == 1 && rows[0].isEmpty()) {
                    Text(
                        "دکمه‌ای اضافه نکرده‌اید. می‌توانید ردیف و دکمه اضافه کنید؛ دکمه‌ها زیر پیام نمایش داده می‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                rows.forEachIndexed { ri, row ->
                    Text("ردیف ${ri + 1}", style = MaterialTheme.typography.labelLarge)
                    row.forEachIndexed { bi, btn ->
                        ButtonEditor(
                            btn = btn,
                            onChange = { nb ->
                                rows = rows.mapIndexed { ri2, r2 ->
                                    if (ri2 == ri) {
                                        r2.mapIndexed { bi2, b2 -> if (bi2 == bi) nb else b2 }
                                    } else r2
                                }
                            },
                            onRemove = {
                                rows = rows.mapIndexed { ri2, r2 ->
                                    if (ri2 == ri) r2.filterIndexed { idx, _ -> idx != bi } else r2
                                }.filter { it.isNotEmpty() }
                            }
                        )
                    }
                    TextButton(
                        onClick = {
                            rows = rows.mapIndexed { i, r ->
                                if (i == ri) r + InlineBtnUi() else r
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("افزودن دکمه به این ردیف")
                    }
                }
                OutlinedButton(
                    onClick = { rows = rows + listOf(InlineBtnUi()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("افزودن ردیف جدید")
                }
                val preview = keyboardJson(rows)
                if (preview != null) {
                    Text("پیش‌نمایش JSON:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (vm.result != null) StatusMessage(vm.result ?: "", vm.isError)

            PrimaryAction(
                text = "ارسال پیام",
                enabled = chatId.isNotBlank() && text.isNotBlank(),
                loading = vm.sending,
                onClick = { vm.send(chatId, text, parseMode, silent, keyboardJson(rows)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ButtonEditor(
    btn: InlineBtnUi,
    onChange: (InlineBtnUi) -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !btn.isUrl,
                    onClick = { onChange(btn.copy(isUrl = false)) },
                    label = { Text("کالبک") }
                )
                FilterChip(
                    selected = btn.isUrl,
                    onClick = { onChange(btn.copy(isUrl = true)) },
                    label = { Text("لینک") }
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "حذف دکمه",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            OutlinedTextField(
                value = btn.label,
                onValueChange = { onChange(btn.copy(label = it)) },
                label = { Text("متن دکمه") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = btn.value,
                onValueChange = { onChange(btn.copy(value = it)) },
                label = {
                    Text(if (btn.isUrl) "آدرس لینک (https://...)" else "داده کالبک (مثل: action_1)")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}
