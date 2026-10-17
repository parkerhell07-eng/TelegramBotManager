package com.telegrambot.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.telegrambot.manager.data.util.Jalali
import com.telegrambot.manager.ui.theme.TgBlue

/**
 * Fully Persian (Jalali) calendar picker: month grid with Persian month
 * names, Persian digits, Saturday-first weekday header and leap-aware
 * اسفند length. Replaces the Gregorian Material3 DatePicker.
 *
 * [initialAt] seeds the visible month/selected day; [onPick] returns the
 * selected Jalali date.
 */
@Composable
fun JalaliDatePickerDialog(
    initialAt: Long,
    onPick: (jy: Int, jm: Int, jd: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember { Jalali.fromMillis(System.currentTimeMillis()) }
    val initial = remember { Jalali.fromMillis(initialAt) }

    var viewY by remember { mutableIntStateOf(initial.jy) }
    var viewM by remember { mutableIntStateOf(initial.jm) }
    var selected by remember { mutableStateOf<Jalali.JDate?>(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            TextButton(
                onClick = {
                    selected?.let { onPick(it.jy, it.jm, it.jd) }
                    onDismiss()
                },
                enabled = selected != null
            ) { Text("تأیید") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
        title = { Text("تاریخ ارسال (شمسی)") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // ── Month navigation ────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = {
                        if (viewM == 1) { viewM = 12; viewY-- } else viewM--
                    }) {
                        Icon(
                            Icons.Filled.KeyboardArrowRight,
                            contentDescription = "ماه قبل",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        Jalali.monthTitle(viewY, viewM),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (viewM == 12) { viewM = 1; viewY++ } else viewM++
                    }) {
                        Icon(
                            Icons.Filled.KeyboardArrowLeft,
                            contentDescription = "ماه بعد",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // ── Weekday header ──────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth()) {
                    Jalali.weekdays.forEach { w ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                w,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))

                // ── Day grid ────────────────────────────────────────────
                val len = Jalali.monthLength(viewY, viewM)
                val lead = Jalali.firstWeekday(viewY, viewM)
                val cells: List<Int?> = List(lead) { null } + (1..len).toList()
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            Box(
                                Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (day == null) {
                                    Spacer(Modifier.size(34.dp))
                                } else {
                                    val isSel = selected?.let {
                                        it.jy == viewY && it.jm == viewM && it.jd == day
                                    } ?: false
                                    val isToday = today.jy == viewY && today.jm == viewM && today.jd == day
                                    DayCell(
                                        label = Jalali.faNum(day),
                                        selected = isSel,
                                        isToday = isToday,
                                        onClick = {
                                            selected = Jalali.JDate(viewY, viewM, day)
                                        }
                                    )
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(4.dp))
                selected?.let { s ->
                    Text(
                        "انتخاب‌شده: ${Jalali.faNum(s.jd)} ${Jalali.months[s.jm - 1]} ${Jalali.faNum(s.jy)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TgBlue
                    )
                }
            }
        }
    )
}

@Composable
private fun DayCell(
    label: String,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                when {
                    selected -> TgBlue
                    isToday -> MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> androidx.compose.ui.graphics.Color.White
                isToday -> TgBlue
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
