package com.stler.tasks.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Task
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.Flow

private data class FolderRow(
    val task             : Task,
    val depth            : Int,
    val hasChildren      : Boolean,
    val labelItems       : List<Pair<String, String>>, // (name, hexColor)
    val pendingChildCount: Int = 0,
)

class FolderWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val folderId    = WidgetPrefs.getFolderId(context, appWidgetId)

        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .taskRepository()

        // ── Obtain flows once — collected reactively inside provideContent ────
        // When a task is completed (or any DB change occurs), Room emits new data,
        // collectAsState picks it up, and the widget recomposes with correct data
        // immediately — without waiting for a new SessionWorker to start.
        val tasksFlow  : Flow<List<Task>>   = repo.observePendingInFolder(folderId)
        val labelsFlow : Flow<List<Label>>  = repo.observeLabels()
        val foldersFlow: Flow<List<Folder>> = repo.observeFolders()

        provideContent {
            val tasks   by tasksFlow.collectAsState(initial = emptyList())
            val labels  by labelsFlow.collectAsState(initial = emptyList())
            val folders by foldersFlow.collectAsState(initial = emptyList())

            val folderName  = folders.find { it.id == folderId }?.name ?: "Inbox"
            val displayList = buildList<FolderRow> {
                addRecursive(tasks, labels, parentId = null, depth = 0)
            }

            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(WSurface),
                ) {
                    WidgetHeader(
                        title     = folderName,
                        folderId  = folderId,
                        screenUri = "stlertasks://folder/$folderId",
                    )
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(displayList, itemId = { it.task.id.hashCode().toLong() }) { row ->
                            WidgetTaskRow(
                                task              = row.task,
                                indentLevel       = row.depth,
                                hasChildren       = row.hasChildren,
                                labelItems        = row.labelItems,
                                pendingChildCount = row.pendingChildCount,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Recursively adds tasks to the display list.
 * Supports unlimited nesting depth — each level is indented by [depth] * 16dp.
 */
private fun MutableList<FolderRow>.addRecursive(
    allTasks : List<Task>,
    allLabels: List<Label>,
    parentId : String?,
    depth    : Int,
) {
    val children = if (parentId == null)
        allTasks.filter { it.isRoot }
    else
        allTasks.filter { it.parentId == parentId }

    for (task in children) {
        val taskChildren = allTasks.filter { it.parentId == task.id }
        add(
            FolderRow(
                task              = task,
                depth             = depth,
                hasChildren       = taskChildren.isNotEmpty(),
                labelItems        = task.labels.mapNotNull { lid ->
                    allLabels.find { it.id == lid }?.let { lbl -> lbl.name to lbl.color }
                },
                // Pending child count = number of visible (pending) children in the loaded list
                pendingChildCount = taskChildren.size,
            )
        )
        if (task.isExpanded) {
            addRecursive(allTasks, allLabels, task.id, depth + 1)
        }
    }
}

class FolderWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FolderWidget()
}
