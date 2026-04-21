package com.stler.tasks.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stler.tasks.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    /** All folders; Inbox always first, then by sortOrder */
    @Query("""
        SELECT * FROM folders
        ORDER BY CASE id WHEN 'fld-inbox' THEN 0 ELSE 1 END, sortOrder
    """)
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Upsert
    suspend fun upsertAll(folders: List<FolderEntity>)

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}
