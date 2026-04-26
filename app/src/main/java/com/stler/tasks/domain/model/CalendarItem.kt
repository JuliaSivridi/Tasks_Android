package com.stler.tasks.domain.model

data class CalendarItem(
    val id: String,           // Google Calendar ID (e.g. "primary")
    val summary: String,      // calendar display name
    val color: String,        // hex (backgroundColor from API, e.g. "#4285f4")
    val isSelected: Boolean,  // user preference
)
