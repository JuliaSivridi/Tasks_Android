package com.stler.tasks.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triggers a re-render of all placed widget instances.
 * Called from TaskRepositoryImpl after every task mutation so that widgets
 * stay in sync without waiting for the 30-minute periodic update.
 *
 * Uses [GlanceAppWidget.updateAll] (Glance extension) instead of
 * per-GlanceId [update] calls — updateAll is more reliable because it
 * does not depend on [GlanceAppWidgetManager.getGlanceIds] succeeding.
 *
 * Must run on the Main dispatcher — Glance's RemoteViews pipeline requires it.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun refreshAll() = withContext(Dispatchers.Main) {
        UpcomingWidget().updateAll(context)
        FolderWidget().updateAll(context)
        TaskListWidget().updateAll(context)
    }
}
