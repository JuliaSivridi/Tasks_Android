package com.stler.tasks.ui.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.tasks.ui.util.EmptyState
import com.stler.tasks.ui.util.ShimmerTaskList
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    calendarId: String,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val groupedEvents by viewModel.groupedEvents.collectAsStateWithLifecycle()
    val isLoading     by viewModel.isLoading.collectAsStateWithLifecycle()

    val today        = LocalDate.now()
    val orderedDates = groupedEvents.keys.toList()

    when {
        isLoading -> ShimmerTaskList(modifier = Modifier.fillMaxSize())

        orderedDates.isEmpty() -> EmptyState(
            icon     = Icons.Outlined.CalendarMonth,
            message  = "No events",
            subtitle = "Events will appear here after the next sync",
        )

        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            orderedDates.forEach { date ->
                val events = groupedEvents[date] ?: emptyList()

                item(key = "header_$date") {
                    CalendarDayHeader(date = date, today = today)
                }

                items(events, key = { "event_${it.id}" }) { event ->
                    CalendarEventItem(event = event, showDate = false)
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

// ── Day header (local copy — same visual as UpcomingScreen's DayHeader) ────────

@Composable
private fun CalendarDayHeader(date: LocalDate, today: LocalDate) {
    val label = if (date == LocalDate.MIN) {
        "Overdue"
    } else {
        val datePart    = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        val weekdayPart = date.dayOfWeek
            .getDisplayName(TextStyle.FULL, Locale.getDefault())
            .replaceFirstChar { it.uppercase() }
        val special = when (date) {
            today             -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else              -> null
        }
        buildString {
            append(datePart)
            append(" ‧ ")
            append(weekdayPart)
            if (special != null) { append(" ‧ "); append(special) }
        }
    }

    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = if (date == LocalDate.MIN) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}
