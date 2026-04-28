package com.stler.tasks.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val taskIdKey    = ActionParameters.Key<String>("taskId")
val folderIdKey  = ActionParameters.Key<String>("folderId")
val expandKey    = ActionParameters.Key<Boolean>("expand")
val screenUriKey = ActionParameters.Key<String>("screenUri")

/**
 * Glance Preferences key that stores the ID of the task whose checkbox was just tapped
 * but whose completion hasn't been committed to Room yet.  Each widget instance maintains
 * its own copy via [PreferencesGlanceStateDefinition], so only the widget that received
 * the tap shows the transient checkmark.
 */
val pendingCompleteKey       = stringPreferencesKey("pending_complete_id")

/**
 * Timestamp (epoch ms) when [pendingCompleteKey] was last written.
 * The widget treats the pending state as valid only for 10 seconds after this timestamp,
 * so recurring tasks never get a permanently-stuck checkmark even without explicit clearing.
 */
val pendingCompleteTimestamp = longPreferencesKey("pending_complete_ts")

/**
 * Marks a task complete via the repository, then refreshes all widget instances.
 *
 * The refresh is called SYNCHRONOUSLY here (not through the debounced WidgetRefresher)
 * because Glance runs action callbacks inside InvisibleActionTrampolineActivity.
 * On MIUI (and other aggressive battery-saving ROMs), background coroutines are
 * restricted as soon as that activity finishes — a debounced refresh that fires
 * 400 ms later may never actually run, leaving completed tasks visible on the widget
 * for minutes.  Calling refreshAll() before onAction() returns ensures the
 * Glance SessionWorker is enqueued while the activity is still alive.
 *
 * The debounced WidgetRefresher (called inside repo.completeTask) will also fire
 * later — that second refresh is harmless and shows the same correct data.
 */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[taskIdKey] ?: return

        // ── Step 1: show checkmark immediately ───────────────────────────────
        // Write the pending task ID + a timestamp into this widget's Glance state.
        // The widget reads the timestamp and only shows the checkmark while it is
        // < 10 seconds old — no explicit clearing needed, so there is no race with
        // WorkManager's KEEP policy (which would have caused clearing to happen
        // before the queued SessionWorker even started).
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().also {
                it[pendingCompleteKey]       = taskId
                it[pendingCompleteTimestamp] = System.currentTimeMillis()
            }
        }
        refreshAll(context)   // for MIUI / non-running sessions

        // Let the checkmark be visible for a moment before Room removes the task.
        delay(300)

        // ── Step 2: commit to Room ───────────────────────────────────────────
        withContext(Dispatchers.IO) { repo(context).completeTask(taskId) }

        // ── Step 3: clear pending state ──────────────────────────────────────
        // updateAppWidgetState writes to DataStore; a *running* Glance session
        // immediately recomposes on that change (DataStore-driven path, not WorkManager),
        // so recurring tasks with an advanced deadline stop showing a stale checkmark
        // before the next WorkManager worker starts.
        // The 4-second timestamp in widget provideContent is a safety net for the
        // WorkManager path (non-running / MIUI background sessions).
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().also {
                it.remove(pendingCompleteKey)
                it.remove(pendingCompleteTimestamp)
            }
        }
        refreshAll(context)   // MIUI safety: force refresh so cleared state reaches all widgets
    }
}

/**
 * Toggles isExpanded on a task and refreshes all widget instances.
 * Same synchronous-refresh rationale as [CompleteTaskAction].
 */
class ToggleExpandAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[taskIdKey] ?: return
        val expand = parameters[expandKey] ?: return
        withContext(Dispatchers.IO) { repo(context).toggleExpanded(taskId, expand) }
        refreshAll(context)
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

/**
 * Refresh all three widget types synchronously from within the action callback.
 * Glance's updateAll() enqueues a SessionWorker via WorkManager — this is
 * thread-safe and does NOT require the Main dispatcher (WorkManager is
 * thread-agnostic).  Removing withContext(Dispatchers.Main) avoids the
 * 300-530 ms UI-thread jank visible in PerfMonitor doFrame warnings.
 */
private suspend fun refreshAll(context: Context) {
    UpcomingWidget().updateAll(context)
    FolderWidget().updateAll(context)
    TaskListWidget().updateAll(context)
}
