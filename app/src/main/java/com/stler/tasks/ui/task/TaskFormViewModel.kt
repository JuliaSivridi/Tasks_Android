package com.stler.tasks.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stler.tasks.data.repository.TaskRepository
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.RecurType
import com.stler.tasks.domain.model.Task
import com.stler.tasks.domain.model.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Generates a short ID matching PWA format: prefix_XXXXXXXX (8 lowercase hex chars). */
private fun generateId(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(8)}"

/**
 * Handles create / edit / "add subtask" task form submissions.
 * Injected via hiltViewModel() in any screen that needs it.
 *
 * Label sentinel: "__new__:colorHex:name" — creates a new label first, then attaches it.
 */
@HiltViewModel
class TaskFormViewModel @Inject constructor(
    private val repository: TaskRepository,
) : ViewModel() {

    /**
     * Creates a new task (and any new labels embedded as sentinels in [result.labelIds]).
     */
    fun createTask(result: TaskFormResult, sortOrder: Int = 0) = viewModelScope.launch {
        val resolvedLabelIds = resolveLabelSentinels(result.labelIds)
        val now = nowIso()
        repository.createTask(
            Task(
                id           = generateId("tsk"),
                parentId     = result.parentId,
                folderId     = result.folderId,
                title        = result.title,
                status       = TaskStatus.PENDING,
                priority     = result.priority,
                deadlineDate = result.deadlineDate,
                deadlineTime = result.deadlineTime,
                isRecurring  = result.isRecurring,
                recurType    = result.recurType,
                recurValue   = result.recurValue,
                labels       = resolvedLabelIds,
                sortOrder    = sortOrder,
                createdAt    = now,
                updatedAt    = now,
            )
        )
    }

    /**
     * Updates an existing task in place, preserving fields not present in the form
     * (status, completedAt, isExpanded, etc.).
     */
    fun updateTask(original: Task, result: TaskFormResult) = viewModelScope.launch {
        val resolvedLabelIds = resolveLabelSentinels(result.labelIds)
        repository.updateTask(
            original.copy(
                title        = result.title,
                folderId     = result.folderId,
                parentId     = result.parentId,
                priority     = result.priority,
                labels       = resolvedLabelIds,
                deadlineDate = result.deadlineDate,
                deadlineTime = result.deadlineTime,
                isRecurring  = result.isRecurring,
                recurType    = result.recurType,
                recurValue   = result.recurValue,
                updatedAt    = nowIso(),
            )
        )
    }

    /**
     * Processes label sentinels of the form "__new__:colorHex:name".
     * For each sentinel: creates the label in Room + Sheets, then returns its real ID.
     * Normal IDs are passed through unchanged.
     */
    private suspend fun resolveLabelSentinels(labelIds: List<String>): List<String> {
        val result = mutableListOf<String>()
        for (id in labelIds) {
            if (id.startsWith("__new__:")) {
                val parts = id.split(":", limit = 3)
                if (parts.size == 3) {
                    val color = parts[1]
                    val name  = parts[2]
                    val newLabel = Label(
                        id    = generateId("lbl"),
                        name  = name,
                        color = color,
                    )
                    repository.createLabel(newLabel)
                    result.add(newLabel.id)
                }
            } else {
                result.add(id)
            }
        }
        return result
    }

    private fun nowIso(): String = Instant.now().toString()
}
