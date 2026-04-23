package com.stler.tasks.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.RecurType
import com.stler.tasks.domain.model.Task
import com.stler.tasks.domain.model.TaskStatus

@Entity(
    tableName = "tasks",
    indices = [
        Index("parentId"),      // hierarchical queries (children, softDelete recursion)
        Index("folderId"),      // folder screen + deleteFolder migration
        Index("status"),        // pending / completed / deleted filter on every screen
        Index("deadlineDate"),  // upcoming screen + widget deadline filter
    ],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val parentId: String = "",
    val folderId: String = "fld-inbox",
    val title: String,
    val status: String = "pending",       // "pending" | "completed"
    val priority: String = "normal",      // "urgent" | "important" | "normal"
    val deadlineDate: String = "",        // "YYYY-MM-DD" or ""
    val deadlineTime: String = "",        // "HH:MM" or ""
    val isRecurring: Boolean = false,
    val recurType: String = "",           // "days" | "weeks" | "months" | ""
    val recurValue: Int = 1,
    val labels: String = "",              // comma-separated label IDs
    val sortOrder: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val completedAt: String = "",
    val isExpanded: Boolean = false,
)

fun TaskEntity.toDomain() = Task(
    id = id,
    parentId = parentId,
    folderId = folderId,
    title = title,
    status = if (status == "completed") TaskStatus.COMPLETED else TaskStatus.PENDING,
    priority = when (priority) {
        "urgent" -> Priority.URGENT
        "important" -> Priority.IMPORTANT
        else -> Priority.NORMAL
    },
    deadlineDate = deadlineDate,
    deadlineTime = deadlineTime,
    isRecurring = isRecurring,
    recurType = when (recurType) {
        "days" -> RecurType.DAYS
        "weeks" -> RecurType.WEEKS
        "months" -> RecurType.MONTHS
        else -> RecurType.NONE
    },
    recurValue = recurValue,
    labels = if (labels.isBlank()) emptyList() else labels.split(",").map { it.trim() },
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    isExpanded = isExpanded,
)

fun Task.toEntity() = TaskEntity(
    id = id,
    parentId = parentId,
    folderId = folderId,
    title = title,
    status = status.name.lowercase(),
    priority = priority.name.lowercase(),
    deadlineDate = deadlineDate,
    deadlineTime = deadlineTime,
    isRecurring = isRecurring,
    recurType = recurType.name.lowercase().let { if (it == "none") "" else it },
    recurValue = recurValue,
    labels = labels.joinToString(","),
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    isExpanded = isExpanded,
)
