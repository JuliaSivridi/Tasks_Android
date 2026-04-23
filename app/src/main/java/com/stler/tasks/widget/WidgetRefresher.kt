package com.stler.tasks.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triggers a re-render of all placed widget instances.
 * Called from TaskRepositoryImpl after every task mutation.
 *
 * ## Debounce
 * Multiple rapid mutations (e.g. complete two tasks in quick succession,
 * or reorder siblings that writes N rows) are coalesced into a single
 * Glance update 400 ms after the last call.  Without debouncing, Glance
 * can receive overlapping Worker scheduling and the second widget update
 * can silently be ignored, leaving a stale widget on-screen.
 *
 * ## Threading
 * [refreshAll] is non-suspend / fire-and-forget — callers do not block.
 * [GlanceAppWidget.updateAll] is called on [Dispatchers.Default] (the scope's
 * dispatcher); no Main thread required — Glance schedules a SessionWorker via
 * WorkManager, which is thread-safe.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope   = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val trigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow    = BufferOverflow.DROP_OLDEST,
    )

    init {
        scope.launch {
            trigger
                .debounce(400L)
                .collect { doRefresh() }
        }
    }

    /** Schedule a widget refresh. Calls within 400 ms are coalesced into one. */
    fun refreshAll() {
        trigger.tryEmit(Unit)
    }

    /**
     * Glance's updateAll() schedules a SessionWorker via WorkManager, which is
     * thread-safe.  No Main-dispatcher requirement — removing withContext(Dispatchers.Main)
     * eliminates the 300-530 ms UI-thread blocks visible in PerfMonitor doFrame logs.
     */
    private suspend fun doRefresh() {
        UpcomingWidget().updateAll(context)
        FolderWidget().updateAll(context)
        TaskListWidget().updateAll(context)
    }
}
