package com.stler.tasks.domain.model

/** Unified list item for screens that show tasks and calendar events together. */
sealed class ListItem {
    data class TaskItem(val task: Task) : ListItem()
    data class EventItem(val event: CalendarEvent) : ListItem()
}
