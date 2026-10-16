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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

data class CommandRowUi(val id: Int, val command: String, val description: String)

class CommandsViewModel(private val bot: Bot) : ViewModel() {
    var rows by mutableStateOf(listOf<CommandRowUi>())
        private set
    var busy by mutableStateOf(false)
        private set
    var result by mutableStateOf<String?>(null)
        private set
    var isError by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set

    private var nextId = 1

    private fun newRow() = CommandRowUi(nextId++, "", "")

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            busy = true
            when (val r = TelegramRepo.getMyCommands(bot.token)) {
                is TelegramRepo.ApiResult.Success -> {
                    rows = r.data.map { CommandRowUi(nextId++, it.command, it.description) }
                    if (rows.isEmpty()) rows = listOf(newRow())
                    loaded = true
                }
                is TelegramRepo.ApiResult.Failure -> {
                    result = "خطا: ${r.message}"
                    isError = true
                    rows = listOf(newRow())
                }
            }
            busy = false
        }
    }

    fun update(id: Int, cmd: CommandRowUi) {
        rows = rows.map { if (it.id == id) cmd else it }
    }

    fun add() {
        rows = rows + newRow()
    }

    fun remove(id: Int) {
        rows = rows.filterNot { it.id == id }
        if (rows.isEmpty()) rows = listOf(newRow())
    }

    fun save() {
        val filled = rows.filter { it.command.isNotBlank() || it.description.isNotBlank() }
        if (filled.isEmpty()) {
            result = "حداقل یک دستور وارد کنید"
            isError = true
            return
        }
        val bad = filled.firstOrNull { !Regex("^[a-z0-9_]{1,32}$").matches(it.command.trim()) }
        if (bad != null) {
            result = "دستور «${bad.command}» نامعتبر است: فقط حروف کوچک انگلیسی، عدد و زیرخط (_) تا ۳۲ کاراکتر"
            isError = true
            return
        }
        viewModelScope.launch {
            busy = true
            val arr = JSONArray()
            filled.forEach {
                arr.put(
                    JSONObject()
                        .put("command", it.command.trim())
                        .put("description", it.description.trim())
                )
            }
            when (val r = TelegramRepo.setMyCommands(bot.token, arr.toString())) {
                is TelegramRepo.ApiResult.Success -> {
                    result = "منوی دستورات ذخیره شد ✓"
                    isError = false
                }
                is TelegramRepo.ApiResult.Failure -> {
                    result = "خطا: ${r.message}"
                    isError = true
                }
            }
            busy = false
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            busy = true
            when (val r = TelegramRepo.deleteMyCommands(bot.token)) {
                is TelegramRepo.ApiResult.Success -> {
                    rows = listOf(newRow())
                    result = "همه دستورات حذف شد ✓"
                    isError = false
                }
                is TelegramRepo.ApiResult.Failure -> {
                    result = "خطا: ${r.message}"
                    isError = true
                }
            }
            busy = false
        }
    }
}

@Composable
fun CommandsScreen(bot: Bot, onBack: () -> Unit) {
    val vm: CommandsViewModel = viewModel(factory = BotVmFactory { CommandsViewModel(bot) })

    AppScaffold(title = "دستورات ربات", subtitle = bot.name, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Text(
                "این دستورات در منوی ربات (دکمه منو) نمایش داده می‌شوند. مثال: start برای شروع.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.rows, key = { it.id }) { row ->
                    CommandEditor(
                        row = row,
                        onChange = { vm.update(row.id, it) },
                        onRemove = { vm.remove(row.id) }
                    )
                }
            }

            if (vm.result != null) StatusMessage(vm.result ?: "", vm.isError)

            OutlinedButton(onClick = { vm.add() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("افزودن دستور جدید")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryAction(
                    text = "ذخیره منو",
                    modifier = Modifier.weight(1f),
                    loading = vm.busy,
                    onClick = { vm.save() }
                )
                OutlinedButton(
                    onClick = { vm.deleteAll() },
                    modifier = Modifier.weight(0.6f),
                    enabled = !vm.busy
                ) {
                    Text("حذف همه", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun CommandEditor(row: CommandRowUi, onChange: (CommandRowUi) -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "دستور ${row.id}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "حذف", modifier = Modifier.size(18.dp))
                }
            }
            LabeledField(
                value = row.command,
                onValueChange = { onChange(row.copy(command = it)) },
                label = "فرمان (بدون /) — مثل: start"
            )
            LabeledField(
                value = row.description,
                onValueChange = { onChange(row.copy(description = it)) },
                label = "توضیح دستور"
            )
        }
    }
}
