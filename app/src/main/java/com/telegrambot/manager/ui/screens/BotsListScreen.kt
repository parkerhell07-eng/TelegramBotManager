package com.telegrambot.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegrambot.manager.data.model.Bot
import com.telegrambot.manager.data.store.SettingsStore
import com.telegrambot.manager.data.update.UpdateChecker
import com.telegrambot.manager.ui.components.UpdateOfferDialog
import com.telegrambot.manager.ui.components.BotAvatar
import com.telegrambot.manager.ui.components.PrimaryAction
import com.telegrambot.manager.ui.components.copyToClipboard
import com.telegrambot.manager.ui.theme.BlueGradient
import com.telegrambot.manager.ui.theme.TgBlue
import com.telegrambot.manager.vm.BotsViewModel
import kotlinx.coroutines.launch

@Composable
fun BotsListScreen(
    vm: BotsViewModel,
    onOpenBot: (String) -> Unit,
    onAddBot: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val bots by vm.bots.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // In-app update: a quiet check at most twice a day, skippable.
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val info = UpdateChecker.checkDue(context)
        if (info != null) updateInfo = info
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(BlueGradient)
                    .clickableShape(CircleShape) { onAddBot() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "افزودن ربات",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Hero header ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "ربات‌های من",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (bots.isEmpty()) "مدیریت همه ربات‌های تلگرام در یک‌جا"
                        else "${bots.size} ربات ذخیره‌شده روی این دستگاه",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "تنظیمات",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (bots.isEmpty()) {
                // ── Empty state ──────────────────────────────────────────
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(BlueGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.SmartToy,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "هنوز رباتی اضافه نکرده‌اید",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "توکن ربات را از @BotFather بگیرید\nو اینجا اضافه کنید تا شروع کنید.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(24.dp))
                        PrimaryAction(
                            text = "افزودن اولین ربات",
                            onClick = onAddBot,
                            modifier = Modifier.padding(horizontal = 40.dp)
                        )
                    }
                }
            } else {
                // ── Bot list ─────────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(bots, key = { it.id }) { bot ->
                        BotCard(
                            bot = bot,
                            onClick = { onOpenBot(bot.id) },
                            onCopyToken = { copyToClipboard(context, "token", bot.token) }
                        )
                    }
                }
            }
        }
    }

    updateInfo?.let { info ->
        UpdateOfferDialog(
            info = info,
            busy = updateBusy,
            note = updateNote,
            onDismiss = { if (!updateBusy) updateInfo = null },
            onSkip = {
                SettingsStore.get(context).setSkippedVersion(info.version)
                updateInfo = null
            },
            onInstall = {
                updateBusy = true
                scope.launch {
                    try {
                        val id = UpdateChecker.enqueueDownload(context, info)
                        UpdateChecker.awaitDownloadAndInstall(context, id, info)
                        updateNote = "اگر صفحه نصب باز نشد، فایل APK را از پوشه Downloads اجرا کنید."
                    } catch (e: Exception) {
                        updateNote = "خطا: ${e.message ?: "نامشخص"}"
                    }
                    updateBusy = false
                }
            }
        )
    }
}

/** Gradient-filled clickable box helper (used by the FAB). */
private fun Modifier.clickableShape(shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BotCard(bot: Bot, onClick: () -> Unit, onCopyToken: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BotAvatar(telegramId = bot.telegramId, name = bot.name, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(bot.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (bot.username.isNotEmpty()) "@${bot.username}" else "بدون نام کاربری",
                    style = MaterialTheme.typography.bodySmall,
                    color = TgBlue.copy(alpha = 0.9f)
                )
            }
            IconButton(onClick = onCopyToken) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "کپی توکن",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
