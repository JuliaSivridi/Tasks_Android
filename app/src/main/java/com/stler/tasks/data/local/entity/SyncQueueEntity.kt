package com.stler.tasks.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Pending Sheets API operations queued while offline or before push.
 * Drained by SyncWorker during the push phase.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,   // "task" | "folder" | "label" | "settings"
    val operation: String,    // "INSERT" | "UPDATE" | "DELETE"
    val entityId: String,
    val payloadJson: String,  // serialized entity (empty for DELETE)
    val retryCount: Int = 0,
)
