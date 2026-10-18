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
import com.telegrambot.manager.ui.screens.AutoReplyScreen
import com.telegrambot.manager.ui.screens.AssistantScreen
import com.telegrambot.manager.ui.screens.BotsListScreen
import com.telegrambot.manager.ui.screens.BroadcastScreen
import com.telegrambot.manager.ui.screens.ChatThreadScreen
import com.telegrambot.manager.ui.screens.ChatsScreen
import com.telegrambot.manager.ui.screens.AlwaysOnScreen
import com.telegrambot.manager.ui.screens.CloudScreen
import com.telegrambot.manager.ui.screens.CommandsScreen
import com.telegrambot.manager.ui.screens.DashboardScreen
import com.telegrambot.manager.ui.screens.MediaScreen
import com.telegrambot.manager.ui.screens.MembersScreen
import com.telegrambot.manager.ui.screens.MessageScreen
import com.telegrambot.manager.ui.screens.PollScreen
import com.telegrambot.manager.ui.screens.ProfileScreen
import com.telegrambot.manager.ui.screens.ScheduleScreen
import com.telegrambot.manager.ui.screens.SettingsScreen
import com.telegrambot.manager.ui.screens.StatsScreen
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
                onAddBot = { navController.navigate("addBot") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }

        composable("settings") {
            SettingsScreen(
                botsVm = vm,
                onBack = { navController.popBackStack() }
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

        composable("bot/{botId}/members") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else MembersScreen(
                bot = bot,
                onBack = { navController.popBackStack() }
            )
        }

        composable("bot/{botId}/poll") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else PollScreen(
                bot = bot,
                onBack = { navController.popBackStack() }
            )
        }

        composable("bot/{botId}/assistant") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else AssistantScreen(
                bot = bot,
                onBack = { navController.popBackStack() }
            )
        }

        composable("bot/{botId}/schedule") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else ScheduleScreen(
                bot = bot,
                onBack = { navController.popBackStack() }
            )
        }

        composable("bot/{botId}/stats") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else StatsScreen(
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

        composable("bot/{botId}/autoreply") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else AutoReplyScreen(bot = bot, onBack = { navController.popBackStack() })
        }

        composable("bot/{botId}/alwayson") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else AlwaysOnScreen(
                bot = bot,
                onBack = { navController.popBackStack() },
                onOpenRules = { navController.navigate("bot/${bot.id}/autoreply") }
            )
        }

        composable("bot/{botId}/cloud") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else CloudScreen(
                bot = bot,
                onBack = { navController.popBackStack() }
            )
        }

        composable("bot/{botId}/profile") { entry ->
            val bot = vm.botById(entry.arguments?.getString("botId") ?: "")
            if (bot == null) LoadingBox() else ProfileScreen(bot = bot, onBack = { navController.popBackStack() })
        }
    }
}
