package com.stler.tasks.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.stler.tasks.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue ORDER BY id")
    suspend fun getAll(): List<SyncQueueEntity>

    @Insert
    suspend fun enqueue(item: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    /** Remove items that have exhausted all retries */
    @Query("DELETE FROM sync_queue WHERE retryCount >= :maxRetries")
    suspend fun deleteExhausted(maxRetries: Int = 5)

    /** Live count of pending operations — drives the TopAppBar sync badge */
    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM sync_queue")
    suspend fun deleteAll()
}
