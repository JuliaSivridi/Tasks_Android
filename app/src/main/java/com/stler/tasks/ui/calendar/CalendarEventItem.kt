package com.stler.tasks.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.ui.task.deadlineColor
import com.stler.tasks.ui.task.deadlineStatus
import com.stler.tasks.util.toComposeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A compact read-only row for a [CalendarEvent].
 * Used in Upcoming, AllTasks, and CalendarScreen.
 *
 * Line 1: event title
 * Line 2: date/time (color from deadline-status logic) · calendar icon (colored) · calendar name
 */
@Composable
fun CalendarEventItem(
    event: CalendarEvent,
    showDate: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Line 1 — title
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
            )

            // Line 2 — time + calendar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val timeLabel = formatEventTime(event, today, showDate)
                if (timeLabel.isNotBlank()) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = deadlineColor(deadlineStatus(event.startDate)),
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = event.calendarColor.toComposeColor(),
                    modifier = Modifier.size(12.dp),
                )

                Text(
                    text = event.calendarName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Formats the event date/time for display.
 * If [showDate] = false (e.g. inside a date-grouped list), shows only the time.
 */
private fun formatEventTime(event: CalendarEvent, today: LocalDate, showDate: Boolean): String {
    return if (event.isAllDay) {
        if (showDate) formatDate(event.startDate, today) else "All day"
    } else {
        if (showDate) {
            val datePart = formatDate(event.startDate, today)
            if (event.startTime.isNotBlank()) "$datePart · ${event.startTime}" else datePart
        } else {
            event.startTime
        }
    }
}

private fun formatDate(dateStr: String, today: LocalDate): String = runCatching {
    val date = LocalDate.parse(dateStr)
    when (date) {
        today             -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else              -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}.getOrDefault(dateStr)
