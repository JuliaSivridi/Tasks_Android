package com.stler.tasks.widget

import android.content.Context
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
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

class TaskListWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId    = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val filterFolder   = WidgetPrefs.getFilterFolder(context, appWidgetId)
        val filterLabel    = WidgetPrefs.getFilterLabel(context, appWidgetId)
        val filterPriority = WidgetPrefs.getFilterPriority(context, appWidgetId)

        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .taskRepository()

        val allLabels  = repo.observeLabels().first()
        val allFolders = repo.observeFolders().first()

        // All pending tasks at all depths
        var tasks = repo.observeAllPendingTasks().first()
        if (filterFolder   != null) tasks = tasks.filter { it.folderId == filterFolder }
        if (filterLabel    != null) tasks = tasks.filter { filterLabel in it.labels }
        if (filterPriority != null) tasks = tasks.filter { it.priority.name.lowercase() == filterPriority }

        // Build a compact filter summary: "@Folder #Label !1/!2/!3"
        val titleParts = buildList {
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
        }
        val title = if (titleParts.isEmpty()) "Tasks" else titleParts.joinToString(" ")

        // Resolve display data before provideContent (no coroutines inside)
        data class Row(
            val task: com.stler.tasks.domain.model.Task,
            val labelItems: List<Pair<String, String>>,
            val folderName: String,
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

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(WSurface),
                ) {
                    WidgetHeader(title = title, screenUri = "stlertasks://all_tasks")
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(rows, itemId = { it.task.id.hashCode().toLong() }) { row ->
                            WidgetTaskRow(
                                task            = row.task,
                                labelItems      = row.labelItems,
                                folderName      = row.folderName,
                                folderHexColor  = row.folderHexColor,
                                showExpandSpace = false,
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
