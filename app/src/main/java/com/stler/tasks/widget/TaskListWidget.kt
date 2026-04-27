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
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Task
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.Flow

class TaskListWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId    = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val filterFolder   = WidgetPrefs.getFilterFolder(context, appWidgetId)
        val filterLabel    = WidgetPrefs.getFilterLabel(context, appWidgetId)
        val filterPriority = WidgetPrefs.getFilterPriority(context, appWidgetId)

        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .taskRepository()

        // ── Obtain flows once — collected reactively inside provideContent ────
        val tasksFlow  : Flow<List<Task>>   = repo.observeAllPendingTasks()
        val labelsFlow : Flow<List<Label>>  = repo.observeLabels()
        val foldersFlow: Flow<List<Folder>> = repo.observeFolders()

        // Build a compact filter summary: "@Folder #Label !1/!2/!3"
        // (Filter prefs are widget-config-time constants, so they don't need to be reactive)
        val titleParts = buildList<String> {
            // folder/label names resolved lazily below, inside provideContent
        }

        provideContent {
            val allTasks   by tasksFlow.collectAsState(initial = emptyList())
            val allLabels  by labelsFlow.collectAsState(initial = emptyList())
            val allFolders by foldersFlow.collectAsState(initial = emptyList())

            val prefs             = currentState<Preferences>()
            val rawPendingId      = prefs[pendingCompleteKey]
            val pendingTs         = prefs[pendingCompleteTimestamp] ?: 0L
            val pendingCompleteId = rawPendingId
                ?.takeIf { System.currentTimeMillis() - pendingTs < 4_000L }

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

            data class Row(
                val task          : Task,
                val labelItems    : List<Pair<String, String>>,
                val folderName    : String,
                val folderHexColor: String,
            )

            val rows = tasks.map { task ->
                val folder = allFolders.find { it.id == task.folderId }
                Row(
                    task           = task,
                    labelItems     = task.labels.mapNotNull { lid ->
                        allLabels.find { it.id == lid }?.let { lbl -> lbl.name to lbl.color }
                    },
                    folderName     = if (filterFolder != null) "" else (folder?.name ?: "Inbox"),
                    folderHexColor = if (filterFolder != null) "" else (folder?.color ?: ""),
                )
            }

            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(WSurface),
                ) {
                    WidgetHeader(title = title, screenUri = "stlertasks://all_tasks")
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(rows.take(20), itemId = { it.task.id.hashCode().toLong() }) { row ->
                            WidgetTaskRow(
                                task              = row.task,
                                labelItems        = row.labelItems,
                                folderName        = row.folderName,
                                folderHexColor    = row.folderHexColor,
                                showExpandSpace   = false,
                                pendingCompleteId = pendingCompleteId,
                            )
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
