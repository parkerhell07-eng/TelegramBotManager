package com.telegrambot.manager.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.telegrambot.manager.data.api.Scheduler
import com.telegrambot.manager.data.api.TelegramRepo
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.model.ScheduledMessage
import com.telegrambot.manager.data.store.BotStore
import com.telegrambot.manager.data.store.ChatStore
import com.telegrambot.manager.data.store.ScheduleStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Shared, activity-scoped ViewModel managing the list of saved bots. */
class BotsViewModel(
    private val store: BotStore,
    private val appContext: Context
) : ViewModel() {

    private val _bots = MutableStateFlow<List<Bot>>(emptyList())
    val bots: StateFlow<List<Bot>> = _bots.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        _bots.value = store.loadBots()
    }

    fun botById(id: String): Bot? = _bots.value.firstOrNull { it.id == id }

    fun addBot(tokenRaw: String, onSuccess: (Bot) -> Unit) {
        val token = tokenRaw.trim()
        if (token.isEmpty()) {
            _message.value = "توکن را وارد کنید"
            return
        }
        if (!token.matches(Regex("^\\d+:[A-Za-z0-9_-]{30,}$"))) {
            _message.value = "قالب توکن معتبر نیست (مثال: 123456789:AAHfiqks...)"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            when (val r = TelegramRepo.getMe(token)) {
                is TelegramRepo.ApiResult.Success -> {
                    val user = r.data
                    if (_bots.value.any { it.telegramId == user.id }) {
                        _message.value = "این ربات قبلاً اضافه شده است"
                    } else {
                        val bot = Bot(
                            id = UUID.randomUUID().toString(),
                            token = token,
                            name = user.firstName ?: "Bot",
                            username = user.username ?: "",
                            telegramId = user.id,
                            addedAt = System.currentTimeMillis()
                        )
                        _bots.value = _bots.value + bot
                        store.saveBots(_bots.value)
                        _message.value = null
                        onSuccess(bot)
                    }
                }
                is TelegramRepo.ApiResult.Failure -> {
                    _message.value =
                        if (r.code == 401) "توکن نامعتبر است (کد 401)" else "خطا: ${r.message}"
                }
            }
            _busy.value = false
        }
    }

    fun deleteBot(id: String) {
        // Cancel pending scheduled sends and clear this bot's local data.
        val scheduleStore = ScheduleStore.get(appContext)
        scheduleStore.all()
            .filter {
                it.botId == id && it.status == ScheduledMessage.STATUS_PENDING
            }
            .forEach { Scheduler.cancelJob(appContext, it.id) }
        scheduleStore.removeBot(id)
        ChatStore.get(appContext).clear(id)
        _bots.value = _bots.value.filterNot { it.id == id }
        store.saveBots(_bots.value)
    }

    fun clearMessage() {
        _message.value = null
    }
}

/** Factory for [BotsViewModel] (needs a Context to build the encrypted store). */
class BotsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        BotsViewModel(BotStore.get(context), context.applicationContext) as T
}

/** Generic factory for per-bot, per-screen ViewModels. */
class BotVmFactory(private val creator: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}
