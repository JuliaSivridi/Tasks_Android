package com.stler.tasks.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.state.PreferencesGlanceStateDefinition
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

/** Unified row type for the TaskList widget's combined task + event list. */
private sealed class TaskListRow {
    data class TaskRow(
        val task          : Task,
        val labelItems    : List<Pair<String, String>>,
        val folderName    : String,
        val folderHexColor: String,
    ) : TaskListRow()
    data class EventRow(val event: CalendarEvent) : TaskListRow()
}

private data class TaskListSortable(
    val date   : LocalDate,
    val hasTime: Boolean,
    val time   : String,
    val row    : TaskListRow,
)

class TaskListWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId    = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val filterFolder   = WidgetPrefs.getFilterFolder(context, appWidgetId)
        val filterLabel    = WidgetPrefs.getFilterLabel(context, appWidgetId)
        val filterPriority = WidgetPrefs.getFilterPriority(context, appWidgetId)

        val ep   = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val repo = ep.taskRepository()
        val calendarRepo = ep.calendarRepository()

        // ── Obtain flows once — collected reactively inside provideContent ────
        val tasksFlow  : Flow<List<Task>>   = repo.observeAllPendingTasks()
        val labelsFlow : Flow<List<Label>>  = repo.observeLabels()
        val foldersFlow: Flow<List<Folder>> = repo.observeFolders()

        // Events are suppressed entirely when any task filter is active,
        // mirroring the AllTasks screen behavior in the app.
        val anyFilterActive = filterFolder != null || filterLabel != null || filterPriority != null
        val from = LocalDate.now().minusDays(1)
        val to   = LocalDate.now().plusDays(7)
        val eventsFlow: Flow<List<CalendarEvent>> =
            if (anyFilterActive) {
                flowOf(emptyList())
            } else {
                calendarRepo.getSelectedCalendarIds().flatMapLatest { ids ->
                    if (ids.isEmpty()) flowOf(emptyList())
                    else calendarRepo.getEventsForCalendars(ids, from, to)
                }
            }

        provideContent {
            val allTasks   by tasksFlow.collectAsState(initial = emptyList())
            val allLabels  by labelsFlow.collectAsState(initial = emptyList())
            val allFolders by foldersFlow.collectAsState(initial = emptyList())
            val allEvents  by eventsFlow.collectAsState(initial = emptyList())

            val prefs             = currentState<Preferences>()
            val rawPendingId      = prefs[pendingCompleteKey]
            val pendingTs         = prefs[pendingCompleteTimestamp] ?: 0L
            val pendingCompleteId = rawPendingId
                ?.takeIf { System.currentTimeMillis() - pendingTs < 4_000L }

            // Apply task filters
            val tasks = allTasks
                .let { list -> if (filterFolder   != null) list.filter { it.folderId == filterFolder } else list }
                .let { list -> if (filterLabel    != null) list.filter { filterLabel in it.labels }    else list }
                .let { list -> if (filterPriority != null) list.filter { it.priority.name.lowercase() == filterPriority } else list }

            val title = buildList {
                if (filterFolder != null) {
                    val name = allFolders.find { it.id == filterFolder }?.name ?: "Folder"
                    add("@$name")
                }
                if (filterLabel != null) {
                    val name = allLabels.find { it.id == filterLabel }?.name ?: "Label"
                    add("#$name")
                }
                if (filterPriority != null) {
                    add(when (filterPriority) {
                        "urgent"    -> "!1"
                        "important" -> "!2"
                        else        -> "!3"
                    })
                }
            }.joinToString(" ").ifEmpty { "Tasks" }

            // ── Unified sorted list: tasks + events ───────────────────────────
            // Tasks are interleaved with events by date order.
            // Undated tasks (no deadlineDate) sort to the very end via LocalDate.MAX.
            val cutoff  = LocalDate.now().plusDays(6)   // 7-day window for events
            val maxDate = LocalDate.MAX

            val sortable: List<TaskListSortable> = buildList {
                // Tasks: dated ones carry their deadline date; undated carry MAX
                tasks.forEach { task ->
                    val folder = allFolders.find { it.id == task.folderId }
                    val date   = task.deadlineDate.takeIf { it.isNotBlank() }
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    add(TaskListSortable(
                        date    = date ?: maxDate,
                        hasTime = date != null && task.deadlineTime.isNotBlank(),
                        time    = if (date != null) task.deadlineTime else "",
                        row     = TaskListRow.TaskRow(
                            task           = task,
                            labelItems     = task.labels.mapNotNull { lid ->
                                allLabels.find { it.id == lid }?.let { lbl -> lbl.name to lbl.color }
                            },
                            folderName     = if (filterFolder != null) "" else (folder?.name ?: "Inbox"),
                            folderHexColor = if (filterFolder != null) "" else (folder?.color ?: ""),
                        ),
                    ))
                }
                // Events: always have a date; skip those beyond the 7-day window
                allEvents.forEach { event ->
                    val date = runCatching { LocalDate.parse(event.startDate) }.getOrNull()
                        ?: return@forEach
                    if (date > cutoff) return@forEach
                    add(TaskListSortable(
                        date    = date,
                        hasTime = event.startTime.isNotBlank(),
                        time    = event.startTime,
                        row     = TaskListRow.EventRow(event),
                    ))
                }
            }

            // Primary sort: date. Secondary: timed items (0) before all-day (1). Tertiary: time.
            val rows: List<TaskListRow> = sortable
                .sortedWith(compareBy({ it.date }, { if (it.hasTime) 0 else 1 }, { it.time }))
                .map { it.row }

            // ── Render ────────────────────────────────────────────────────────
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(WSurface),
                ) {
                    WidgetHeader(title = title, screenUri = "stlertasks://all_tasks")
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(rows.take(20), itemId = { row ->
                            when (row) {
                                is TaskListRow.TaskRow  -> "t_${row.task.id}".hashCode().toLong()
                                is TaskListRow.EventRow -> "e_${row.event.id}".hashCode().toLong()
                            }
                        }) { row ->
                            when (row) {
                                is TaskListRow.TaskRow  -> WidgetTaskRow(
                                    task              = row.task,
                                    labelItems        = row.labelItems,
                                    folderName        = row.folderName,
                                    folderHexColor    = row.folderHexColor,
                                    showExpandSpace   = false,
                                    pendingCompleteId = pendingCompleteId,
                                )
                                is TaskListRow.EventRow -> WidgetEventRow(
                                    event           = row.event,
                                    showExpandSpace = false,
                                    timeOnly        = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class TaskListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskListWidget()
}
