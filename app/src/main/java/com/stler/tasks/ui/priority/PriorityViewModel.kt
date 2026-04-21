package com.stler.tasks.ui.priority

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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PriorityViewModel @Inject constructor(
    private val repository: TaskRepository,
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = repository.observeAllPendingTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val labels: StateFlow<List<Label>> = repository.observeLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPriority = MutableStateFlow(Priority.URGENT)
    val selectedPriority: StateFlow<Priority> = _selectedPriority.asStateFlow()

    val filteredTasks: StateFlow<List<Task>> = combine(tasks, _selectedPriority) { list, p ->
        list.filter { it.priority == p }
            .sortedWith(
                compareBy(
                    { if (it.deadlineDate.isBlank()) 1 else 0 },
                    { it.deadlineDate },
                    { if (it.deadlineTime.isBlank()) 1 else 0 },
                    { it.deadlineTime },
                    { it.createdAt },
                )
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectPriority(p: Priority) {
        _selectedPriority.value = p
    }

    fun completeTask(id: String) = viewModelScope.launch { repository.completeTask(id) }

    fun deleteTask(id: String) = viewModelScope.launch { repository.deleteTask(id) }

    fun updateDeadline(
        id         : String,
        date       : String,
        time       : String,
        isRecurring: Boolean,
        recurType  : com.stler.tasks.domain.model.RecurType,
        recurValue : Int,
    ) = viewModelScope.launch {
        val t = tasks.value.find { it.id == id } ?: return@launch
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

    fun updatePriority(id: String, p: Priority) = viewModelScope.launch {
        val t = tasks.value.find { it.id == id } ?: return@launch
        repository.updateTask(t.copy(priority = p, updatedAt = nowIso()))
    }

    fun updateLabels(id: String, lbls: List<String>) = viewModelScope.launch {
        val t = tasks.value.find { it.id == id } ?: return@launch
        repository.updateTask(t.copy(labels = lbls, updatedAt = nowIso()))
    }

    private fun nowIso() = java.time.Instant.now().toString()
}
