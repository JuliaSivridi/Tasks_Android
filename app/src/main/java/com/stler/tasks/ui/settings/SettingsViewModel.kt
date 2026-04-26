package com.stler.tasks.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stler.tasks.auth.AuthPreferences
import com.stler.tasks.auth.GoogleAuthRepository
import com.stler.tasks.data.local.dao.FolderDao
import com.stler.tasks.data.local.dao.LabelDao
import com.stler.tasks.data.local.dao.SyncQueueDao
import com.stler.tasks.data.local.dao.TaskDao
import com.stler.tasks.data.remote.dto.DriveFile
import com.stler.tasks.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val googleAuthRepository: GoogleAuthRepository,
    private val taskDao: TaskDao,
    private val folderDao: FolderDao,
    private val labelDao: LabelDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncManager: SyncManager,
) : ViewModel() {

    val spreadsheetId: StateFlow<String> = authPreferences.spreadsheetId
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val spreadsheetName: StateFlow<String> = authPreferences.spreadsheetName
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _files     = MutableStateFlow<List<DriveFile>>(emptyList())
    private val _loading   = MutableStateFlow(false)
    private val _switching = MutableStateFlow(false)

    val files:     StateFlow<List<DriveFile>> = _files.asStateFlow()
    val loading:   StateFlow<Boolean>          = _loading.asStateFlow()
    val switching: StateFlow<Boolean>          = _switching.asStateFlow()

    /** Loads all Google Sheets from the user's Drive (called when the picker expands). */
    fun loadFiles() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _files.value = googleAuthRepository.listUserSheets()
            } catch (e: Exception) {
                Log.e("SettingsVM", "loadFiles error", e)
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Switches the active spreadsheet:
     * 1. Saves new spreadsheetId + name to DataStore
     * 2. Clears all local Room data
     * 3. Triggers a sync to pull fresh data from the new spreadsheet
     */
    fun switchSpreadsheet(file: DriveFile) {
        if (file.id == spreadsheetId.value) return
        viewModelScope.launch {
            _switching.value = true
            try {
                authPreferences.setSpreadsheet(file.id, file.name)
                taskDao.deleteAll()
                folderDao.deleteAll()
                labelDao.deleteAll()
                syncQueueDao.deleteAll()
                syncManager.triggerSync()
            } catch (e: Exception) {
                Log.e("SettingsVM", "switchSpreadsheet error", e)
            } finally {
                _switching.value = false
            }
        }
    }
}
