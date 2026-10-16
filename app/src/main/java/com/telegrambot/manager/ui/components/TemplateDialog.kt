package com.telegrambot.manager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telegrambot.manager.data.model.MsgTemplate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Replaces template variables with per-recipient values.
 * Supported: {first_name} {last_name} {date} {time}.
 * [recipientName] may be null (single send where the name is unknown).
 */
fun applyTemplateVars(body: String, recipientName: String?): String {
    val parts = (recipientName?.trim() ?: "").split(" ").filter { it.isNotBlank() }
    val first = parts.firstOrNull() ?: ""
    val last = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""
    val now = Calendar.getInstance()
    val date = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(now.time)
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
    return body
        .replace("{first_name}", first)
        .replace("{last_name}", last)
        .replace("{date}", date)
        .replace("{time}", time)
}

/**
 * Pick / create / delete message templates. Used by MessageScreen and
 * BroadcastScreen; the picked template body comes back through [onPick].
 */
@Composable
fun TemplateManagerDialog(
    templates: List<MsgTemplate>,
    onPick: (MsgTemplate) -> Unit,
    onDelete: (MsgTemplate) -> Unit,
    onAdd: (title: String, body: String) -> Unit,
    onDismiss: () -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (adding) "قالب جدید" else "قالب‌های پیام") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (adding) {
                    LabeledField(value = title, onValueChange = { title = it }, label = "عنوان قالب")
                    LabeledField(
                        value = body,
                        onValueChange = { body = it },
                        label = "متن قالب",
                        singleLine = false,
                        minLines = 3
                    )
                } else if (templates.isEmpty()) {
                    Text(
                        "هنوز قالبی ذخیره نشده است. یک قالب بسازید و همیشه سریع پیام بفرستید.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    templates.forEach { t ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable { onPick(t) }
                            ) {
                                Text(
                                    t.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    t.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onDelete(t) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "حذف قالب",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                Text(
                    "متغیرها: {first_name} {last_name} {date} {time} — هنگام ارسال جایگزین می‌شوند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (adding) {
                TextButton(
                    onClick = {
                        if (title.isNotBlank() && body.isNotBlank()) {
                            onAdd(title.trim(), body)
                            adding = false
                            title = ""
                            body = ""
                        }
                    }
                ) { Text("ذخیره قالب") }
            } else {
                TextButton(onClick = { adding = true }) { Text("قالب جدید") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (adding) adding = false else onDismiss() }) {
                Text(if (adding) "بازگشت" else "بستن")
            }
        }
    )
}
