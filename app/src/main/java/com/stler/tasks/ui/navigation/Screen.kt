package com.stler.tasks.ui.navigation

import android.net.Uri

/** Centralized route constants for Navigation Compose. */
object Screen {
    const val UPCOMING       = "upcoming"
    const val ALL_TASKS      = "all_tasks"
    const val COMPLETED      = "completed"
    const val FOLDERS_LIST   = "folders_list"
    const val CALENDARS_LIST = "calendars_list"
    const val MENU_SCREEN    = "menu_screen"
    const val FOLDER         = "folder/{folderId}"
    const val CALENDAR       = "calendar/{calendarId}"

    fun folderRoute(folderId: String)     = "folder/$folderId"
    /** URL-encodes the calendar ID so special chars like # and @ don't break routing. */
    fun calendarRoute(calendarId: String) = "calendar/${Uri.encode(calendarId)}"
}
