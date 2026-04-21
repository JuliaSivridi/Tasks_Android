package com.stler.tasks.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

private sealed class UpcomingRow {
    data class Header(val text: String) : UpcomingRow()
    data class Item(
        val task: com.stler.tasks.domain.model.Task,
        val labelItems: List<Pair<String, String>>,  // (name, hexColor)
        val folderName: String,
        val folderHexColor: String,
    ) : UpcomingRow()
}

class UpcomingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .taskRepository()

        val allLabels  = repo.observeLabels().first()
        val allFolders = repo.observeFolders().first()

        // All pending tasks with a deadline — same as UpcomingViewModel (no isRoot filter).
        // Sort: by date → timed tasks before no-time tasks → then by time → then by sortOrder.
        val tasks = repo.observeAllPendingTasks().first()
            .filter { it.deadlineDate.isNotBlank() }
            .sortedWith(
                compareBy(
                    { it.deadlineDate },
                    { if (it.deadlineTime.isBlank()) 1 else 0 },
                    { it.deadlineTime },
                    { it.sortOrder },
                )
            )

        val today = LocalDate.now()

        // Split tasks into overdue (< today) and future/today groups.
        // All overdue tasks share a single "Overdue" header — matching PWA behaviour.
        val overdueTasks = tasks.filter {
            runCatching { LocalDate.parse(it.deadlineDate) < today }.getOrDefault(false)
        }
        val upcomingTasks = tasks.filter {
            runCatching { LocalDate.parse(it.deadlineDate) >= today }.getOrDefault(true)
        }

        fun taskToItem(task: com.stler.tasks.domain.model.Task): UpcomingRow.Item {
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
            // ── Overdue section ───────────────────────────────────────────────
            if (overdueTasks.isNotEmpty()) {
                add(UpcomingRow.Header("Overdue"))
                overdueTasks
                    .sortedWith(compareBy(
                        { it.deadlineDate },
                        { if (it.deadlineTime.isBlank()) 1 else 0 },
                        { it.deadlineTime },
                    ))
                    .forEach { add(taskToItem(it)) }
            }
            // ── Future / today sections (one header per date) ─────────────────
            upcomingTasks
                .groupBy { it.deadlineDate }
                .entries
                .sortedBy { it.key }
                .forEach { (dateStr, group) ->
                    add(UpcomingRow.Header(formatDateHeader(dateStr, today)))
                    group.forEach { add(taskToItem(it)) }
                }
        }

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface),
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
                                is UpcomingRow.Header -> DateHeader(row.text)
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
private fun DateHeader(text: String) {
    Text(
        text     = text,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
        style = TextStyle(
            color      = GlanceTheme.colors.primary,
            fontSize   = 14.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

/**
 * Formats a date header identical to the app's DayHeader:
 *   "17 Apr · Today · Thursday"
 *   "18 Apr · Tomorrow · Friday"
 *   "25 Apr · Saturday"
 *   "1 Jan · Overdue" (past dates)
 */
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
            if (special != null) { append(" · "); append(special) }
            append(" · ")
            append(weekday)
        }
    } catch (_: Exception) { dateStr }
}

class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}
