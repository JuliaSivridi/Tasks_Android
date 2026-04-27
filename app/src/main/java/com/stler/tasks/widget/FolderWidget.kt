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

private data class FolderRow(
    val task               : Task,
    val depth              : Int,
    val hasChildren        : Boolean,
    val labelItems         : List<Pair<String, String>>, // (name, hexColor)
    val completedChildCount: Int = 0,
    val pendingChildCount  : Int = 0,
    val totalChildCount    : Int = 0,
)

class FolderWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

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
        val tasksFlow          : Flow<List<Task>>          = repo.observePendingInFolder(folderId)
        val labelsFlow         : Flow<List<Label>>         = repo.observeLabels()
        val foldersFlow        : Flow<List<Folder>>        = repo.observeFolders()
        val completedCountsFlow: Flow<Map<String, Int>>    = repo.observeCompletedChildCountsInFolder(folderId)

        provideContent {
            val tasks           by tasksFlow.collectAsState(initial = emptyList())
            val labels          by labelsFlow.collectAsState(initial = emptyList())
            val folders         by foldersFlow.collectAsState(initial = emptyList())
            val completedCounts by completedCountsFlow.collectAsState(initial = emptyMap())

            val prefs             = currentState<Preferences>()
            val rawPendingId      = prefs[pendingCompleteKey]
            val pendingTs         = prefs[pendingCompleteTimestamp] ?: 0L
            val pendingCompleteId = rawPendingId
                ?.takeIf { System.currentTimeMillis() - pendingTs < 10_000L }

            val folderName  = folders.find { it.id == folderId }?.name ?: "Inbox"
            val displayList = buildList<FolderRow> {
                addRecursive(tasks, labels, completedCounts, parentId = null, depth = 0)
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
                        items(displayList.take(20), itemId = { it.task.id.hashCode().toLong() }) { row ->
                            WidgetTaskRow(
                                task                = row.task,
                                indentLevel         = row.depth,
                                hasChildren         = row.hasChildren,
                                labelItems          = row.labelItems,
                                completedChildCount = row.completedChildCount,
                                pendingChildCount   = row.pendingChildCount,
                                totalChildCount     = row.totalChildCount,
                                pendingCompleteId   = pendingCompleteId,
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
 *
 * @param completedCounts Map from parentId → completed child count (from Room).
 */
private fun MutableList<FolderRow>.addRecursive(
    allTasks       : List<Task>,
    allLabels      : List<Label>,
    completedCounts: Map<String, Int>,
    parentId       : String?,
    depth          : Int,
) {
    val children = if (parentId == null)
        allTasks.filter { it.isRoot }
    else
        allTasks.filter { it.parentId == parentId }

    for (task in children) {
        val pendingKids   = allTasks.filter { it.parentId == task.id }.size
        val completedKids = completedCounts[task.id] ?: 0
        val totalKids     = pendingKids + completedKids
        add(
            FolderRow(
                task                = task,
                depth               = depth,
                hasChildren         = totalKids > 0,
                labelItems          = task.labels.mapNotNull { lid ->
                    allLabels.find { it.id == lid }?.let { lbl -> lbl.name to lbl.color }
                },
                completedChildCount = completedKids,
                pendingChildCount   = pendingKids,
                totalChildCount     = totalKids,
            )
        )
        if (task.isExpanded) {
            addRecursive(allTasks, allLabels, completedCounts, task.id, depth + 1)
        }
    }
}

class FolderWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FolderWidget()
}
