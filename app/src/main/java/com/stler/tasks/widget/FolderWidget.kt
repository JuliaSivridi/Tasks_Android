package com.stler.tasks.widget

import android.content.Context
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
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Task
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

private data class FolderRow(
    val task: Task,
    val depth: Int,
    val hasChildren: Boolean,
    val labelItems: List<Pair<String, String>>, // (name, hexColor)
)

class FolderWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val folderId = WidgetPrefs.getFolderId(context, appWidgetId)

        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .taskRepository()

        val allLabels  = repo.observeLabels().first()
        val folders    = repo.observeFolders().first()
        val folderName = folders.find { it.id == folderId }?.name ?: "Inbox"
        val tasks      = repo.observePendingInFolder(folderId).first()

        val displayList = buildList<FolderRow> {
            addRecursive(tasks, allLabels, parentId = null, depth = 0)
        }

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface),
                ) {
                    WidgetHeader(
                        title     = folderName,
                        folderId  = folderId,
                        screenUri = "stlertasks://folder/$folderId",
                    )
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(displayList, itemId = { it.task.id.hashCode().toLong() }) { row ->
                            WidgetTaskRow(
                                task        = row.task,
                                indentLevel = row.depth,
                                hasChildren = row.hasChildren,
                                labelItems  = row.labelItems,
                                // Folder name not shown (all tasks are in this folder)
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
    allTasks  : List<Task>,
    allLabels : List<Label>,
    parentId  : String?,
    depth     : Int,
) {
    val children = if (parentId == null)
        allTasks.filter { it.isRoot }
    else
        allTasks.filter { it.parentId == parentId }

    for (task in children) {
        val taskChildren = allTasks.filter { it.parentId == task.id }
        add(
            FolderRow(
                task        = task,
                depth       = depth,
                hasChildren = taskChildren.isNotEmpty(),
                labelItems  = task.labels.mapNotNull { lid ->
                    allLabels.find { it.id == lid }?.let { lbl -> lbl.name to lbl.color }
                },
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
