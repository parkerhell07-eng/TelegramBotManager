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
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.TgChatMember
import com.telegrambot.manager.data.store.ChatStore
import com.telegrambot.manager.ui.components.AppScaffold
import com.telegrambot.manager.ui.components.BotAvatar
import com.telegrambot.manager.ui.components.ChatIdPicker
import com.telegrambot.manager.ui.components.LabeledField
import com.telegrambot.manager.ui.components.PrimaryAction
import com.telegrambot.manager.ui.components.SectionCard
import com.telegrambot.manager.ui.components.StatusMessage
import com.telegrambot.manager.vm.BotVmFactory
import kotlinx.coroutines.launch

class MembersViewModel(private val bot: Bot, private val chatStore: ChatStore) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var result by mutableStateOf<String?>(null)
        private set
    var isError by mutableStateOf(false)
        private set
    var memberCount by mutableStateOf<Int?>(null)
        private set
    var admins by mutableStateOf(listOf<TgChatMember>())
        private set

    fun knownChats() = chatStore.load(bot.id).threads.sortedByDescending { it.lastDate }

    private fun ok(msg: String) {
        result = msg; isError = false
    }

    private fun fail(msg: String) {
        result = "خطا: $msg"; isError = true
    }

    fun loadChatInfo(chatIdRaw: String) {
        val chatId = chatIdRaw.trim()
        if (chatId.isEmpty()) return
        viewModelScope.launch {
            loading = true
            result = null
            when (val c = TelegramRepo.getChatMemberCount(bot.token, chatId)) {
                is TelegramRepo.ApiResult.Success -> memberCount = c.data
                is TelegramRepo.ApiResult.Failure -> {
                    memberCount = null
                    fail(c.message)
                }
            }
            when (val a = TelegramRepo.getChatAdministrators(bot.token, chatId)) {
                is TelegramRepo.ApiResult.Success -> admins = a.data
                is TelegramRepo.ApiResult.Failure -> admins = emptyList()
            }
            loading = false
        }
    }

    fun ban(chatIdRaw: String, userIdRaw: String, revoke: Boolean) {
        val chatId = chatIdRaw.trim()
        val userId = userIdRaw.trim().toLongOrNull()
        if (chatId.isEmpty() || userId == null) {
            fail("chat_id و user_id معتبر وارد کنید")
            return
        }
        viewModelScope.launch {
            loading = true
            when (val r = TelegramRepo.banChatMember(bot.token, chatId, userId, revoke)) {
                is TelegramRepo.ApiResult.Success -> ok("کاربر بن شد ✓")
                is TelegramRepo.ApiResult.Failure -> fail(r.message)
            }
            loading = false
        }
    }

    fun unban(chatIdRaw: String, userIdRaw: String) {
        val chatId = chatIdRaw.trim()
        val userId = userIdRaw.trim().toLongOrNull()
        if (chatId.isEmpty() || userId == null) {
            fail("chat_id و user_id معتبر وارد کنید")
            return
        }
        viewModelScope.launch {
            loading = true
            when (val r = TelegramRepo.unbanChatMember(bot.token, chatId, userId, null)) {
                is TelegramRepo.ApiResult.Success -> ok("آنبن انجام شد ✓")
                is TelegramRepo.ApiResult.Failure -> fail(r.message)
            }
            loading = false
        }
    }

    fun promote(
        chatIdRaw: String, userIdRaw: String,
        changeInfo: Boolean, deleteMsgs: Boolean, invite: Boolean,
        restrict: Boolean, pin: Boolean, promoteOthers: Boolean
    ) {
        val chatId = chatIdRaw.trim()
        val userId = userIdRaw.trim().toLongOrNull()
        if (chatId.isEmpty() || userId == null) {
            fail("chat_id و user_id معتبر وارد کنید")
            return
        }
        viewModelScope.launch {
            loading = true
            when (
                val r = TelegramRepo.promoteChatMember(
                    bot.token, chatId, userId,
                    changeInfo, deleteMsgs, invite, restrict, pin, promoteOthers
                )
            ) {
                is TelegramRepo.ApiResult.Success -> ok("کاربر ادمین شد ✓")
                is TelegramRepo.ApiResult.Failure -> fail(r.message)
            }
            loading = false
        }
    }

    fun pin(chatIdRaw: String, messageIdRaw: String, silent: Boolean) {
        val chatId = chatIdRaw.trim()
        val messageId = messageIdRaw.trim().toLongOrNull()
        if (chatId.isEmpty() || messageId == null) {
            fail("chat_id و message_id معتبر وارد کنید")
            return
        }
        viewModelScope.launch {
            loading = true
            when (val r = TelegramRepo.pinChatMessage(bot.token, chatId, messageId, silent)) {
                is TelegramRepo.ApiResult.Success -> ok("پیام سنجاق شد ✓")
                is TelegramRepo.ApiResult.Failure -> fail(r.message)
            }
            loading = false
        }
    }

    private inline fun check(chatIdRaw: String, userIdRaw: String): Pair<String, Long>? {
        val chatId = chatIdRaw.trim()
        val userId = userIdRaw.trim().toLongOrNull()
        if (chatId.isEmpty() || userId == null) {
            fail("chat_id و user_id معتبر وارد کنید")
            return null
        }
        return chatId to userId
    }
}

