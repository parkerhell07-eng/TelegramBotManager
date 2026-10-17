package com.telegrambot.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.telegrambot.manager.data.api.Scheduler
import com.telegrambot.manager.ui.AppNavHost
import com.telegrambot.manager.ui.theme.TelegramBotManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Force-stop / OEM battery savers can drop WorkManager jobs;
        // re-enqueue every still-pending scheduled message on each launch.
        Scheduler.reschedulePending(applicationContext)
        setContent {
            TelegramBotManagerTheme {
                AppNavHost()
            }
        }
    }
}
