package com.telegrambot.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.telegrambot.manager.ui.AppNavHost
import com.telegrambot.manager.ui.theme.TelegramBotManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TelegramBotManagerTheme {
                AppNavHost()
            }
        }
    }
}
