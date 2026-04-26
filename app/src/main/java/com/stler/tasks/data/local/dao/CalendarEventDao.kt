package com.stler.tasks.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stler.tasks.data.local.entity.CalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {

    @Query("SELECT * FROM calendar_events WHERE calendarId IN (:calendarIds) ORDER BY startDate ASC, startTime ASC")
    fun observeByCalendars(calendarIds: List<String>): Flow<List<CalendarEventEntity>>

    @Upsert
    suspend fun upsertAll(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE calendarId = :calendarId")
    suspend fun deleteByCalendar(calendarId: String)

    @Query("DELETE FROM calendar_events")
    suspend fun deleteAll()
}
