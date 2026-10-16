package com.telegrambot.manager.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.telegrambot.manager.ui.components.LoadingBox
import com.telegrambot.manager.ui.screens.AddBotScreen
import com.telegrambot.manager.ui.screens.BotsListScreen
import com.telegrambot.manager.ui.screens.BroadcastScreen
import com.telegrambot.manager.ui.screens.ChatThreadScreen
import com.telegrambot.manager.ui.screens.ChatsScreen
import com.telegrambot.manager.ui.screens.CommandsScreen
import com.telegrambot.manager.ui.screens.DashboardScreen
import com.telegrambot.manager.ui.screens.MediaScreen
import com.telegrambot.manager.ui.screens.MessageScreen
import com.telegrambot.manager.ui.screens.ProfileScreen
import com.telegrambot.manager.ui.screens.UpdatesScreen
import com.telegrambot.manager.ui.screens.WebhookScreen
import com.telegrambot.manager.vm.BotsViewModel
import com.telegrambot.manager.vm.BotsViewModelFactory

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val vm: BotsViewModel = viewModel(factory = BotsViewModelFactory(context))

    NavHost(
        navController = navController,
        startDestination = "bots",
        enterTransition = {
            slideInHorizontally(tween(260)) { it } + fadeIn(tween(260))
        },
        exitTransition = {
            slideOutHorizontally(tween(220)) { -it / 3 } + fadeOut(tween(220))
        },
        popEnterTransition = {
            slideInHorizontally(tween(260)) { -it / 3 } + fadeIn(tween(260))
        },
        popExitTransition = {
            slideOutHorizontally(tween(220)) { it } + fadeOut(tween(220))
        }
    ) {

        composable("bots") {
            BotsListScreen(
                vm = vm,
                onOpenBot = { id -> navController.navigate("bot/$id") },
                onAddBot = { navController.navigate("addBot") }
            )
        }

        composable("addBot") {
            AddBotScreen(
                vm = vm,
                onDone = { id ->
                    navController.navigate("bot/$id") {
                        popUpTo("bots")
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("bot/{botId}") { entry ->
            val botId = entry.arguments?.getString("botId") ?: ""
            val bot = vm.botById(botId)
            if (bot == null) {
                LoadingBox()
            } else {
                DashboardScreen(
                    bot = bot,
                    botsVm = vm,
                    onBack = { navController.popBackStack() },
                    onOpen = { suffix -> navController.navigate("bot/$botId/$suffix") }
                )
            }
        }

        composable("bot/{botId}/chats") { entry ->
            val botId = entry.arguments?.getString("botId") ?: ""
            val bot = vm.botById(botId)
            if (bot == null) LoadingBox() else ChatsScreen(
                bot = bot,
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId -> navController.navigate("bot/$botId/chat/$chatId") }
            )
        }

        composable("bot/{botId}/chat/{chatId}") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            val chatId = entry.arguments?.getString("chatId")?.toLongOrNull() ?: 0L
            if (bot == null) LoadingBox() else ChatThreadScreen(
                bot = bot,
                chatId = chatId,
                onBack = { navController.popBackStack() }
            )
        }

        composable("bot/{botId}/broadcast") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else BroadcastScreen(
                bot = bot,
                onBack = { navController.popBackStack() }
            )
        }

        composable("bot/{botId}/message") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else MessageScreen(bot = bot, onBack = { navController.popBackStack() })
        }

        composable("bot/{botId}/media") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else MediaScreen(bot = bot, onBack = { navController.popBackStack() })
        }

        composable("bot/{botId}/commands") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else CommandsScreen(bot = bot, onBack = { navController.popBackStack() })
        }

        composable("bot/{botId}/webhook") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else WebhookScreen(bot = bot, onBack = { navController.popBackStack() })
        }

        composable("bot/{botId}/updates") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else UpdatesScreen(bot = bot, onBack = { navController.popBackStack() })
        }

        composable("bot/{botId}/profile") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else ProfileScreen(bot = bot, onBack = { navController.popBackStack() })
        }
    }
}
