package com.stler.tasks.domain.model

data class Task(
    val id: String,
    val parentId: String = "",
    val folderId: String = "fld-inbox",
    val title: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: Priority = Priority.NORMAL,
    val deadlineDate: String = "",   // "YYYY-MM-DD" or ""
    val deadlineTime: String = "",   // "HH:MM" or ""
    val isRecurring: Boolean = false,
    val recurType: RecurType = RecurType.NONE,
    val recurValue: Int = 1,
    val labels: List<String> = emptyList(), // list of label IDs
    val sortOrder: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val completedAt: String = "",
    val isExpanded: Boolean = false,
) {
    val isRoot: Boolean get() = parentId.isEmpty()
}

enum class TaskStatus { PENDING, COMPLETED }

enum class Priority { URGENT, IMPORTANT, NORMAL }

enum class RecurType { NONE, DAYS, WEEKS, MONTHS }
