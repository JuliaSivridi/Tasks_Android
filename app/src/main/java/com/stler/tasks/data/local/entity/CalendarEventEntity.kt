package com.stler.tasks.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stler.tasks.domain.model.CalendarEvent

@Entity(
    tableName = "calendar_events",
    indices = [
        Index(value = ["calendarId"]),
        Index(value = ["recurringEventId"]),
    ],
)
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val calendarId: String,
    val calendarName: String,
    val calendarColor: String,
    val title: String,
    val startDate: String,        // "YYYY-MM-DD"
    val startTime: String,        // "HH:MM" or "" for all-day
    val endDate: String,
    val endTime: String,
    val isAllDay: Boolean,
    /** Base event ID of the recurring series this instance belongs to, or "" if not recurring. */
    val recurringEventId: String = "",
) {
    fun toDomain() = CalendarEvent(
        id               = id,
        calendarId       = calendarId,
        calendarName     = calendarName,
        calendarColor    = calendarColor,
        title            = title,
        startDate        = startDate,
        startTime        = startTime,
        endDate          = endDate,
        endTime          = endTime,
        isAllDay         = isAllDay,
        recurringEventId = recurringEventId,
    )
}
