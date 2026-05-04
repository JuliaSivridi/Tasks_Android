package com.stler.tasks.data.remote.dto

// ── Calendar list ─────────────────────────────────────────────────────────────

data class CalendarListResponse(
    val items: List<CalendarListEntry> = emptyList(),
)

data class CalendarListEntry(
    val id: String,
    val summary: String,
    val backgroundColor: String = "",
    val accessRole: String = "",   // "owner" | "writer" | "reader" | "freeBusyReader"
)

// ── Events list ───────────────────────────────────────────────────────────────

data class CalendarEventsResponse(
    val items: List<CalendarEventDto> = emptyList(),
)

data class CalendarEventDto(
    val id: String,
    val summary: String? = null,
    val start: EventDateTime,
    val end: EventDateTime,
    /** Non-null for instances of a recurring series; value = the series base event ID. */
    val recurringEventId: String? = null,
    /** RRULE list — only present on the series base event, not on instances. */
    val recurrence: List<String>? = null,
)

data class EventDateTime(
    val dateTime: String? = null,  // RFC3339 for timed events, e.g. "2026-04-26T14:00:00+03:00"
    val date: String? = null,      // "YYYY-MM-DD" for all-day events
    /**
     * IANA timezone name (e.g. "Europe/Helsinki").
     * Required by the Google Calendar API for recurring timed events:
     * without it the API rejects the create/update request with 400.
     * Omitted (null) for all-day events — only applicable to timed events.
     */
    val timeZone: String? = null,
)

// ── Event creation request ────────────────────────────────────────────────────

data class CalendarEventRequest(
    val summary: String,
    val start: EventDateTime,
    val end: EventDateTime,
    val recurrence: List<String>? = null,  // e.g. ["RRULE:FREQ=WEEKLY;BYDAY=TU,TH"]
)
