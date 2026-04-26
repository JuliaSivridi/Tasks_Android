package com.stler.tasks.data.repository

import com.stler.tasks.data.remote.dto.CalendarEventRequest
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.domain.model.CalendarItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface CalendarRepository {

    /**
     * Room-backed Flow of events for the given calendar IDs, filtered to the [from]..[to] range.
     * Returns an empty flow immediately if [calendarIds] is empty.
     */
    fun getEventsForCalendars(
        calendarIds: Set<String>,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<CalendarEvent>>

    /** DataStore-backed Flow of the user's selected calendar IDs. */
    fun getSelectedCalendarIds(): Flow<Set<String>>

    /**
     * Fetches the calendar list from the API and returns it as domain objects.
     * Does NOT persist calendars to Room (transient — just for display).
     */
    suspend fun fetchCalendarsAndSave(): List<CalendarItem>

    /**
     * Fetches events from the API for all [calendarIds] in [from]..[to] range
     * and upserts them into Room.  Clears stale events per calendar before inserting.
     */
    suspend fun fetchEventsAndSave(
        calendarIds: Set<String>,
        from: LocalDate,
        to: LocalDate,
    )

    /** Creates a new event via the Calendar API. */
    suspend fun createEvent(
        calendarId: String,
        event: CalendarEventRequest,
    ): Result<CalendarEvent>

    /** Persists the user's selected calendar ID set to DataStore. */
    suspend fun saveSelectedCalendarIds(ids: Set<String>)
}
