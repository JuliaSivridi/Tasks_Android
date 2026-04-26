package com.stler.tasks.data.remote.dto

// ── Calendar list ─────────────────────────────────────────────────────────────

data class CalendarListResponse(
    val items: List<CalendarListEntry> = emptyList(),
)

data class CalendarListEntry(
    val id: String,
    val summary: String,
    val backgroundColor: String = "",
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
)

data class EventDateTime(
    val dateTime: String? = null,  // RFC3339 for timed events, e.g. "2026-04-26T14:00:00+03:00"
    val date: String? = null,      // "YYYY-MM-DD" for all-day events
)

// ── Event creation request ────────────────────────────────────────────────────

data class CalendarEventRequest(
    val summary: String,
    val start: EventDateTime,
    val end: EventDateTime,
)
