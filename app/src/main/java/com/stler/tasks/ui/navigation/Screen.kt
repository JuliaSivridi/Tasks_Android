package com.stler.tasks.ui.navigation

import android.net.Uri

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
    /** URL-encodes the calendar ID so special chars like # and @ don't break routing. */
    fun calendarRoute(calendarId: String) = "calendar/${Uri.encode(calendarId)}"
}
