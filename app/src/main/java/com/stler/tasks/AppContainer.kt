package com.stler.tasks

import android.content.Context

/**
 * Manual dependency injection container.
 * Holds and provides singleton instances across the app.
 * Populated in stages as we implement each layer.
 */
class AppContainer(private val context: Context) {
    // Stage 2: data layer singletons will be added here (Room, Retrofit, Repository)
    // Stage 3: auth components will be added here
    // Stage 4: SyncWorker scheduling will be set up here
}
