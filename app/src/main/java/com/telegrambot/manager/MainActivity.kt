package com.telegrambot.manager

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.telegrambot.manager.data.api.Scheduler
import com.telegrambot.manager.data.store.AutoReplyStore
import com.telegrambot.manager.data.store.SettingsStore
import com.telegrambot.manager.data.util.L10n
import com.telegrambot.manager.ui.AppNavHost
import com.telegrambot.manager.ui.theme.TelegramBotManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Force-stop / OEM battery savers can drop WorkManager jobs;
        // re-enqueue every still-pending scheduled message on each launch.
        Scheduler.reschedulePending(applicationContext)

        // Sync the notification/engine language with the saved preference.
        L10n.set(SettingsStore.get(applicationContext).lang.value)

        // Bring the 24/7 answer engine back up if any bot has auto-reply on
        // (the service is START_STICKY, but OEM task killers are aggressive).
        // Cloud-served bots are excluded: they answer from Cloudflare and
        // must not be long-polled from the phone.
        val autoStore = AutoReplyStore.get(applicationContext)
        val cloudStore = com.telegrambot.manager.data.store.CloudStore.get(applicationContext)
        val phoneBotActive = autoStore.enabledBotIds().any { !cloudStore.load(it).enabled }
        if (phoneBotActive) {
            EngineControl.start(applicationContext)
        }

        setContent {
            TelegramBotManagerTheme {
                AppNavHost()
            }
        }

        requestNotificationPermissionIfNeeded()
    }

    /** Android 13+ needs a runtime grant for the engine's persistent notification. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
