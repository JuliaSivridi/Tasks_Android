package com.stler.tasks.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stler.tasks.data.local.entity.LabelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {

    @Query("SELECT * FROM labels ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM labels")
    suspend fun getAll(): List<LabelEntity>

    @Upsert
    suspend fun upsertAll(labels: List<LabelEntity>)

    @Upsert
    suspend fun upsert(label: LabelEntity)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM labels")
    suspend fun deleteAll()
}
