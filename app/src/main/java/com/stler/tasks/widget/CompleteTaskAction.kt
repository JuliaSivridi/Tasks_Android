package com.stler.tasks.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val taskIdKey    = ActionParameters.Key<String>("taskId")
val folderIdKey  = ActionParameters.Key<String>("folderId")
val expandKey    = ActionParameters.Key<Boolean>("expand")
val screenUriKey = ActionParameters.Key<String>("screenUri")

/** Marks a task complete via the repository, then refreshes all widget instances. */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[taskIdKey] ?: return
        // completeTask() already calls widgetRefresher.refreshAll() internally.
        withContext(Dispatchers.IO) { repo(context).completeTask(taskId) }
    }
}

/**
 * Toggles isExpanded on a task and refreshes all widget instances.
 * Previously only refreshed FolderWidget; now goes through the debounced
 * WidgetRefresher singleton so all three widget types stay in sync.
 */
class ToggleExpandAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[taskIdKey] ?: return
        val expand = parameters[expandKey] ?: return
        // toggleExpanded() calls widgetRefresher.refreshAll() internally.
        withContext(Dispatchers.IO) { repo(context).toggleExpanded(taskId, expand) }
    }
}

/** Opens the app to edit a specific task: stlertasks://task/{taskId} */
class OpenTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[taskIdKey] ?: return
        startDeepLink(context, "stlertasks://task/$taskId")
    }
}

/** Opens the create-task sheet, optionally pre-selecting a folder: stlertasks://create[?folderId=…] */
class OpenCreateAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val folderId = parameters[folderIdKey]
        val uri = if (!folderId.isNullOrBlank()) "stlertasks://create?folderId=$folderId"
                  else "stlertasks://create"
        startDeepLink(context, uri)
    }
}

/** Opens a specific screen in the app via deeplink URI stored in [screenUriKey]. */
class OpenScreenAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val uri = parameters[screenUriKey] ?: return
        startDeepLink(context, uri)
    }
}

// ── Private helpers ──────────────────────────────────────────────────────────

private fun repo(context: Context) =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        .taskRepository()

private fun startDeepLink(context: Context, uri: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        .addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
    context.startActivity(intent)
}
