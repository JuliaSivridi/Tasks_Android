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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.ui.task.deadlineColor
import com.stler.tasks.ui.task.deadlineStatus
import com.stler.tasks.util.toComposeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Read-only row for a [CalendarEvent] — mirrors the two-row structure of TaskItem:
 *
 *  Row 1 [padding start=0 end=4 top=4 bottom=2]:
 *    [40dp expand placeholder] [6dp] [40dp CalendarMonth icon at 20dp] [8dp] [title bodyMedium]
 *
 *  Row 2 [padding start=54 end=8 bottom=4]:
 *    [time text bodyMedium]  [14dp CalendarMonth icon]  [calendar name bodyMedium onSurfaceVariant]
 *
 * [showDate] = false in date-grouped lists (Upcoming, CalendarScreen): only the
 * time portion is shown, and "All day" is suppressed (the section header carries the date).
 * [showDate] = true in flat lists (AllTasks): shows "d MMM · HH:MM" or "Today" etc.
 */
@Composable
fun CalendarEventItem(
    event: CalendarEvent,
    showDate: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val today     = remember { LocalDate.now() }
    val timeLabel = formatEventTime(event, today, showDate)
    val calColor  = event.calendarColor.toComposeColor()

    Column(modifier = modifier.fillMaxWidth()) {

        // ── Row 1: leading icon + title ────────────────────────────────────
        Row(
            modifier = Modifier.padding(
                start  = 0.dp,
                end    = 4.dp,
                top    = 4.dp,
                bottom = 2.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Expand/collapse placeholder — keeps left alignment identical to TaskItem
            Box(modifier = Modifier.size(40.dp))

            Spacer(modifier = Modifier.width(6.dp))

            // CalendarMonth icon in the "checkbox" position.
            // Touch-target Box is 40 dp (same as TaskCheckbox), icon visual is 20 dp
            // (one step larger than the 18 dp checkbox canvas so it reads at the same weight).
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = calColor,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text     = event.title,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style    = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.width(4.dp))
        }

        // ── Row 2: time + calendar name ────────────────────────────────────
        // Always rendered (at minimum shows the calendar name).
        // start = 54 dp = expand(40) + spacer(6) + 8 — same formula as TaskItem's
        // metadata row at depth = 0.
        Row(
            modifier = Modifier.padding(
                start  = 54.dp,
                end    = 8.dp,
                bottom = 4.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            if (timeLabel.isNotBlank()) {
                Text(
                    text  = timeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = deadlineColor(deadlineStatus(event.startDate)),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint     = calColor,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text     = event.calendarName,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Formats the event date/time for display.
 *
 * [showDate] = false (grouped-by-date, e.g. Upcoming / CalendarScreen):
 *   - All-day → "" (section header already conveys the date)
 *   - Timed   → "HH:MM"
 *
 * [showDate] = true (flat list, e.g. AllTasks):
 *   - All-day → "Today" / "Tomorrow" / "d MMM"
 *   - Timed   → "d MMM · HH:MM"
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
