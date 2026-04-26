package com.stler.tasks.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "auth_prefs")

data class AuthData(
    val userEmail: String = "",
    val userName: String = "",
    val userAvatarUrl: String = "",
    val spreadsheetId: String = "",
) {
    val isSignedIn: Boolean get() = spreadsheetId.isNotBlank()
}

@Singleton
class AuthPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val ACCESS_TOKEN      = stringPreferencesKey("access_token")
        private val TOKEN_EXPIRY      = stringPreferencesKey("token_expiry")
        private val SPREADSHEET_ID    = stringPreferencesKey("spreadsheet_id")
        private val SPREADSHEET_NAME  = stringPreferencesKey("spreadsheet_name")
        private val USER_EMAIL        = stringPreferencesKey("user_email")
        private val USER_NAME         = stringPreferencesKey("user_name")
        private val USER_AVATAR_URL        = stringPreferencesKey("user_avatar_url")
        private val SELECTED_CALENDAR_IDS  = stringSetPreferencesKey("selected_calendar_ids")
    }

    val accessToken: Flow<String>      = context.authDataStore.data.map { it[ACCESS_TOKEN]     ?: "" }
    val tokenExpiry: Flow<String>      = context.authDataStore.data.map { it[TOKEN_EXPIRY]     ?: "" }
    val spreadsheetId: Flow<String>    = context.authDataStore.data.map { it[SPREADSHEET_ID]   ?: "" }
    val spreadsheetName: Flow<String>  = context.authDataStore.data.map { it[SPREADSHEET_NAME] ?: "" }
    val userEmail: Flow<String>        = context.authDataStore.data.map { it[USER_EMAIL]       ?: "" }
    val userName: Flow<String>         = context.authDataStore.data.map { it[USER_NAME]        ?: "" }
    val userAvatarUrl: Flow<String>       = context.authDataStore.data.map { it[USER_AVATAR_URL]       ?: "" }
    val selectedCalendarIds: Flow<Set<String>> = context.authDataStore.data.map { it[SELECTED_CALENDAR_IDS] ?: emptySet() }

    suspend fun saveAll(
        accessToken: String,
        tokenExpiry: String,
        spreadsheetId: String,
        userEmail: String,
        userName: String,
        userAvatarUrl: String,
        spreadsheetName: String = "db_tasks",
    ) {
        context.authDataStore.edit { prefs ->
            prefs[ACCESS_TOKEN]     = accessToken
            prefs[TOKEN_EXPIRY]     = tokenExpiry
            prefs[SPREADSHEET_ID]   = spreadsheetId
            prefs[SPREADSHEET_NAME] = spreadsheetName
            prefs[USER_EMAIL]       = userEmail
            prefs[USER_NAME]        = userName
            prefs[USER_AVATAR_URL]  = userAvatarUrl
        }
    }

    /** Switches active spreadsheet — called from SettingsViewModel. */
    suspend fun setSpreadsheet(id: String, name: String) {
        context.authDataStore.edit { prefs ->
            prefs[SPREADSHEET_ID]   = id
            prefs[SPREADSHEET_NAME] = name
        }
    }

    suspend fun saveToken(accessToken: String, tokenExpiry: String) {
        context.authDataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = accessToken
            prefs[TOKEN_EXPIRY] = tokenExpiry
        }
    }

    suspend fun saveSelectedCalendarIds(ids: Set<String>) {
        context.authDataStore.edit { it[SELECTED_CALENDAR_IDS] = ids }
    }

    suspend fun saveSpreadsheetId(spreadsheetId: String) {
        context.authDataStore.edit { prefs ->
            prefs[SPREADSHEET_ID] = spreadsheetId
        }
    }

    suspend fun getAuthData(): AuthData = AuthData(
        userEmail     = userEmail.first(),
        userName      = userName.first(),
        userAvatarUrl = userAvatarUrl.first(),
        spreadsheetId = spreadsheetId.first(),
    )

    suspend fun clearAll() {
        context.authDataStore.edit { it.clear() }
    }
}
