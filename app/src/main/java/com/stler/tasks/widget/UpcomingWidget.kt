package com.stler.tasks.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Task
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

// ── Row model ────────────────────────────────────────────────────────────────

private sealed class UpcomingRow {
    data class Header(val text: String, val isOverdue: Boolean = false) : UpcomingRow()
    data class Item(
        val task          : Task,
        val labelItems    : List<Pair<String, String>>,  // (name, hexColor)
        val folderName    : String,
        val folderHexColor: String,
    ) : UpcomingRow()
    data class Event(val event: CalendarEvent) : UpcomingRow()
}

// ── Widget ───────────────────────────────────────────────────────────────────

class UpcomingWidget : GlanceAppWidget() {

    // Per-widget-instance Glance state: stores the pending-complete task ID between
    // the checkbox tap and the Room confirmation, driving the transient checkmark visual.
    override val stateDefinition = PreferencesGlanceStateDefinition

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

        val repo         = ep.taskRepository()
        val calendarRepo = ep.calendarRepository()

        // ── Obtain flows once — collected reactively inside provideContent ────
        // DB or DataStore changes cause recomposition and widget refresh automatically.
        val tasksFlow  : Flow<List<Task>>   = repo.observeAllPendingTasks()
        val labelsFlow : Flow<List<Label>>  = repo.observeLabels()
        val foldersFlow: Flow<List<Folder>> = repo.observeFolders()

        val from = LocalDate.now()
        val to   = LocalDate.now().plusDays(366)

        // Switches to a new Room query whenever the selected calendar IDs change.
        val eventsFlow: Flow<List<CalendarEvent>> =
            calendarRepo.getSelectedCalendarIds().flatMapLatest { ids ->
                if (ids.isEmpty()) flowOf(emptyList())
                else calendarRepo.getEventsForCalendars(ids, from, to)
            }

        provideContent {
            val allTasks   by tasksFlow.collectAsState(initial = emptyList())
            val allLabels  by labelsFlow.collectAsState(initial = emptyList())
            val allFolders by foldersFlow.collectAsState(initial = emptyList())
            val allEvents  by eventsFlow.collectAsState(initial = emptyList())

            // Read transient pending-complete state (set by CompleteTaskAction before Room commits)
            val prefs             = currentState<Preferences>()
            val pendingCompleteId = prefs[pendingCompleteKey]

            val today = LocalDate.now()

            // ── Helper: task → display row ────────────────────────────────
            fun taskToItem(task: Task): UpcomingRow.Item {
                val folder = allFolders.find { it.id == task.folderId }
                return UpcomingRow.Item(
                    task           = task,
                    labelItems     = task.labels.mapNotNull { lid ->
                        allLabels.find { it.id == lid }?.let { lbl -> lbl.name to lbl.color }
                    },
                    folderName     = folder?.name ?: "Inbox",
                    folderHexColor = folder?.color ?: "",
                )
            }

            // ── Unified timeline: sort tasks + events together ────────────
            // Each entry carries the info needed for date-grouping and sorting.
            data class TimelineEntry(
                val date   : LocalDate,
                val hasTime: Boolean,   // false = all-day; goes after timed items in same day
                val time   : String,    // "HH:MM" or "" — used as secondary sort key
                val row    : UpcomingRow,
            )

            val allEntries: List<TimelineEntry> = buildList {
                allTasks
                    .filter { it.deadlineDate.isNotBlank() }
                    .forEach { task ->
                        val date = runCatching { LocalDate.parse(task.deadlineDate) }.getOrNull()
                            ?: return@forEach
                        add(TimelineEntry(
                            date    = date,
                            hasTime = task.deadlineTime.isNotBlank(),
                            time    = task.deadlineTime,
                            row     = taskToItem(task),
                        ))
                    }
                allEvents.forEach { event ->
                    val date = runCatching { LocalDate.parse(event.startDate) }.getOrNull()
                        ?: return@forEach
                    add(TimelineEntry(
                        date    = date,
                        hasTime = event.startTime.isNotBlank(),
                        time    = event.startTime,
                        row     = UpcomingRow.Event(event),
                    ))
                }
            }

            // Primary: date. Secondary: timed (0) before all-day (1). Tertiary: time string.
            val sorted = allEntries.sortedWith(
                compareBy({ it.date }, { if (it.hasTime) 0 else 1 }, { it.time })
            )

            // ── Build header-per-date row list ────────────────────────────
            val rows = buildList<UpcomingRow> {
                val overdueEntries  = sorted.filter { it.date < today }
                val upcomingEntries = sorted.filter { it.date >= today }

                if (overdueEntries.isNotEmpty()) {
                    add(UpcomingRow.Header("Overdue", isOverdue = true))
                    overdueEntries.forEach { add(it.row) }
                }

                // groupBy preserves insertion order; entries are already date-sorted.
                upcomingEntries
                    .groupBy { it.date }
                    .forEach { (date, entries) ->
                        add(UpcomingRow.Header(formatDateHeader(date, today)))
                        entries.forEach { add(it.row) }
                    }
            }

            // ── Render ────────────────────────────────────────────────────
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(WSurface),
                ) {
                    WidgetHeader(
                        title     = "Upcoming",
                        screenUri = "stlertasks://upcoming",
                    )
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(rows, itemId = { row ->
                            when (row) {
                                is UpcomingRow.Header -> "h_${row.text}".hashCode().toLong()
                                is UpcomingRow.Item   -> "t_${row.task.id}".hashCode().toLong()
                                is UpcomingRow.Event  -> "e_${row.event.id}".hashCode().toLong()
                            }
                        }) { row ->
                            when (row) {
                                is UpcomingRow.Header -> DateHeader(row.text, row.isOverdue)
                                is UpcomingRow.Item   -> WidgetTaskRow(
                                    task              = row.task,
                                    labelItems        = row.labelItems,
                                    folderName        = row.folderName,
                                    folderHexColor    = row.folderHexColor,
                                    showExpandSpace   = false,
                                    timeOnly          = true,
                                    pendingCompleteId = pendingCompleteId,
                                )
                                is UpcomingRow.Event  -> WidgetEventRow(
                                    event           = row.event,
                                    showExpandSpace = false,
                                    timeOnly        = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun DateHeader(text: String, isOverdue: Boolean = false) {
    Text(
        text     = text,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
        style = TextStyle(
            color      = if (isOverdue) WError else WOnSurface,
            fontSize   = 14.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

// ── Date header text ──────────────────────────────────────────────────────────

private fun formatDateHeader(date: LocalDate, today: LocalDate): String {
    return try {
        val datePart = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        val weekday  = date.dayOfWeek
            .getDisplayName(JTextStyle.FULL, Locale.getDefault())
            .replaceFirstChar { it.uppercase() }
        val special = when {
            date < today              -> "Overdue"
            date == today             -> "Today"
            date == today.plusDays(1) -> "Tomorrow"
            else                      -> null
        }
        buildString {
            append(datePart)
            append(" · ")
            append(weekday)
            if (special != null) { append(" · "); append(special) }
        }
    } catch (_: Exception) { date.toString() }
}

// ── Receiver ──────────────────────────────────────────────────────────────────

class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}
