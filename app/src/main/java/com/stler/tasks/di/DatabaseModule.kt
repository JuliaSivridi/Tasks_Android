package com.stler.tasks.di

import android.content.Context
import androidx.room.Room
import com.stler.tasks.data.local.TaskDatabase
import com.stler.tasks.data.local.dao.CalendarEventDao
import com.stler.tasks.data.local.dao.FolderDao
import com.stler.tasks.data.local.dao.LabelDao
import com.stler.tasks.data.local.dao.SyncQueueDao
import com.stler.tasks.data.local.dao.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TaskDatabase =
        Room.databaseBuilder(context, TaskDatabase::class.java, "tasks.db")
            .addMigrations(TaskDatabase.MIGRATION_4_5)
            .fallbackToDestructiveMigration(dropAllTables = true)  // safety net for unexpected version gaps
            .build()

    @Provides fun provideTaskDao(db: TaskDatabase): TaskDao = db.taskDao()
    @Provides fun provideFolderDao(db: TaskDatabase): FolderDao = db.folderDao()
    @Provides fun provideLabelDao(db: TaskDatabase): LabelDao = db.labelDao()
    @Provides fun provideSyncQueueDao(db: TaskDatabase): SyncQueueDao = db.syncQueueDao()
    @Provides fun provideCalendarEventDao(db: TaskDatabase): CalendarEventDao = db.calendarEventDao()
}
