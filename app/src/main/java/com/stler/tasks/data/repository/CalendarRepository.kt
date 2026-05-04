package com.stler.tasks.data.repository

import com.stler.tasks.data.remote.dto.CalendarEventRequest
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.domain.model.CalendarItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Carries the series base event's date/time/title plus the raw RRULE string. */
data class CalendarEventWithRRule(
    val event      : CalendarEvent,
    val rrule      : String,   // e.g. "RRULE:FREQ=WEEKLY;BYDAY=TU,TH" or ""
    val endTime    : String,   // "HH:MM" or ""
)

interface CalendarRepository {

    /**
     * Room-backed Flow of ALL stored events (no calendar or date filter).
     * Used for deep-link lookup where only an event ID is known.
     */
    fun getAllEvents(): Flow<List<CalendarEvent>>

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

    /**
     * Moves an event (or series) from [fromCalendarId] to [toCalendarId].
     * Returns the updated event in the new calendar.
     */
    suspend fun moveEvent(
        fromCalendarId: String,
        toCalendarId  : String,
        eventId       : String,
    ): Result<CalendarEvent>

    /** Updates an existing event via the Calendar API. */
    suspend fun updateEvent(
        calendarId: String,
        eventId   : String,
        event     : CalendarEventRequest,
    ): Result<CalendarEvent>

    /** Deletes a single event instance from the Calendar API and removes it from Room. */
    suspend fun deleteEvent(
        calendarId: String,
        eventId   : String,
    ): Result<Unit>

    /**
     * Deletes the entire recurring series: API call on the base event ID,
     * then removes all instances from Room.
     */
    suspend fun deleteEventSeries(
        calendarId: String,
        seriesId  : String,
    ): Result<Unit>

    /**
     * Fetches the series base event by [seriesId] from the API.
     * Returns the domain event with its RRULE stored in [CalendarEvent.recurrenceRule].
     */
    suspend fun getBaseEvent(
        calendarId: String,
        seriesId  : String,
    ): Result<CalendarEventWithRRule>

    /** Persists the user's selected calendar ID set to DataStore. */
    suspend fun saveSelectedCalendarIds(ids: Set<String>)
}
