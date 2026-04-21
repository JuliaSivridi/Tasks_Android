package com.stler.tasks.ui.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stler.tasks.data.repository.TaskRepository
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A task with its display depth, total direct-child count and completed-child count. */
data class TaskNode(val task: Task, val depth: Int, val childCount: Int, val completedChildCount: Int = 0)

@HiltViewModel
class FolderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
) : ViewModel() {

    private val folderId: String = checkNotNull(savedStateHandle["folderId"])

    private val allTasks: StateFlow<List<Task>> = repository.observePendingInFolder(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val labels: StateFlow<List<Label>> = repository.observeLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val completedCounts: StateFlow<Map<String, Int>> =
        repository.observeCompletedChildCountsInFolder(folderId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Flat display list built from the hierarchical task tree. */
    val displayList: StateFlow<List<TaskNode>> = combine(allTasks, completedCounts) { tasks, counts ->
        buildDisplayList(tasks, counts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Actions ───────────────────────────────────────────────────────────

    fun toggleExpanded(task: Task) = viewModelScope.launch {
        repository.toggleExpanded(task.id, !task.isExpanded)
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
        val t = allTasks.value.find { it.id == id } ?: return@launch
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
        val t = allTasks.value.find { it.id == id } ?: return@launch
        repository.updateTask(t.copy(priority = p, updatedAt = nowIso()))
    }

    fun updateLabels(id: String, lbls: List<String>) = viewModelScope.launch {
        val t = allTasks.value.find { it.id == id } ?: return@launch
        repository.updateTask(t.copy(labels = lbls, updatedAt = nowIso()))
    }

    /**
     * Reorder siblings within [parentId]: moves item at [fromIndex] to [toIndex]
     * in the siblings list, then rewrites sortOrder = index * 10.
     * [fromIndex] / [toIndex] are indices within the *siblings* list only.
     */
    fun reorderSiblings(parentId: String, fromIndex: Int, toIndex: Int) = viewModelScope.launch {
        val siblings = allTasks.value
            .filter { it.parentId == parentId }
            .sortedBy { it.sortOrder }
            .toMutableList()
        if (fromIndex !in siblings.indices || toIndex !in siblings.indices) return@launch
        val moved = siblings.removeAt(fromIndex)
        siblings.add(toIndex, moved)
        siblings.forEachIndexed { i, task ->
            repository.updateTask(task.copy(sortOrder = i * 10, updatedAt = nowIso()))
        }
    }

    /**
     * Reparent [taskId] to [newParentId] (empty string = root).
     * Appends to end of new parent's children.
     */
    fun reparentTask(taskId: String, newParentId: String) = viewModelScope.launch {
        val task = allTasks.value.find { it.id == taskId } ?: return@launch
        val newSiblings = allTasks.value.filter { it.parentId == newParentId }
        val newSortOrder = (newSiblings.maxOfOrNull { it.sortOrder } ?: -10) + 10
        repository.updateTask(
            task.copy(parentId = newParentId, sortOrder = newSortOrder, updatedAt = nowIso())
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun buildDisplayList(tasks: List<Task>, completedCounts: Map<String, Int>): List<TaskNode> {
        val byParent = tasks.groupBy { it.parentId }
        val result = mutableListOf<TaskNode>()

        fun addTask(task: Task, depth: Int) {
            val children = byParent[task.id] ?: emptyList()
            val completedCount = completedCounts[task.id] ?: 0
            result.add(TaskNode(task, depth, children.size, completedCount))
            if (task.isExpanded) {
                children.sortedBy { it.sortOrder }
                    .forEach { child -> addTask(child, depth + 1) }
            }
        }

        byParent[""]?.sortedBy { it.sortOrder }?.forEach { root -> addTask(root, 0) }
        return result
    }

    private fun nowIso() = java.time.Instant.now().toString()
}
