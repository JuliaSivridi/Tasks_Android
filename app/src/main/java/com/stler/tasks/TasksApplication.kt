package com.stler.tasks

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.stler.tasks.sync.SyncManager
import com.stler.tasks.widget.WidgetRefresher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TasksApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var widgetRefresher: WidgetRefresher

    // Manual DI container retained for non-Hilt singletons (if any).
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Schedule the 30-minute periodic sync
        syncManager.initialize()
        // Re-register Glance sessions after process restart so pending SessionWorker
        // jobs scheduled by WorkManager can find the sessions and render widget content.
        widgetRefresher.refreshAll()
    }

    /** Hilt injects its WorkerFactory so @HiltWorker classes get their deps. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
