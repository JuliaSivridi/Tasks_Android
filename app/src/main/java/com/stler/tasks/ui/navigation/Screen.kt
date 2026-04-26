package com.stler.tasks.ui.navigation

/** Centralized route constants for Navigation Compose. */
object Screen {
    const val UPCOMING  = "upcoming"
    const val ALL_TASKS = "all_tasks"
    const val COMPLETED = "completed"
    const val FOLDER    = "folder/{folderId}"
    const val LABEL     = "label/{labelId}"
    const val PRIORITY  = "priority/{priority}"
    const val CALENDAR  = "calendar/{calendarId}"

    fun folderRoute(folderId: String)     = "folder/$folderId"
    fun labelRoute(labelId: String)       = "label/$labelId"
    fun priorityRoute(priority: String)   = "priority/$priority"
    fun calendarRoute(calendarId: String) = "calendar/$calendarId"
}
