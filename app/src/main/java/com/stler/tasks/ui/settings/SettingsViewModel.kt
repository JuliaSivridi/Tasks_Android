package com.stler.tasks.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stler.tasks.auth.AuthPreferences
import com.stler.tasks.auth.FeatureFlags
import com.stler.tasks.auth.GoogleAuthRepository
import com.stler.tasks.data.local.dao.FolderDao
import com.stler.tasks.data.local.dao.LabelDao
import com.stler.tasks.data.local.dao.SyncQueueDao
import com.stler.tasks.data.local.dao.TaskDao
import com.stler.tasks.data.remote.dto.DriveFile
import com.stler.tasks.data.repository.CalendarRepository
import com.stler.tasks.data.repository.TaskRepository
import com.stler.tasks.domain.model.CalendarItem
import com.stler.tasks.domain.model.Label
import com.stler.tasks.sync.SyncManager
import com.stler.tasks.widget.CalendarWidgetReceiver
import com.stler.tasks.widget.FolderWidgetReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private fun generateId(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(8)}"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authPreferences: AuthPreferences,
    private val googleAuthRepository: GoogleAuthRepository,
    private val taskRepository: TaskRepository,
    private val taskDao: TaskDao,
    private val folderDao: FolderDao,
    private val labelDao: LabelDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncManager: SyncManager,
    private val calendarRepository: CalendarRepository,
) : ViewModel() {

    // ── Feature flags ─────────────────────────────────────────────────────

    val featureFlags: StateFlow<FeatureFlags> = authPreferences.featureFlags
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeatureFlags())

    fun setFoldersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            authPreferences.setFoldersEnabled(enabled)
            setComponentEnabled(FolderWidgetReceiver::class.java, enabled)
        }
    }

    fun setLabelsEnabled(enabled: Boolean) {
        viewModelScope.launch { authPreferences.setLabelsEnabled(enabled) }
    }

    fun setPrioritiesEnabled(enabled: Boolean) {
        viewModelScope.launch { authPreferences.setPrioritiesEnabled(enabled) }
    }

    private fun setComponentEnabled(cls: Class<*>, enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else         PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, cls),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    // ── Spreadsheet ───────────────────────────────────────────────────────

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

    fun loadFiles() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _files.value = googleAuthRepository.listUserSheets()
            } catch (e: Exception) {
                Log.e(TAG, "loadFiles error", e)
            } finally {
                _loading.value = false
            }
        }
    }

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
                Log.e(TAG, "switchSpreadsheet error", e)
            } finally {
                _switching.value = false
            }
        }
    }

    // ── Labels (managed in Settings) ──────────────────────────────────────

    val labels: StateFlow<List<Label>> = taskRepository.observeLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createLabel(name: String, color: String) {
        viewModelScope.launch {
            val nextOrder = (labels.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
            taskRepository.createLabel(
                Label(id = generateId("lbl"), name = name, color = color, sortOrder = nextOrder)
            )
        }
    }

    fun updateLabel(label: Label, name: String, color: String) {
        viewModelScope.launch { taskRepository.updateLabel(label.copy(name = name, color = color)) }
    }

    fun deleteLabel(labelId: String) {
        viewModelScope.launch { taskRepository.deleteLabel(labelId) }
    }

    // ── Calendars ─────────────────────────────────────────────────────────

    /** Convenience shortcut used by the Switch composable. */
    val calendarsEnabled: StateFlow<Boolean> = authPreferences.calendarsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setCalendarsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            authPreferences.setCalendarsEnabled(enabled)
            if (!enabled) calendarRepository.clearAllEvents()
            setComponentEnabled(CalendarWidgetReceiver::class.java, enabled)
        }
    }

    private val _calendars        = MutableStateFlow<List<CalendarItem>>(emptyList())
    private val _calendarsLoading = MutableStateFlow(false)

    val calendars:        StateFlow<List<CalendarItem>> = _calendars.asStateFlow()
    val calendarsLoading: StateFlow<Boolean>             = _calendarsLoading.asStateFlow()

    fun loadCalendars() {
        viewModelScope.launch {
            _calendarsLoading.value = true
            try {
                _calendars.value = calendarRepository.fetchCalendarsAndSave()
            } catch (e: Exception) {
                Log.e(TAG, "loadCalendars error", e)
            } finally {
                _calendarsLoading.value = false
            }
        }
    }

    fun toggleCalendar(id: String, selected: Boolean) {
        viewModelScope.launch {
            try {
                val current = authPreferences.selectedCalendarIds.first().toMutableSet()
                if (selected) current.add(id) else current.remove(id)
                calendarRepository.saveSelectedCalendarIds(current)
                _calendars.update { list ->
                    list.map { if (it.id == id) it.copy(isSelected = selected) else it }
                }
            } catch (e: Exception) {
                Log.e(TAG, "toggleCalendar error", e)
            }
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}
