package com.telegrambot.manager.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MediaViewModel(private val bot: Bot) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var result by mutableStateOf<String?>(null)
        private set
    var isError by mutableStateOf(false)
        private set

    private fun ok(msg: String) {
        result = msg
        isError = false
    }

    private fun fail(msg: String) {
        result = "خطا: $msg"
        isError = true
    }

    fun sendPhotoUrl(chatId: String, photo: String, caption: String?) {
        viewModelScope.launch {
            busy = true
            when (val r = TelegramRepo.sendPhotoUrl(bot.token, chatId, photo, caption, null)) {
                is TelegramRepo.ApiResult.Success -> ok("عکس ارسال شد ✓")
                is TelegramRepo.ApiResult.Failure -> fail(r.message)
            }
            busy = false
        }
    }

    fun sendPhotoFile(chatId: String, uri: Uri, context: Context, caption: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    fail("خواندن فایل ناموفق بود")
                    busy = false
                    return@launch
                }
                val name = fileName(context, uri) ?: "photo.jpg"
                when (val r = TelegramRepo.sendPhotoFile(bot.token, chatId, bytes, name, caption)) {
                    is TelegramRepo.ApiResult.Success -> ok("عکس ارسال شد ✓")
                    is TelegramRepo.ApiResult.Failure -> fail(r.message)
                }
            } catch (e: Exception) {
                fail(e.message ?: "خطای نامشخص")
            }
            busy = false
        }
    }

    fun sendDocumentUrl(chatId: String, docUrl: String, caption: String?) {
        viewModelScope.launch {
            busy = true
            when (val r = TelegramRepo.sendDocumentUrl(bot.token, chatId, docUrl, caption, null)) {
                is TelegramRepo.ApiResult.Success -> ok("فایل ارسال شد ✓")
                is TelegramRepo.ApiResult.Failure -> fail(r.message)
            }
            busy = false
        }
    }

    fun sendDocumentFile(chatId: String, uri: Uri, context: Context, caption: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    fail("خواندن فایل ناموفق بود")
                    busy = false
                    return@launch
                }
                val name = fileName(context, uri) ?: "file.bin"
                when (val r = TelegramRepo.sendDocumentFile(bot.token, chatId, bytes, name, caption)) {
                    is TelegramRepo.ApiResult.Success -> ok("فایل ارسال شد ✓")
                    is TelegramRepo.ApiResult.Failure -> fail(r.message)
                }
            } catch (e: Exception) {
                fail(e.message ?: "خطای نامشخص")
            }
            busy = false
        }
    }

    fun sendLocation(chatId: String, lat: String, lng: String) {
        val la = lat.trim().toDoubleOrNull()
        val lo = lng.trim().toDoubleOrNull()
        if (la == null || lo == null) {
            fail("عرض و طول جغرافیایی باید عدد باشند")
            return
        }
        viewModelScope.launch {
            busy = true
            when (val r = TelegramRepo.sendLocation(bot.token, chatId, la, lo)) {
                is TelegramRepo.ApiResult.Success -> ok("موقعیت مکانی ارسال شد ✓")
                is TelegramRepo.ApiResult.Failure -> fail(r.message)
            }
            busy = false
        }
    }

    private fun fileName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
}

@Composable
fun MediaScreen(bot: Bot, onBack: () -> Unit) {
    val vm: MediaViewModel = viewModel(factory = BotVmFactory { MediaViewModel(bot) })
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("عکس", "فایل", "موقعیت مکانی")

    AppScaffold(title = "ارسال مدیا", subtitle = bot.name, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (tab) {
                    0 -> PhotoTab(vm)
                    1 -> FileTab(vm)
                    else -> LocationTab(vm)
                }
                if (vm.result != null) StatusMessage(vm.result ?: "", vm.isError)
            }
        }
    }
}

@Composable
private fun PhotoTab(vm: MediaViewModel) {
    val context = LocalContext.current
    var chatId by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        picked = uri
    }

    SectionCard(title = "ارسال عکس") {
        LabeledField(chatId, { chatId = it }, "شناسه چت (chat_id یا @نام‌کانال)")
        LabeledField(url, { url = it; picked = null }, "لینک مستقیم عکس (https://...)")
        Text("یا انتخاب عکس از حافظه گوشی:", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { launcher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Image, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (picked != null) "عکس انتخاب شد ✓ (برای تغییر بزنید)" else "انتخاب عکس از گوشی")
        }
        LabeledField(caption, { caption = it }, "کپشن (اختیاری)", singleLine = false, minLines = 2)
        PrimaryAction(
            text = "ارسال عکس",
            enabled = chatId.isNotBlank() && (url.isNotBlank() || picked != null),
            loading = vm.busy,
            onClick = {
                val u = picked
                if (u != null) {
                    vm.sendPhotoFile(chatId.trim(), u, context, caption.ifBlank { null })
                } else {
                    vm.sendPhotoUrl(chatId.trim(), url.trim(), caption.ifBlank { null })
                }
            }
        )
    }
}

@Composable
private fun FileTab(vm: MediaViewModel) {
    val context = LocalContext.current
    var chatId by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        picked = uri
    }

    SectionCard(title = "ارسال فایل") {
        LabeledField(chatId, { chatId = it }, "شناسه چت (chat_id یا @نام‌کانال)")
        LabeledField(url, { url = it; picked = null }, "لینک مستقیم فایل (https://...)")
        Text("یا انتخاب فایل از حافظه گوشی:", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { launcher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.AttachFile, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (picked != null) "فایل انتخاب شد ✓ (برای تغییر بزنید)" else "انتخاب فایل از گوشی")
        }
        LabeledField(caption, { caption = it }, "کپشن (اختیاری)", singleLine = false, minLines = 2)
        PrimaryAction(
            text = "ارسال فایل",
            enabled = chatId.isNotBlank() && (url.isNotBlank() || picked != null),
            loading = vm.busy,
            onClick = {
                val u = picked
                if (u != null) {
                    vm.sendDocumentFile(chatId.trim(), u, context, caption.ifBlank { null })
                } else {
                    vm.sendDocumentUrl(chatId.trim(), url.trim(), caption.ifBlank { null })
                }
            }
        )
    }
}

@Composable
private fun LocationTab(vm: MediaViewModel) {
    var chatId by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }

    SectionCard(title = "ارسال موقعیت مکانی") {
        LabeledField(chatId, { chatId = it }, "شناسه چت (chat_id یا @نام‌کانال)")
        LabeledField(
            lat, { lat = it },
            "عرض جغرافیایی (مثل: 35.6892)",
            keyboardType = KeyboardType.Decimal
        )
        LabeledField(
            lng, { lng = it },
            "طول جغرافیایی (مثل: 51.3890)",
            keyboardType = KeyboardType.Decimal
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(
                "روی نقشه (مثل Google Maps) روی مکان موردنظر نگه دارید تا مختصات نمایش داده شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PrimaryAction(
            text = "ارسال موقعیت",
            enabled = chatId.isNotBlank() && lat.isNotBlank() && lng.isNotBlank(),
            loading = vm.busy,
            onClick = { vm.sendLocation(chatId.trim(), lat, lng) }
        )
    }
}
