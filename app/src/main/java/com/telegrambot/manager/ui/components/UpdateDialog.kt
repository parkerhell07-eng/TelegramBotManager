package com.telegrambot.manager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telegrambot.manager.data.update.UpdateChecker
import com.telegrambot.manager.data.util.st

/**
 * "New version available" dialog. [busy] shows a progress bar while the APK
 * downloads; [note] surfaces transient states (errors / install hints).
 */
@Composable
fun UpdateOfferDialog(
    info: UpdateChecker.UpdateInfo,
    busy: Boolean,
    note: String?,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onInstall: () -> Unit
) {
    val sizeMb = if (info.sizeBytes > 0) "%.1f MB".format(info.sizeBytes / 1024f / 1024f) else ""
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(st("نسخه جدید: v", "New version: v") + info.version) },
        text = {
            Column {
                if (sizeMb.isNotEmpty()) {
                    Text(
                        st("حجم دانلود: ", "Download size: ") + sizeMb,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (info.notes.isNotBlank()) {
                    Text(
                        info.notes.take(1200),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        st(
                            "در حال دانلود… پس از اتمام، صفحه نصب باز می‌شود. اگر باز نشد، فایل را از پوشه Downloads نصب کنید.",
                            "Downloading… the installer opens when done. If it doesn't, install the APK from Downloads."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (note != null) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onInstall, enabled = !busy) {
                Text(st("دانلود و نصب", "Download & install"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(st("بعداً", "Later"))
            }
        }
    )
}
