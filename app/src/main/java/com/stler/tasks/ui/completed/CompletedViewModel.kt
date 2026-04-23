package com.stler.tasks.ui.completed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stler.tasks.data.repository.TaskRepository
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompletedViewModel @Inject constructor(
    private val repository: TaskRepository,
) : ViewModel() {

    private val allTasks: StateFlow<List<Task>> = repository.observeCompletedTasks()
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
        allTasks, _priorityFilter, _labelFilter, _folderFilter
    ) { list, pf, lf, ff ->
        list.filter { task ->
            (pf.isEmpty() || task.priority in pf) &&
                (lf.isEmpty() || task.labels.any { it in lf }) &&
                (ff.isEmpty() || task.folderId in ff)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePriorityFilter(p: Priority) =
        _priorityFilter.update { if (p in it) it - p else it + p }

    fun toggleLabelFilter(id: String) =
        _labelFilter.update { if (id in it) it - id else it + id }

    fun toggleFolderFilter(id: String) =
        _folderFilter.update { if (id in it) it - id else it + id }

    fun clearAllFilters() {
        _priorityFilter.value = emptySet()
        _labelFilter.value    = emptySet()
        _folderFilter.value   = emptySet()
    }

    fun restoreTask(id: String) = viewModelScope.launch { repository.restoreTask(id) }

    fun deleteTask(id: String) = viewModelScope.launch { repository.deleteTask(id) }
}
