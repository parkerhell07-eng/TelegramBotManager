package com.telegrambot.manager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telegrambot.manager.data.model.ChatThread

/**
 * Shared "target chat" picker: quick chips of known chats + a manual
 * chat_id field. Used by MembersScreen and PollScreen.
 */
@Composable
fun ChatIdPicker(
    threads: List<ChatThread>,
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    LabeledField(
        value = value,
        onValueChange = onValueChange,
        label = label
    )
    if (threads.isNotEmpty()) {
        Text(
            "چت‌های شناخته‌شده (لمس کنید):",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            threads.take(12).forEach { t ->
                FilterChip(
                    selected = value == t.chatId.toString(),
                    onClick = { onValueChange(t.chatId.toString()) },
                    label = {
                        Text(t.title, maxLines = 1, modifier = Modifier.padding(0.dp))
                    }
                )
            }
        }
    }
}
