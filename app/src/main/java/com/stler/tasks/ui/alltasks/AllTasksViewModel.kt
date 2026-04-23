package com.stler.tasks.ui.alltasks

import androidx.lifecycle.viewModelScope
import com.stler.tasks.data.repository.TaskRepository
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.Task
import com.stler.tasks.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class AllTasksViewModel @Inject constructor(
    private val repository: TaskRepository,
) : BaseViewModel() {

    val tasks: StateFlow<List<Task>> = repository.observeAllPendingTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val labels: StateFlow<List<Label>> = repository.observeLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _priorityFilter = MutableStateFlow<Set<Priority>>(emptySet())
    val priorityFilter: StateFlow<Set<Priority>> = _priorityFilter.asStateFlow()

    private val _labelFilter = MutableStateFlow<Set<String>>(emptySet())
    val labelFilter: StateFlow<Set<String>> = _labelFilter.asStateFlow()

    private val _folderFilter = MutableStateFlow<Set<String>>(emptySet())
    val folderFilter: StateFlow<Set<String>> = _folderFilter.asStateFlow()

    val filteredTasks: StateFlow<List<Task>> = combine(
        tasks,
        _priorityFilter,
        _labelFilter,
        _folderFilter,
    ) { list, pf, lf, ff ->
        list.filter { task ->
            (pf.isEmpty() || task.priority in pf) &&
                (lf.isEmpty() || task.labels.any { it in lf }) &&
                (ff.isEmpty() || task.folderId in ff)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePriorityFilter(p: Priority) {
        _priorityFilter.update { if (p in it) it - p else it + p }
    }

    fun toggleLabelFilter(id: String) {
        _labelFilter.update { if (id in it) it - id else it + id }
    }

    fun toggleFolderFilter(id: String) {
        _folderFilter.update { if (id in it) it - id else it + id }
    }

    fun clearAllFilters() {
        _priorityFilter.value = emptySet()
        _labelFilter.value    = emptySet()
        _folderFilter.value   = emptySet()
    }

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
        val task = tasks.value.find { it.id == id } ?: return@safeLaunch
        repository.updateTask(
            task.copy(
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
        val task = tasks.value.find { it.id == id } ?: return@safeLaunch
        repository.updateTask(task.copy(priority = p, updatedAt = nowIso()))
    }

    fun updateLabels(id: String, lbls: List<String>) = safeLaunch {
        val task = tasks.value.find { it.id == id } ?: return@safeLaunch
        repository.updateTask(task.copy(labels = lbls, updatedAt = nowIso()))
    }

    private fun nowIso(): String = java.time.Instant.now().toString()
}
