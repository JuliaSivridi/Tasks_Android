package com.stler.tasks.data.remote

import com.stler.tasks.data.remote.dto.CalendarEventDto
import com.stler.tasks.data.remote.dto.CalendarEventRequest
import com.stler.tasks.data.remote.dto.CalendarEventsResponse
import com.stler.tasks.data.remote.dto.CalendarListResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
}
