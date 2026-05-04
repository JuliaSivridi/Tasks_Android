package com.stler.tasks.ui.main

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sidebarDataStore by preferencesDataStore(name = "sidebar_prefs")

data class SidebarState(
    val prioritiesOpen: Boolean = true,
    val foldersOpen: Boolean = true,
    val labelsOpen: Boolean = true,
    val calendarsOpen: Boolean = true,
)

@Singleton
class SidebarPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val PRIORITIES_OPEN = booleanPreferencesKey("priorities_open")
        private val FOLDERS_OPEN    = booleanPreferencesKey("folders_open")
        private val LABELS_OPEN     = booleanPreferencesKey("labels_open")
        private val CALENDARS_OPEN  = booleanPreferencesKey("calendars_open")
    }

    val sidebarState: Flow<SidebarState> = context.sidebarDataStore.data.map { prefs ->
        SidebarState(
            prioritiesOpen = prefs[PRIORITIES_OPEN] ?: true,
            foldersOpen    = prefs[FOLDERS_OPEN]    ?: true,
            labelsOpen     = prefs[LABELS_OPEN]     ?: true,
            calendarsOpen  = prefs[CALENDARS_OPEN]  ?: true,
        )
    }

    suspend fun toggleSection(section: String) {
        context.sidebarDataStore.edit { prefs ->
            when (section) {
                "priorities" -> prefs[PRIORITIES_OPEN] = !(prefs[PRIORITIES_OPEN] ?: true)
                "folders"    -> prefs[FOLDERS_OPEN]    = !(prefs[FOLDERS_OPEN]    ?: true)
                "labels"     -> prefs[LABELS_OPEN]     = !(prefs[LABELS_OPEN]     ?: true)
                "calendars"  -> prefs[CALENDARS_OPEN]  = !(prefs[CALENDARS_OPEN]  ?: true)
            }
        }
    }
}
