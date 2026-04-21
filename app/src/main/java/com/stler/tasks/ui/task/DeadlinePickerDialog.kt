package com.stler.tasks.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stler.tasks.domain.model.RecurType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Deadline picker dialog opened by the clock-button on a task row.
 *
 * Layout:
 *   Row 1 — [Date chip] [Time chip]
 *   Row 2 — "Repeat" toggle; when on → RecurRow (Every N D/W/M)
 *   ─────────────────────
 *   [No date] [Postpone?]          [Cancel] [Save]
 *
 * "No date" resets everything (date, time, recurring).
 * "Postpone" shifts selectedDate +1 day (only shown when a date is selected).
 * "Save" confirms with all current values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlinePickerDialog(
    initialDate       : String,
    initialTime       : String,
    initialIsRecurring: Boolean   = false,
    initialRecurType  : RecurType = RecurType.DAYS,
    initialRecurValue : Int       = 1,
    onConfirm: (date: String, time: String, isRecurring: Boolean, recurType: RecurType, recurValue: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedDate by remember { mutableStateOf(initialDate) }
    var selectedTime by remember { mutableStateOf(initialTime) }
    var isRecurring  by remember { mutableStateOf(initialIsRecurring) }
    // Default to DAYS when existing value is NONE (only NONE means "no recur selected in dialog")
    var recurType    by remember {
        mutableStateOf(if (initialRecurType == RecurType.NONE) RecurType.DAYS else initialRecurType)
    }
    var recurValue   by remember { mutableStateOf(initialRecurValue.coerceAtLeast(1).toString()) }

    var showCalendar   by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    /** Format "YYYY-MM-DD" → "d MMM" (e.g. "18 Apr"). */
    fun formatDate(d: String): String =
        runCatching {
            LocalDate.parse(d).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        }.getOrDefault(d)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set deadline") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ── Row 1: Date chip + Time chip ──────────────────────────────
                // Both chips use selected=false so they always render as the same
                // outlined (border-only) style regardless of whether a value is set.
                // Active state is indicated by the label/icon color instead.
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    // Date chip
                    FilterChip(
                        selected = false,
                        onClick  = { showCalendar = true },
                        label    = {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.CalendarMonth, null,
                                    modifier = Modifier.size(13.dp),
                                    tint = if (selectedDate.isNotBlank()) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text  = if (selectedDate.isBlank()) "No date" else formatDate(selectedDate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selectedDate.isNotBlank()) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    // Time chip — hidden until a date is set
                    if (selectedDate.isNotBlank()) {
                        FilterChip(
                            selected = false,
                            onClick  = { showTimePicker = true },
                            label    = {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Schedule, null,
                                        modifier = Modifier.size(13.dp),
                                        tint = if (selectedTime.isNotBlank()) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text  = if (selectedTime.isBlank()) "No time" else selectedTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selectedTime.isNotBlank()) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }

                // ── Row 2: Repeat (inline checkbox design) ────────────────────
                RepeatRow(
                    isChecked    = isRecurring,
                    onToggle     = { isRecurring = it },
                    recurValue   = recurValue,
                    onValueChange = { recurValue = it },
                    recurType    = recurType,
                    onTypeChange = { recurType = it },
                )

                HorizontalDivider()

                // ── Action row — full-width for reliable left/right layout ────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    // Left: × clear all  +  ↺ postpone
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onConfirm("", "", false, RecurType.DAYS, 1) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "No date",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Postpone: only for recurring tasks — advances deadline by the task's own
                        // recurrence interval (recur_value days/weeks/months) and saves immediately.
                        if (isRecurring && selectedDate.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    runCatching {
                                        val n = recurValue.toIntOrNull() ?: 1
                                        val base = LocalDate.parse(selectedDate)
                                        val postponed = when (recurType) {
                                            RecurType.WEEKS  -> base.plusWeeks(n.toLong())
                                            RecurType.MONTHS -> base.plusMonths(n.toLong())
                                            else             -> base.plusDays(n.toLong())  // DAYS or NONE
                                        }.toString()
                                        onConfirm(postponed, selectedTime, true, recurType, n)
                                    }
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.ArrowForward,
                                    contentDescription = "Postpone",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // Right: Cancel  +  Save
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        TextButton(
                            onClick = {
                                onConfirm(
                                    selectedDate,
                                    selectedTime,
                                    isRecurring,
                                    if (isRecurring) recurType else RecurType.NONE,
                                    recurValue.toIntOrNull() ?: 1,
                                )
                            },
                        ) { Text("Save") }
                    }
                }
            }
        },
        dismissButton = null,
        confirmButton = {},
    )

    // ── Calendar sub-dialog ───────────────────────────────────────────────────
    if (showCalendar) {
        val initialMillis = remember(selectedDate) {
            if (selectedDate.isBlank()) null
            else runCatching {
                LocalDate.parse(selectedDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()
        }
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton    = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { ms ->
                        selectedDate = LocalDate
                            .ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC)
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    showCalendar = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showCalendar = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = dateState) }
    }

    // ── Time sub-dialog ───────────────────────────────────────────────────────
    if (showTimePicker) {
        val (initH, initM) = remember(selectedTime) {
            if (selectedTime.isBlank()) 9 to 0
            else runCatching {
                val p = selectedTime.split(":"); p[0].toInt() to p[1].toInt()
            }.getOrDefault(9 to 0)
        }
        val timeState = rememberTimePickerState(
            initialHour   = initH,
            initialMinute = initM,
            is24Hour      = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title            = { Text("Set time") },
            text             = { TimePicker(state = timeState) },
            confirmButton    = {
                TextButton(onClick = {
                    selectedTime = "%02d:%02d".format(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }
}