private fun statusFa(status: String?): String = when (status) {
    "creator" -> "سازنده"
    "administrator" -> "ادمین"
    "member" -> "عضو"
    "restricted" -> "محدودشده"
    "left" -> "ترک‌کرده"
    "kicked" -> "بن‌شده"
    else -> status ?: "نامشخص"
}

private fun memberName(m: TgChatMember): String =
    listOfNotNull(m.user?.firstName, m.user?.lastName).joinToString(" ")
        .ifBlank { m.user?.username?.let { "@$it" } ?: m.user?.id?.toString() ?: "؟" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(bot: Bot, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: MembersViewModel = viewModel(
        factory = BotVmFactory { MembersViewModel(bot, ChatStore.get(context)) }
    )
    val knownChats = remember { vm.knownChats() }

    var chatId by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var messageId by remember { mutableStateOf("") }

    var revoke by remember { mutableStateOf(true) }
    var pinSilent by remember { mutableStateOf(false) }

    var pChangeInfo by remember { mutableStateOf(false) }
    var pDeleteMsgs by remember { mutableStateOf(true) }
    var pInvite by remember { mutableStateOf(false) }
    var pRestrict by remember { mutableStateOf(false) }
    var pPin by remember { mutableStateOf(false) }
    var pPromote by remember { mutableStateOf(false) }

    AppScaffold(
        title = "مدیریت اعضا",
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

            SectionCard(title = "گروه یا کانال هدف") {
                ChatIdPicker(
                    threads = knownChats,
                    value = chatId,
                    onValueChange = { chatId = it },
                    label = "chat_id گروه/کانال"
                )
                OutlinedButton(
                    onClick = { vm.loadChatInfo(chatId) },
                    enabled = chatId.isNotBlank() && !vm.loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("نمایش ادمین‌ها و تعداد اعضا") }

                vm.memberCount?.let {
                    Text(
                        "تعداد اعضا: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                vm.admins.forEach { m ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(10.dp)
                        ) {
                            BotAvatar(
                                telegramId = m.user?.id ?: 0,
                                name = memberName(m),
                                size = 36.dp,
                                letterSize = 15.sp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    memberName(m),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    listOfNotNull(
                                        statusFa(m.status),
                                        m.customTitle?.let { "($it)" },
                                        if (m.isAnonymous == true) "ناشناس" else null
                                    ).joinToString(" "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            SectionCard(title = "کاربر") {
                LabeledField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = "user_id کاربر"
                )
            }

            SectionCard(title = "بن / آنبن") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "حذف پیام‌های کاربر هنگام بن",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = revoke, onCheckedChange = { revoke = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { vm.ban(chatId, userId, revoke) },
                        enabled = chatId.isNotBlank() && userId.isNotBlank() && !vm.loading,
                        modifier = Modifier.weight(1f)
                    ) { Text("بن") }
                    OutlinedButton(
                        onClick = { vm.unban(chatId, userId) },
                        enabled = chatId.isNotBlank() && userId.isNotBlank() && !vm.loading,
                        modifier = Modifier.weight(1f)
                    ) { Text("آنبن") }
                }
            }

            SectionCard(title = "ارتقا به ادمین") {
                PermCheck("تغییر اطلاعات چت", pChangeInfo) { pChangeInfo = it }
                PermCheck("حذف پیام‌ها", pDeleteMsgs) { pDeleteMsgs = it }
                PermCheck("دعوت کاربران", pInvite) { pInvite = it }
                PermCheck("محدودکردن اعضا", pRestrict) { pRestrict = it }
                PermCheck("سنجاق پیام", pPin) { pPin = it }
                PermCheck("ارتقای بقیه", pPromote) { pPromote = it }
                PrimaryAction(
                    text = "ارتقای کاربر به ادمین",
                    enabled = chatId.isNotBlank() && userId.isNotBlank() && !vm.loading,
                    loading = vm.loading,
                    onClick = {
                        vm.promote(
                            chatId, userId,
                            pChangeInfo, pDeleteMsgs, pInvite, pRestrict, pPin, pPromote
                        )
                    }
                )
            }

            SectionCard(title = "سنجاق پیام") {
                LabeledField(
                    value = messageId,
                    onValueChange = { messageId = it },
                    label = "message_id پیام (از «پیام‌های دریافتی»)"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "سنجاق بی‌صدا",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = pinSilent, onCheckedChange = { pinSilent = it })
                }
                PrimaryAction(
                    text = "سنجاق کن",
                    enabled = chatId.isNotBlank() && messageId.isNotBlank() && !vm.loading,
                    loading = vm.loading,
                    onClick = { vm.pin(chatId, messageId, pinSilent) }
                )
            }
        }
    }
}

@Composable
private fun PermCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = checked, onCheckedChange = onChange)
    }
}
