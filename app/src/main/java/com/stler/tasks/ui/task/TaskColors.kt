package com.stler.tasks.ui.task

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.ui.theme.DeadlineOverdue
import com.stler.tasks.ui.theme.DeadlineThisWeek
import com.stler.tasks.ui.theme.DeadlineToday
import com.stler.tasks.ui.theme.DeadlineTomorrow
import com.stler.tasks.ui.theme.PriorityImportant
import com.stler.tasks.ui.theme.PriorityNormal
import com.stler.tasks.ui.theme.PriorityUrgent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// ── Priority colours ─────────────────────────────────────────────────────────
// Source of truth is ui/theme/Color.kt — re-exported here for convenience.

fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.URGENT    -> PriorityUrgent
    Priority.IMPORTANT -> PriorityImportant
    Priority.NORMAL    -> PriorityNormal
}

// ── Deadline colours ──────────────────────────────────────────────────────────
// Source of truth is ui/theme/Color.kt — re-exported here for convenience.

enum class DeadlineStatus { NONE, OVERDUE, TODAY, TOMORROW, THIS_WEEK, FUTURE }

/**
 * Parses a "YYYY-MM-DD" string and returns the corresponding [DeadlineStatus]
 * relative to today. Returns [DeadlineStatus.NONE] for blank / unparseable input.
 */
fun deadlineStatus(deadlineDate: String): DeadlineStatus {
    if (deadlineDate.isBlank()) return DeadlineStatus.NONE
    return try {
        val date = LocalDate.parse(deadlineDate)
        val today = LocalDate.now()
        val daysUntil = ChronoUnit.DAYS.between(today, date)
        when {
            daysUntil < 0L -> DeadlineStatus.OVERDUE
            daysUntil == 0L -> DeadlineStatus.TODAY
            daysUntil == 1L -> DeadlineStatus.TOMORROW
            daysUntil <= 7L -> DeadlineStatus.THIS_WEEK
            else -> DeadlineStatus.FUTURE
        }
    } catch (_: Exception) {
        DeadlineStatus.NONE
    }
}

@Composable
fun deadlineColor(status: DeadlineStatus): Color = when (status) {
    DeadlineStatus.OVERDUE -> DeadlineOverdue
    DeadlineStatus.TODAY -> DeadlineToday
    DeadlineStatus.TOMORROW -> DeadlineTomorrow
    DeadlineStatus.THIS_WEEK -> DeadlineThisWeek
    DeadlineStatus.FUTURE, DeadlineStatus.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Returns a human-readable deadline label, or null if [date] is blank.
 *
 * @param includeDate When false only the [time] is returned (used in Upcoming where the date
 *   is already shown in the section header). Returns null when [time] is also blank.
 *
 * Rules (when [includeDate] is true):
 *  - Yesterday  → "Yesterday"
 *  - Today      → "Today HH:mm" if [time] is set, else "Today"
 *  - Tomorrow   → "Tomorrow"
 *  - 2–6 days away → weekday name (Mon, Tue …)
 *  - Otherwise  → "d MMM" (e.g. "3 Jun")
 */
fun deadlineLabel(date: String, time: String, includeDate: Boolean = true): String? {
    if (date.isBlank()) return null
    if (!includeDate) return if (time.isNotBlank()) time else null
    return try {
        val deadlineDate = LocalDate.parse(date)
        val today = LocalDate.now()
        val daysUntil = ChronoUnit.DAYS.between(today, deadlineDate)
        val timeStr = if (time.isNotBlank()) " $time" else ""
        when {
            daysUntil == -1L -> "Yesterday$timeStr"
            daysUntil == 0L  -> "Today$timeStr"
            daysUntil == 1L  -> "Tomorrow$timeStr"
            daysUntil in 2L..6L ->
                deadlineDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) + timeStr
            else -> {
                val pattern = if (deadlineDate.year > today.year) "d MMM yyyy" else "d MMM"
                deadlineDate.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())) + timeStr
            }
        }
    } catch (_: Exception) {
        null
    }
}
