package com.stler.tasks.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Task
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

private sealed class UpcomingRow {
    data class Header(val text: String, val isOverdue: Boolean = false) : UpcomingRow()
    data class Item(
        val task          : Task,
        val labelItems    : List<Pair<String, String>>,  // (name, hexColor)
        val folderName    : String,
        val folderHexColor: String,
    ) : UpcomingRow()
}

class UpcomingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .taskRepository()

        // ── Obtain flows once — collected reactively inside provideContent ────
        // This makes the widget auto-update whenever the DB changes (e.g. task
        // completed) without needing an explicit updateAll() call.
        val tasksFlow   : Flow<List<Task>>   = repo.observeAllPendingTasks()
        val labelsFlow  : Flow<List<Label>>  = repo.observeLabels()
        val foldersFlow : Flow<List<Folder>> = repo.observeFolders()

        provideContent {
            // collectAsState subscribes inside the Glance composition; any DB
            // change causes a recomposition and re-renders the widget with fresh data.
            val allTasks   by tasksFlow.collectAsState(initial = emptyList())
            val allLabels  by labelsFlow.collectAsState(initial = emptyList())
            val allFolders by foldersFlow.collectAsState(initial = emptyList())

            val today = LocalDate.now()

            val tasks = allTasks
                .filter { it.deadlineDate.isNotBlank() }
                .sortedWith(
                    compareBy(
                        { it.deadlineDate },
                        { if (it.deadlineTime.isBlank()) 1 else 0 },
                        { it.deadlineTime },
                        { it.sortOrder },
                    )
                )

            val overdueTasks = tasks.filter {
                runCatching { LocalDate.parse(it.deadlineDate) < today }.getOrDefault(false)
            }
            val upcomingTasks = tasks.filter {
                runCatching { LocalDate.parse(it.deadlineDate) >= today }.getOrDefault(true)
            }

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

            val rows = buildList<UpcomingRow> {
                if (overdueTasks.isNotEmpty()) {
                    add(UpcomingRow.Header("Overdue", isOverdue = true))
                    overdueTasks
                        .sortedWith(compareBy(
                            { it.deadlineDate },
                            { if (it.deadlineTime.isBlank()) 1 else 0 },
                            { it.deadlineTime },
                        ))
                        .forEach { add(taskToItem(it)) }
                }
                upcomingTasks
                    .groupBy { it.deadlineDate }
                    .entries
                    .sortedBy { it.key }
                    .forEach { (dateStr, group) ->
                        add(UpcomingRow.Header(formatDateHeader(dateStr, today)))
                        group.forEach { add(taskToItem(it)) }
                    }
            }

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
                                is UpcomingRow.Item   -> row.task.id.hashCode().toLong()
                            }
                        }) { row ->
                            when (row) {
                                is UpcomingRow.Header -> DateHeader(row.text, row.isOverdue)
                                is UpcomingRow.Item   -> WidgetTaskRow(
                                    task            = row.task,
                                    labelItems      = row.labelItems,
                                    folderName      = row.folderName,
                                    folderHexColor  = row.folderHexColor,
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

private fun formatDateHeader(dateStr: String, today: LocalDate): String {
    return try {
        val date       = LocalDate.parse(dateStr)
        val datePart   = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        val weekday    = date.dayOfWeek
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
    } catch (_: Exception) { dateStr }
}

class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}
