package com.stler.tasks.data.remote

import com.stler.tasks.data.remote.dto.CalendarEventDto
import com.stler.tasks.data.remote.dto.CalendarEventRequest
import com.stler.tasks.data.remote.dto.CalendarEventsResponse
import com.stler.tasks.data.remote.dto.CalendarListResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the Google Calendar API v3.
 * Base URL: https://www.googleapis.com/
 */
interface CalendarApi {

    /** Returns the list of calendars in the authenticated user's calendar list. */
    @GET("calendar/v3/users/me/calendarList")
    suspend fun listCalendars(): CalendarListResponse

    /**
     * Returns events in the specified calendar within the given time range.
     * [timeMin] and [timeMax] are RFC3339 timestamps (e.g. "2026-04-26T00:00:00Z").
     * [singleEvents] = true expands recurring events into individual instances.
     */
    @GET("calendar/v3/calendars/{calendarId}/events")
    suspend fun listEvents(
        @Path("calendarId") calendarId: String,
        @Query("timeMin") timeMin: String,
        @Query("timeMax") timeMax: String,
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("orderBy") orderBy: String = "startTime",
    ): CalendarEventsResponse

    /** Creates a new event in the specified calendar. */
    @POST("calendar/v3/calendars/{calendarId}/events")
    suspend fun createEvent(
        @Path("calendarId") calendarId: String,
        @Body event: CalendarEventRequest,
    ): CalendarEventDto

    /**
     * Replaces an existing event in full (PUT / events.update).
     *
     * Using PUT instead of PATCH because PATCH uses JSON merge-patch semantics and does NOT
     * reliably clear an existing `start.date` when we add `start.dateTime` (converting an
     * all-day event to a timed event). PUT replaces the whole event resource, so the new
     * start/end objects are authoritative and there is no residual all-day `date` field.
     */
    @PUT("calendar/v3/calendars/{calendarId}/events/{eventId}")
    suspend fun updateEvent(
        @Path("calendarId") calendarId: String,
        @Path("eventId")    eventId   : String,
        @Body event: CalendarEventRequest,
    ): CalendarEventDto

    /** Fetches a single event by ID (used to retrieve series base event for edit). */
    @GET("calendar/v3/calendars/{calendarId}/events/{eventId}")
    suspend fun getEvent(
        @Path("calendarId") calendarId: String,
        @Path("eventId")    eventId   : String,
    ): CalendarEventDto

    /**
     * Moves an event to a different calendar.
     * [destination] is the target calendarId (e.g. "primary" or a calendar email).
     */
    @POST("calendar/v3/calendars/{calendarId}/events/{eventId}/move")
    suspend fun moveEvent(
        @Path("calendarId")   calendarId  : String,
        @Path("eventId")      eventId     : String,
        @Query("destination") destination : String,
    ): CalendarEventDto

    /**
     * Deletes an event. Returns 204 No Content — use Response<Void> to avoid
     * JSON-converter errors on empty bodies.
     */
    @DELETE("calendar/v3/calendars/{calendarId}/events/{eventId}")
    suspend fun deleteEvent(
        @Path("calendarId") calendarId: String,
        @Path("eventId")    eventId   : String,
    ): Response<Void>
}
