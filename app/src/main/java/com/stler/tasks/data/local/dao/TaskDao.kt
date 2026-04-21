package com.stler.tasks.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stler.tasks.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/** Room projection: parentId → count of completed children in a folder. */
data class ParentCount(val parentId: String, val cnt: Int)

@Dao
interface TaskDao {

    // --- Observation (for UI) ---

    /** All root pending tasks; sort: priority → deadline (nulls last) → created_at */
    @Query("""
        SELECT * FROM tasks
        WHERE status = 'pending' AND parentId = ''
        ORDER BY
            CASE priority WHEN 'urgent' THEN 0 WHEN 'important' THEN 1 ELSE 2 END,
            CASE WHEN deadlineDate = '' THEN 1 ELSE 0 END,
            deadlineDate,
            createdAt
    """)
    fun observeRootPending(): Flow<List<TaskEntity>>

    /** Root pending tasks that have a deadline (for Upcoming screen) */
    @Query("""
        SELECT * FROM tasks
        WHERE status = 'pending' AND parentId = '' AND deadlineDate != ''
        ORDER BY deadlineDate, createdAt
    """)
    fun observePendingWithDeadline(): Flow<List<TaskEntity>>

    /** ALL pending tasks at any depth; priority → deadline date → deadline time → created */
    @Query("""
        SELECT * FROM tasks
        WHERE status = 'pending'
        ORDER BY
            CASE priority WHEN 'urgent' THEN 0 WHEN 'important' THEN 1 ELSE 2 END,
            CASE WHEN deadlineDate = '' THEN 1 ELSE 0 END,
            deadlineDate,
            CASE WHEN deadlineTime = '' THEN 1 ELSE 0 END,
            deadlineTime,
            createdAt
    """)
    fun observeAllPending(): Flow<List<TaskEntity>>

    /** ALL pending tasks at any depth that have a deadline (for Upcoming screen) */
    @Query("""
        SELECT * FROM tasks
        WHERE status = 'pending' AND deadlineDate != ''
        ORDER BY deadlineDate,
            CASE WHEN deadlineTime = '' THEN 1 ELSE 0 END,
            deadlineTime,
            createdAt
    """)
    fun observeAllPendingWithDeadline(): Flow<List<TaskEntity>>

    /** All pending tasks in a folder, ordered for hierarchical display */
    @Query("""
        SELECT * FROM tasks
        WHERE folderId = :folderId AND status = 'pending'
        ORDER BY parentId, sortOrder
    """)
    fun observeByFolder(folderId: String): Flow<List<TaskEntity>>

    /** Count of completed children per parentId in a folder (for subtask counter). */
    @Query("""
        SELECT parentId, COUNT(*) as cnt FROM tasks
        WHERE folderId = :folderId AND status = 'completed' AND parentId != ''
        GROUP BY parentId
    """)
    fun observeCompletedChildCounts(folderId: String): Flow<List<ParentCount>>

    /** Direct children of a task */
    @Query("SELECT * FROM tasks WHERE parentId = :parentId AND status = 'pending' ORDER BY sortOrder")
    fun observeChildren(parentId: String): Flow<List<TaskEntity>>

    /** Completed tasks, newest first */
    @Query("""
        SELECT * FROM tasks
        WHERE status = 'completed'
        ORDER BY CASE WHEN completedAt != '' THEN completedAt ELSE updatedAt END DESC
    """)
    fun observeCompleted(): Flow<List<TaskEntity>>

    // --- Suspend reads (for SyncWorker / repo logic) ---

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    // --- Writes ---

    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}
