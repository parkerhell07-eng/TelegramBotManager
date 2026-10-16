package com.telegrambot.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telegrambot.manager.ui.components.LabeledField
import com.telegrambot.manager.ui.components.PrimaryAction
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.vm.BotsViewModel

@Composable
fun AddBotScreen(
    vm: BotsViewModel,
    onDone: (String) -> Unit,
    onBack: () -> Unit
) {
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    var token by remember { mutableStateOf("") }

    AppScaffold(title = "افزودن ربات", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionCard(title = "توکن ربات") {
                Text(
                    "در تلگرام به @BotFather پیام /newbot بدهید، ربات را بسازید و توکن دریافتی را اینجا وارد کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LabeledField(
                    value = token,
                    onValueChange = { token = it },
                    label = "مثال: 123456789:AAHfiqksKZ8WbR5P..."
                )
                Text(
                    "توکن‌ها به‌صورت رمزنگاری‌شده (AES) فقط روی همین گوشی ذخیره می‌شوند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (message != null) {
                StatusMessage(message ?: "", isError = true)
            }

            PrimaryAction(
                text = "بررسی و افزودن ربات",
                loading = busy,
                onClick = { vm.addBot(token) { onDone(it.id) } }
            )
        }
    }
}
