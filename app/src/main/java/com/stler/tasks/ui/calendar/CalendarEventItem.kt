package com.stler.tasks.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.ui.task.deadlineColor
import com.stler.tasks.ui.task.deadlineStatus
import com.stler.tasks.util.toComposeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Row for a [CalendarEvent]. When [onEdit] / [onDelete] are provided, shows action buttons:
 *  - Schedule icon → [onEdit]
 *  - MoreHoriz icon → ModalBottomSheet with Edit / Delete entries
 *
 * When [event.recurringEventId] is non-blank, the Delete entry triggers a choice dialog:
 * "Delete this event only" ([onDelete]) vs "Delete all in series" ([onDeleteSeries]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalendarEventItem(
    event           : CalendarEvent,
    showDate        : Boolean = true,
    onEdit          : (() -> Unit)? = null,
    onEditSchedule  : (() -> Unit)? = null,   // Schedule icon — lightweight date/time edit
    onDelete        : (() -> Unit)? = null,
    onDeleteSeries  : (() -> Unit)? = null,
    modifier        : Modifier = Modifier,
) {
    val today    = remember { LocalDate.now() }
    val timeLabel = formatEventTime(event, today, showDate)
    val calColor  = event.calendarColor.toComposeColor()

    var showMenu          by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isRecurring = event.recurringEventId.isNotBlank()

    // ── Delete confirmation ────────────────────────────────────────────────────
    if (showDeleteConfirm) {
        if (isRecurring && onDeleteSeries != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete recurring event?") },
                text  = { Text("\"${event.title}\"") },
                confirmButton = {
                    // All three buttons in a full-width Column — each on its own row
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = { showDeleteConfirm = false; onDelete?.invoke() }) {
                            Text("Delete this event only")
                        }
                        TextButton(onClick = { showDeleteConfirm = false; onDeleteSeries() }) {
                            Text("Delete all events in series",
                                color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                },
                dismissButton = null,
            )
        } else {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete event?") },
                text  = { Text("\"${event.title}\" will be permanently deleted from Google Calendar.") },
                confirmButton = {
                    TextButton(onClick = { showDeleteConfirm = false; onDelete?.invoke() }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                },
            )
        }
    }

    // ── More-options bottom sheet ──────────────────────────────────────────────
    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            if (onEdit != null) {
                ListItem(
                    leadingContent  = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    headlineContent = { Text("Edit") },
                    supportingContent = if (isRecurring) {
                        { Text("All events in series",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMenu = false; onEdit() },
                )
            }
            if (onDelete != null) {
                ListItem(
                    leadingContent  = {
                        Icon(Icons.Outlined.Delete, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error)
                    },
                    headlineContent = {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMenu = false; showDeleteConfirm = true },
                )
            }
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }

    // ── Item layout — mirrors TaskItem: single Row with Column { title; metadata } ──
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Calendar icon — occupies the same 40dp slot as the task checkbox
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector        = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint               = calColor,
                modifier           = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        // Title + metadata in a Column, same as TaskItem
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = event.title,
                modifier = if (onEdit != null) Modifier.clickable { onEdit() } else Modifier,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style    = MaterialTheme.typography.bodyMedium,
            )
            // Metadata: recurring icon · time label · calendar name
            // Mirrors TaskItem's FlowRow structure: recurring+time grouped, calendar name separate
            FlowRow(
                modifier              = Modifier.padding(top = 2.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(2.dp),
            ) {
                if (isRecurring || timeLabel.isNotBlank()) {
                    Row(
                        modifier              = Modifier.defaultMinSize(minHeight = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        if (isRecurring) {
                            Icon(
                                imageVector        = Icons.Outlined.Autorenew,
                                contentDescription = "Recurring",
                                modifier           = Modifier.size(14.dp),
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        if (timeLabel.isNotBlank()) {
                            Text(
                                text  = timeLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = deadlineColor(deadlineStatus(event.startDate)),
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint               = calColor,
                        modifier           = Modifier.size(14.dp),
                    )
                    Text(
                        text  = event.calendarName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        if (onEdit != null || onDelete != null) {
            Box(
                modifier = Modifier.size(40.dp).clickable { (onEditSchedule ?: onEdit)?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Outlined.Schedule,
                    contentDescription = "Edit schedule",
                    modifier           = Modifier.size(18.dp),
                    tint               = deadlineColor(deadlineStatus(event.startDate)),
                )
            }
            Box(
                modifier = Modifier.size(40.dp).clickable { showMenu = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Outlined.MoreHoriz,
                    contentDescription = "More options",
                    modifier           = Modifier.size(18.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatEventTime(event: CalendarEvent, today: LocalDate, showDate: Boolean): String {
    return if (event.isAllDay) {
        if (showDate) formatDate(event.startDate, today) else ""
    } else {
        val timePart = if (event.endTime.isNotBlank()) "${event.startTime} — ${event.endTime}"
                       else event.startTime
        if (showDate) {
            val datePart = formatDate(event.startDate, today)
            if (timePart.isNotBlank()) "$datePart · $timePart" else datePart
        } else timePart
    }
}

/** Same date-label rules as [deadlineLabel] in TaskColors.kt (without the time suffix). */
private fun formatDate(dateStr: String, today: LocalDate): String = runCatching {
    val date = LocalDate.parse(dateStr)
    val daysUntil = ChronoUnit.DAYS.between(today, date)
    when {
        daysUntil == -1L        -> "Yesterday"
        daysUntil == 0L         -> "Today"
        daysUntil == 1L         -> "Tomorrow"
        daysUntil in 2L..6L     -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        else                    -> {
            val pattern = if (date.year > today.year) "d MMM yyyy" else "d MMM"
            date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
        }
    }
}.getOrDefault(dateStr)
