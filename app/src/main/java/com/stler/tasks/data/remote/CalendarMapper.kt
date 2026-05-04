package com.stler.tasks.data.remote

import com.stler.tasks.data.local.entity.CalendarEventEntity
import com.stler.tasks.data.remote.dto.CalendarEventDto

/**
 * Maps Calendar API DTOs to Room entities.
 *
 * RFC3339 parsing strategy:
 *  - Timed events:   dateTime = "2026-04-26T14:00:00+03:00"
 *                    → startDate = "2026-04-26", startTime = "14:00"
 *  - All-day events: date      = "2026-04-26"
 *                    → startDate = "2026-04-26", startTime = ""
 */
object CalendarMapper {

    fun dtoToEntity(
        dto: CalendarEventDto,
        calendarId: String,
        calendarName: String,
        calendarColor: String,
    ): CalendarEventEntity? {
        val id = dto.id.ifBlank { return null }
        val title = dto.summary?.ifBlank { "(No title)" } ?: "(No title)"

        val isAllDay = dto.start.dateTime == null

        val startDate: String
        val startTime: String
        val endDate: String
        val endTime: String

        if (isAllDay) {
            startDate = dto.start.date ?: return null
            startTime = ""
            endDate   = dto.end.date ?: startDate
            endTime   = ""
        } else {
            val startDt = dto.start.dateTime ?: return null
            val endDt   = dto.end.dateTime   ?: return null
            startDate = startDt.take(10)
            startTime = if (startDt.length >= 16) startDt.substring(11, 16) else ""
            endDate   = endDt.take(10)
            endTime   = if (endDt.length >= 16) endDt.substring(11, 16) else ""
        }

        return CalendarEventEntity(
            id               = id,
            calendarId       = calendarId,
            calendarName     = calendarName,
            calendarColor    = calendarColor.ifBlank { "#4285f4" },
            title            = title,
            startDate        = startDate,
            startTime        = startTime,
            endDate          = endDate,
            endTime          = endTime,
            isAllDay         = isAllDay,
            recurringEventId = dto.recurringEventId ?: "",
        )
    }

    /** Formats a LocalDate as "YYYY-MM-DDT00:00:00Z" for use as RFC3339 timeMin/timeMax. */
    fun toRfc3339(date: java.time.LocalDate): String = "${date}T00:00:00Z"
}
