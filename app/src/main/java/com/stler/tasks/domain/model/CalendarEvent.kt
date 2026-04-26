package com.stler.tasks.domain.model

data class CalendarEvent(
    val id: String,
    val calendarId: String,
    val calendarName: String,
    val calendarColor: String,  // hex
    val title: String,
    val startDate: String,      // "YYYY-MM-DD"
    val startTime: String,      // "HH:MM" or "" for all-day
    val endDate: String,
    val endTime: String,
    val isAllDay: Boolean,
)
