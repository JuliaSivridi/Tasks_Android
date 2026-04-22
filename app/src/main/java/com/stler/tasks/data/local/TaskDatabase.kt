package com.stler.tasks.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.stler.tasks.data.local.dao.FolderDao
import com.stler.tasks.data.local.dao.LabelDao
import com.stler.tasks.data.local.dao.SyncQueueDao
import com.stler.tasks.data.local.dao.TaskDao
import com.stler.tasks.data.local.entity.FolderEntity
import com.stler.tasks.data.local.entity.LabelEntity
import com.stler.tasks.data.local.entity.SyncQueueEntity
import com.stler.tasks.data.local.entity.TaskEntity

@Database(
    entities = [
        TaskEntity::class,
        FolderEntity::class,
        LabelEntity::class,
        SyncQueueEntity::class,
    ],
    version = 3,   // v3: labels.sort_order added
    exportSchema = true,
)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun folderDao(): FolderDao
    abstract fun labelDao(): LabelDao
    abstract fun syncQueueDao(): SyncQueueDao
}
