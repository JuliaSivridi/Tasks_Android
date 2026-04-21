package com.stler.tasks.ui.task

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.stler.tasks.domain.model.Priority
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// ── Priority colours ─────────────────────────────────────────────────────────

val PriorityUrgent = Color(0xFFf87171)
val PriorityImportant = Color(0xFFfb923c)
val PriorityNormal = Color(0xFF9ca3af)

fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.URGENT -> PriorityUrgent
    Priority.IMPORTANT -> PriorityImportant
    Priority.NORMAL -> PriorityNormal
}

// ── Deadline colours ──────────────────────────────────────────────────────────

val DeadlineOverdue = Color(0xFFf87171)
val DeadlineToday = Color(0xFF16a34a)
val DeadlineTomorrow = Color(0xFFfb923c)
val DeadlineThisWeek = Color(0xFFa78bfa)

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
            else ->
                deadlineDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) + timeStr
        }
    } catch (_: Exception) {
        null
    }
}
