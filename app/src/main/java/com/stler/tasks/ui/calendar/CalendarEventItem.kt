package com.stler.tasks.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
 * Matches the visual layout of TaskItem:
 *   - 40 dp expand placeholder (left)
 *   - 40 dp box with CalendarMonth icon (in "checkbox" position, tinted with calendar color)
 *   - Column: title / time + calendar name
 *
 * [showDate] = false in date-grouped lists (Upcoming, CalendarScreen) — hides date and
 * suppresses "All day" text (the section header already conveys the date).
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
            .padding(start = 0.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Expand placeholder — keeps left alignment identical to TaskItem ──
        Box(modifier = Modifier.size(40.dp))

        Spacer(modifier = Modifier.width(6.dp))

        // ── Calendar icon in the "checkbox" position ──────────────────────
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = event.calendarColor.toComposeColor(),
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // ── Content ───────────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            // Line 1 — title
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )

            // Line 2 — time + calendar name
            val timeLabel = formatEventTime(event, today, showDate)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
 *
 * [showDate] = false (grouped-by-date list, e.g. Upcoming / CalendarScreen):
 *   - Timed events → "HH:MM"
 *   - All-day events → "" (the day header already conveys the date)
 *
 * [showDate] = true (flat list, e.g. AllTasks):
 *   - Timed events → "d MMM · HH:MM"
 *   - All-day events → "d MMM" / "Today" / "Tomorrow"
 */
private fun formatEventTime(event: CalendarEvent, today: LocalDate, showDate: Boolean): String {
    return if (event.isAllDay) {
        if (showDate) formatDate(event.startDate, today) else ""
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
