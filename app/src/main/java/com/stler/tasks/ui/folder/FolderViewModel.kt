package com.stler.tasks.ui.folder

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.stler.tasks.data.repository.TaskRepository
import com.stler.tasks.ui.BaseViewModel
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A task with its display depth, total direct-child count and completed-child count. */
data class TaskNode(val task: Task, val depth: Int, val childCount: Int, val completedChildCount: Int = 0)

@HiltViewModel
class FolderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
) : BaseViewModel() {

    private val folderId: String = savedStateHandle.get<String>("folderId") ?: run {
        Log.e("FolderViewModel", "Missing 'folderId' in SavedStateHandle — navigation bug")
        ""
    }

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

    fun toggleExpanded(task: Task) = safeLaunch {
        repository.toggleExpanded(task.id, !task.isExpanded)
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
        val t = allTasks.value.find { it.id == id } ?: return@safeLaunch
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
        val t = allTasks.value.find { it.id == id } ?: return@safeLaunch
        repository.updateTask(t.copy(priority = p, updatedAt = nowIso()))
    }

    fun updateLabels(id: String, lbls: List<String>) = safeLaunch {
        val t = allTasks.value.find { it.id == id } ?: return@safeLaunch
        repository.updateTask(t.copy(labels = lbls, updatedAt = nowIso()))
    }

    /**
     * Reorder siblings within [parentId]: moves item at [fromIndex] to [toIndex]
     * in the siblings list, then rewrites sortOrder = index * 10.
     * [fromIndex] / [toIndex] are indices within the *siblings* list only.
     */
    /**
     * Reorder siblings within [parentId]: moves item at [fromIndex] to [toIndex]
     * in the siblings list, then rewrites sortOrder = index * 10.
     *
     * Uses a single batch DB transaction (updateTasks) so N siblings produce
     * exactly 1 DB write, 1 widget refresh, and N sync-queue entries.
     */
    fun reorderSiblings(parentId: String, fromIndex: Int, toIndex: Int) = safeLaunch {
        val siblings = allTasks.value
            .filter { it.parentId == parentId }
            .sortedBy { it.sortOrder }
            .toMutableList()
        if (fromIndex !in siblings.indices || toIndex !in siblings.indices) return@safeLaunch
        val moved = siblings.removeAt(fromIndex)
        siblings.add(toIndex, moved)
        val now     = nowIso()
        val updated = siblings.mapIndexed { i, task -> task.copy(sortOrder = i * 10, updatedAt = now) }
        repository.updateTasks(updated)
    }

    /**
     * Reparent [taskId] to [newParentId] (empty string = root).
     * Appends to end of new parent's children.
     */
    fun reparentTask(taskId: String, newParentId: String) = safeLaunch {
        val task = allTasks.value.find { it.id == taskId } ?: return@safeLaunch
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
