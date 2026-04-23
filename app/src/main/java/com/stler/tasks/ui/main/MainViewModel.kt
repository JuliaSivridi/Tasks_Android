package com.stler.tasks.ui.main

import androidx.lifecycle.viewModelScope
import com.stler.tasks.auth.AuthData
import com.stler.tasks.ui.BaseViewModel
import com.stler.tasks.auth.GoogleAuthRepository
import com.stler.tasks.data.repository.TaskRepository
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Task
import kotlinx.coroutines.flow.Flow
import com.stler.tasks.sync.SyncManager
import com.stler.tasks.sync.SyncState
import java.util.UUID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private fun generateId(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(8)}"

@HiltViewModel
class MainViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val authRepository: GoogleAuthRepository, // used for authData flow
    private val syncManager: SyncManager,
    private val sidebarPreferences: SidebarPreferences,
) : BaseViewModel() {

    /** All pending tasks at any depth — used for deeplink task lookup. */
    val allTasksForDeepLink: Flow<List<Task>> = taskRepository.observeAllPendingTasks()

    val folders: StateFlow<List<Folder>> = taskRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val labels: StateFlow<List<Label>> = taskRepository.observeLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncState: StateFlow<SyncState> = syncManager.syncState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncState.Idle)

    val authData: StateFlow<AuthData> = authRepository.authData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthData())

    val sidebarState: StateFlow<SidebarState> = sidebarPreferences.sidebarState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SidebarState())

    init {
        // Trigger an immediate sync when MainScreen first appears (i.e. user is signed in)
        syncManager.triggerSync()
    }

    fun toggleSection(section: String) = safeLaunch {
        sidebarPreferences.toggleSection(section)
    }

    fun triggerSync() = syncManager.triggerSync()

    // ── Folder CRUD ───────────────────────────────────────────────────────

    fun createFolder(name: String, color: String) = safeLaunch {
        val nextOrder = (folders.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
        taskRepository.createFolder(
            Folder(id = generateId("fld"), name = name, color = color, sortOrder = nextOrder)
        )
    }

    fun updateFolder(folder: Folder, name: String, color: String) = safeLaunch {
        taskRepository.updateFolder(folder.copy(name = name, color = color))
    }

    fun deleteFolder(folderId: String) = safeLaunch {
        taskRepository.deleteFolder(folderId)
    }

    // ── Label CRUD ────────────────────────────────────────────────────────

    fun createLabel(name: String, color: String) = safeLaunch {
        val nextOrder = (labels.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
        taskRepository.createLabel(
            Label(id = generateId("lbl"), name = name, color = color, sortOrder = nextOrder)
        )
    }

    fun updateLabel(label: Label, name: String, color: String) = safeLaunch {
        taskRepository.updateLabel(label.copy(name = name, color = color))
    }

    fun deleteLabel(labelId: String) = safeLaunch {
        taskRepository.deleteLabel(labelId)
    }
}
