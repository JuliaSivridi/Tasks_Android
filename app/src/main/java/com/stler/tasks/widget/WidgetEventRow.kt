package com.stler.tasks.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.stler.tasks.R
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.ui.task.DeadlineStatus
import com.stler.tasks.ui.task.deadlineStatus

/**
 * Widget row for a calendar event — mirrors [WidgetTaskRow] layout:
 *
 *   [spacer?] [calendar icon] Title
 *                              time · Calendar name
 *
 * The calendar icon appears in the checkbox position, tinted with the event's calendar colour.
 * Events cannot be completed, so there is no click action on the icon area.
 *
 * @param showExpandSpace When true, adds a 28dp spacer before the icon to align with
 *   WidgetTaskRow rows that include a chevron (e.g. FolderWidget).
 *   Pass false (default) in Upcoming/TaskList where tasks use showExpandSpace=false.
 */
@Composable
fun WidgetEventRow(
    event: CalendarEvent,
    showExpandSpace: Boolean = false,
    /** When true only the time is shown in row 2; used in Upcoming where the
     *  date header already names the date.  When false the date is also shown. */
    timeOnly: Boolean = true,
) {
    val calColor = hexToColorProvider(event.calendarColor) ?: WPrimary

    val dlStatus = deadlineStatus(event.startDate)
    val dlColor  = when (dlStatus) {
        DeadlineStatus.OVERDUE   -> WDeadlineOverdue
        DeadlineStatus.TODAY     -> WDeadlineToday
        DeadlineStatus.TOMORROW  -> WDeadlineTomorrow
        DeadlineStatus.THIS_WEEK -> WDeadlineThisWeek
        else                     -> WOnSurfaceVariant
    }

    // Time label: all-day events show nothing in grouped (timeOnly) view;
    // timed events always show the time.
    val timeLabel: String = when {
        event.isAllDay && timeOnly  -> ""
        event.isAllDay && !timeOnly -> formatEventDate(event.startDate)
        !timeOnly                   -> "${formatEventDate(event.startDate)} · ${event.startTime}"
        else                        -> event.startTime   // timeOnly, has time
    }

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(
                    start  = 8.dp,
                    end    = 8.dp,
                    top    = 4.dp,
                    bottom = 4.dp,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            // ── Chevron spacer — matches WidgetTaskRow's expand column width ──
            if (showExpandSpace) {
                Spacer(GlanceModifier.width(28.dp))
            }

            Spacer(GlanceModifier.width(6.dp))

            // ── Calendar icon — sits in the same 32dp slot as the checkbox ────
            Box(
                modifier = GlanceModifier.size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider           = ImageProvider(R.drawable.ic_calendar_month),
                    contentDescription = event.calendarName,
                    modifier           = GlanceModifier.size(20.dp),
                    colorFilter        = ColorFilter.tint(calColor),
                )
            }

            Spacer(GlanceModifier.width(8.dp))

            // ── Title + row 2 ────────────────────────────────────────────────
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .wrapContentHeight(),
            ) {
                // Row 1: event title
                Text(
                    text     = event.title,
                    maxLines = 2,
                    style    = TextStyle(
                        color    = WOnSurface,
                        fontSize = 14.sp,
                    ),
                )

                // Row 2: time label + calendar name
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    var hasContent = false

                    if (timeLabel.isNotBlank()) {
                        Text(
                            text  = timeLabel,
                            style = TextStyle(color = dlColor, fontSize = 14.sp),
                        )
                        hasContent = true
                    }

                    val calSep = if (hasContent) " · " else ""
                    Text(
                        text  = "$calSep${event.calendarName}",
                        style = TextStyle(color = WOnSurfaceVariant, fontSize = 14.sp),
                    )
                }
            }
        }

        // Thin divider — matches WidgetTaskRow
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(WDivider),
        ) {}
    }
}

// ── Private helpers ──────────────────────────────────────────────────────────

/** Formats a "YYYY-MM-DD" string to a short human-readable label. */
private fun formatEventDate(dateStr: String): String = try {
    val date  = java.time.LocalDate.parse(dateStr)
    val today = java.time.LocalDate.now()
    when {
        date == today             -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        else                      -> date.format(
            java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault())
        )
    }
} catch (_: Exception) { dateStr }
