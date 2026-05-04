package com.stler.tasks.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.stler.tasks.data.local.entity.CalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CalendarEventDao {

    @Query("SELECT * FROM calendar_events WHERE calendarId IN (:calendarIds) ORDER BY startDate ASC, startTime ASC")
    abstract fun observeByCalendars(calendarIds: List<String>): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events ORDER BY startDate ASC, startTime ASC")
    abstract fun observeAll(): Flow<List<CalendarEventEntity>>

    @Upsert
    abstract suspend fun upsertAll(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE calendarId = :calendarId")
    abstract suspend fun deleteByCalendar(calendarId: String)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    /** Deletes the base event and all instances that share its [seriesId]. */
    @Query("DELETE FROM calendar_events WHERE id = :seriesId OR recurringEventId = :seriesId")
    abstract suspend fun deleteBySeriesId(seriesId: String)

    @Query("DELETE FROM calendar_events")
    abstract suspend fun deleteAll()

    /**
     * Atomically deletes all cached events for [calendarId] and inserts [entities].
     * Using @Transaction ensures no window where the calendar has zero events in Room.
     */
    @Transaction
    open suspend fun deleteAndReplace(calendarId: String, entities: List<CalendarEventEntity>) {
        deleteByCalendar(calendarId)
        if (entities.isNotEmpty()) upsertAll(entities)
    }

    /**
     * Atomically deletes the series/event by [seriesId] (base + all instances)
     * and inserts [entity] if non-null. Prevents the event from disappearing
     * from Room if the subsequent upsert fails.
     */
    @Transaction
    open suspend fun deleteSeriesAndReplace(seriesId: String, entity: CalendarEventEntity?) {
        deleteBySeriesId(seriesId)
        if (entity != null) upsertAll(listOf(entity))
    }
}
