package com.telegrambot.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

class ProfileViewModel(private val bot: Bot) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var isError by mutableStateOf(false)
        private set

    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var shortDescription by mutableStateOf("")

    init {
        load()
    }

    private fun setMsg(text: String, err: Boolean) {
        message = text
        isError = err
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            when (val r = TelegramRepo.getMyName(bot.token)) {
                is TelegramRepo.ApiResult.Success -> name = r.data.name ?: ""
                is TelegramRepo.ApiResult.Failure -> setMsg("خطا: ${r.message}", true)
            }
            when (val r = TelegramRepo.getMyDescription(bot.token)) {
                is TelegramRepo.ApiResult.Success -> description = r.data.description ?: ""
                else -> {}
            }
            when (val r = TelegramRepo.getMyShortDescription(bot.token)) {
                is TelegramRepo.ApiResult.Success -> shortDescription = r.data.shortDescription ?: ""
                else -> {}
            }
            loading = false
        }
    }

    fun saveName() {
        viewModelScope.launch {
            loading = true
            when (val r = TelegramRepo.setMyName(bot.token, name.trim())) {
                is TelegramRepo.ApiResult.Success -> setMsg("نام ربات ذخیره شد ✓", false)
                is TelegramRepo.ApiResult.Failure -> setMsg("خطا: ${r.message}", true)
            }
            loading = false
        }
    }

    fun saveDescription() {
        viewModelScope.launch {
            loading = true
            when (val r = TelegramRepo.setMyDescription(bot.token, description.trim())) {
                is TelegramRepo.ApiResult.Success -> setMsg("توضیحات ذخیره شد ✓", false)
                is TelegramRepo.ApiResult.Failure -> setMsg("خطا: ${r.message}", true)
            }
            loading = false
        }
    }

    fun saveShortDescription() {
        viewModelScope.launch {
            loading = true
            when (val r = TelegramRepo.setMyShortDescription(bot.token, shortDescription.trim())) {
                is TelegramRepo.ApiResult.Success -> setMsg("توضیح کوتاه ذخیره شد ✓", false)
                is TelegramRepo.ApiResult.Failure -> setMsg("خطا: ${r.message}", true)
            }
            loading = false
        }
    }
}

@Composable
fun ProfileScreen(bot: Bot, onBack: () -> Unit) {
    val vm: ProfileViewModel = viewModel(factory = BotVmFactory { ProfileViewModel(bot) })

    AppScaffold(title = "پروفایل ربات", subtitle = bot.name, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.message != null) StatusMessage(vm.message ?: "", vm.isError)

            SectionCard(title = "نام ربات") {
                Text(
                    "نامی که در پروفایل و لیست چت‌ها دیده می‌شود (حداکثر ۶۴ کاراکتر).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LabeledField(value = vm.name, onValueChange = { vm.name = it }, label = "نام ربات")
                PrimaryAction(
                    text = "ذخیره نام",
                    loading = vm.loading,
                    onClick = { vm.saveName() }
                )
            }

            SectionCard(title = "توضیحات (Description)") {
                Text(
                    "متنی که قبل از شروع گفتگو با ربات نمایش داده می‌شود (حداکثر ۵۱۲ کاراکتر).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LabeledField(
                    value = vm.description,
                    onValueChange = { vm.description = it },
                    label = "توضیحات",
                    singleLine = false,
                    minLines = 4
                )
                PrimaryAction(
                    text = "ذخیره توضیحات",
                    loading = vm.loading,
                    onClick = { vm.saveDescription() }
                )
            }

            SectionCard(title = "توضیح کوتاه (Short Description)") {
                Text(
                    "متنی که در پروفایل ربات و صفحه اشتراک‌گذاری نمایش داده می‌شود (حداکثر ۱۲۰ کاراکتر).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LabeledField(
                    value = vm.shortDescription,
                    onValueChange = { vm.shortDescription = it },
                    label = "توضیح کوتاه",
                    singleLine = false,
                    minLines = 2
                )
                PrimaryAction(
                    text = "ذخیره توضیح کوتاه",
                    loading = vm.loading,
                    onClick = { vm.saveShortDescription() }
                )
            }

            OutlinedButton(
                onClick = { vm.load() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !vm.loading
            ) {
                Text("بازخوانی از سرور")
            }
        }
    }
}
