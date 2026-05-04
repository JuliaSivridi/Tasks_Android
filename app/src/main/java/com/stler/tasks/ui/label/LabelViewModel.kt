package com.stler.tasks.ui.label

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.stler.tasks.data.repository.TaskRepository
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.Task
import com.stler.tasks.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LabelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
) : BaseViewModel() {

    // Safe fallback: show empty screen instead of crashing on missing nav arg
    private val labelId: String = savedStateHandle.get<String>("labelId") ?: run {
        Log.e("LabelViewModel", "Missing 'labelId' in SavedStateHandle — navigation bug")
        ""
    }

    val tasks: StateFlow<List<Task>> = repository.observeAllPendingTasks()
        .map { list -> list.filter { labelId in it.labels } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val labels: StateFlow<List<Label>> = repository.observeLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTasks: StateFlow<List<Task>> = tasks

    /** True until the first emission from [tasks], then false. */
    val isLoading: StateFlow<Boolean> = tasks
        .map { false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun completeTask(id: String) = safeLaunch { repository.completeTask(id) }

    fun deleteTask(id: String) = safeLaunch { repository.deleteTask(id) }

    fun updateDeadline(
        id         : String,
        date       : String,
        time       : String,
        isRecurring: Boolean,
        recurType  : com.stler.tasks.domain.model.RecurType,
        recurValue : Int,
    ) = safeLaunch {
        val t = tasks.value.find { it.id == id } ?: return@safeLaunch
        repository.updateTask(
            t.copy(
                deadlineDate = date,
                deadlineTime = time,
                isRecurring  = isRecurring,
                recurType    = recurType,
                recurValue   = recurValue,
                updatedAt    = nowIso(),
            )
        )
    }

    fun updatePriority(id: String, p: Priority) = safeLaunch {
        val t = tasks.value.find { it.id == id } ?: return@safeLaunch
        repository.updateTask(t.copy(priority = p, updatedAt = nowIso()))
    }

    fun updateLabels(id: String, lbls: List<String>) = safeLaunch {
        val t = tasks.value.find { it.id == id } ?: return@safeLaunch
        repository.updateTask(t.copy(labels = lbls, updatedAt = nowIso()))
    }

    private fun nowIso() = java.time.Instant.now().toString()
}
