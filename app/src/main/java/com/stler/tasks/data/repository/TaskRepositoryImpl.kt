package com.stler.tasks.data.repository

import com.google.gson.Gson
import com.stler.tasks.data.local.dao.FolderDao
import com.stler.tasks.data.local.dao.LabelDao
import com.stler.tasks.data.local.dao.SyncQueueDao
import com.stler.tasks.data.local.dao.TaskDao
import com.stler.tasks.data.local.entity.SyncQueueEntity
import com.stler.tasks.data.local.entity.toDomain
import com.stler.tasks.data.local.entity.toEntity
import com.stler.tasks.data.remote.SheetsApi
import com.stler.tasks.data.remote.SheetsMapper
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.RecurType
import com.stler.tasks.domain.model.Task
import com.stler.tasks.widget.WidgetRefresher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val folderDao: FolderDao,
    private val labelDao: LabelDao,
    private val syncQueueDao: SyncQueueDao,
    private val sheetsApi: SheetsApi,
    private val mapper: SheetsMapper,
    private val gson: Gson,
    private val widgetRefresher: WidgetRefresher,
) : TaskRepository {

    // ── Observation ───────────────────────────────────────────────────────

    override fun observeRootPendingTasks(): Flow<List<Task>> =
        taskDao.observeRootPending().map { it.map { e -> e.toDomain() } }

    override fun observeAllPendingTasks(): Flow<List<Task>> =
        taskDao.observeAllPending().map { it.map { e -> e.toDomain() } }

    override fun observePendingTasksWithDeadline(): Flow<List<Task>> =
        taskDao.observePendingWithDeadline().map { it.map { e -> e.toDomain() } }

    override fun observeAllPendingTasksWithDeadline(): Flow<List<Task>> =
        taskDao.observeAllPendingWithDeadline().map { it.map { e -> e.toDomain() } }

    override fun observePendingInFolder(folderId: String): Flow<List<Task>> =
        taskDao.observeByFolder(folderId).map { it.map { e -> e.toDomain() } }

    override fun observeCompletedChildCountsInFolder(folderId: String): Flow<Map<String, Int>> =
        taskDao.observeCompletedChildCounts(folderId).map { list -> list.associate { it.parentId to it.cnt } }

    override fun observeChildren(parentId: String): Flow<List<Task>> =
        taskDao.observeChildren(parentId).map { it.map { e -> e.toDomain() } }

    override fun observeCompletedTasks(): Flow<List<Task>> =
        taskDao.observeCompleted().map { it.map { e -> e.toDomain() } }

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun observeLabels(): Flow<List<Label>> =
        labelDao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun observeSyncPendingCount(): Flow<Int> =
        syncQueueDao.observePendingCount()

    // ── Task mutations ────────────────────────────────────────────────────

    override suspend fun createTask(task: Task) {
        val entity = task.toEntity()
        taskDao.upsert(entity)
        enqueue("task", "INSERT", task.id, entity)
        widgetRefresher.refreshAll()   // debounced, fire-and-forget
    }

    override suspend fun updateTask(task: Task) {
        val entity = task.toEntity()
        taskDao.upsert(entity)
        enqueue("task", "UPDATE", task.id, entity)
        widgetRefresher.refreshAll()
    }

    override suspend fun updateTasks(tasks: List<Task>) {
        if (tasks.isEmpty()) return
        val entities = tasks.map { it.toEntity() }
        taskDao.upsertAll(entities)                           // single transaction
        entities.forEach { enqueue("task", "UPDATE", it.id, it) }
        widgetRefresher.refreshAll()
    }

    override suspend fun deleteTask(id: String) {
        val entity = taskDao.getById(id) ?: return
        val now    = nowIso()
        val deleted = entity.copy(status = "deleted", updatedAt = now)
        taskDao.upsert(deleted)
        enqueue("task", "UPDATE", id, deleted)
        softDeleteDescendants(id, now)
        widgetRefresher.refreshAll()
    }

    /** Recursively marks all non-deleted children of [parentId] as deleted. */
    private suspend fun softDeleteDescendants(parentId: String, now: String) {
        val children = taskDao.getAll().filter { it.parentId == parentId && it.status != "deleted" }
        for (child in children) {
            val deleted = child.copy(status = "deleted", updatedAt = now)
            taskDao.upsert(deleted)
            enqueue("task", "UPDATE", child.id, deleted)
            softDeleteDescendants(child.id, now)
        }
    }

    override suspend fun toggleExpanded(id: String, isExpanded: Boolean) {
        val entity = taskDao.getById(id) ?: return
        // Copy isExpanded only — do NOT touch updatedAt (spec §4.5)
        val updated = entity.copy(isExpanded = isExpanded)
        taskDao.upsert(updated)
        enqueue("task", "UPDATE", id, updated)
        widgetRefresher.refreshAll()
    }

    override suspend fun completeTask(id: String) {
        val entity = taskDao.getById(id) ?: return
        val now = nowIso()
        if (entity.isRecurring) {
            // Recurring: advance deadline, do NOT mark done, leave subtasks untouched
            val advanced = advanceDeadline(entity, now)
            taskDao.upsert(advanced)
            enqueue("task", "UPDATE", id, advanced)
        } else {
            val updated = entity.copy(status = "completed", completedAt = now, updatedAt = now)
            taskDao.upsert(updated)
            enqueue("task", "UPDATE", id, updated)
            // Also complete all descendants at every depth (mirrors PWA behaviour)
            completeDescendants(id, now)
        }
        widgetRefresher.refreshAll()
    }

    /** Recursively marks all pending children of [parentId] as completed (skips deleted). */
    private suspend fun completeDescendants(parentId: String, now: String) {
        val children = taskDao.getAll().filter { it.parentId == parentId && it.status != "deleted" }
        for (child in children) {
            if (child.status != "completed") {
                val updated = child.copy(status = "completed", completedAt = now, updatedAt = now)
                taskDao.upsert(updated)
                enqueue("task", "UPDATE", child.id, updated)
            }
            completeDescendants(child.id, now)   // recurse regardless (subtasks may have their own children)
        }
    }

    override suspend fun restoreTask(id: String) {
        val entity = taskDao.getById(id) ?: return
        val updated = entity.copy(status = "pending", completedAt = "", updatedAt = nowIso())
        taskDao.upsert(updated)
        enqueue("task", "UPDATE", id, updated)
    }

    // ── Folder mutations ──────────────────────────────────────────────────

    override suspend fun createFolder(folder: Folder) {
        val entity = folder.toEntity()
        folderDao.upsert(entity)
        enqueue("folder", "INSERT", folder.id, entity)
    }

    override suspend fun updateFolder(folder: Folder) {
        val entity = folder.toEntity()
        folderDao.upsert(entity)
        enqueue("folder", "UPDATE", folder.id, entity)
    }

    override suspend fun deleteFolder(id: String) {
        // Move non-deleted tasks to Inbox
        taskDao.getAll()
            .filter { it.folderId == id && it.status != "deleted" }
            .forEach { task ->
                val moved = task.copy(folderId = "fld-inbox", updatedAt = nowIso())
                taskDao.upsert(moved)
                enqueue("task", "UPDATE", task.id, moved)
            }
        folderDao.deleteById(id)
        enqueue("folder", "DELETE", id, null)
    }

    // ── Label mutations ───────────────────────────────────────────────────

    override suspend fun createLabel(label: Label) {
        val entity = label.toEntity()
        labelDao.upsert(entity)
        enqueue("label", "INSERT", label.id, entity)
    }

    override suspend fun updateLabel(label: Label) {
        val entity = label.toEntity()
        labelDao.upsert(entity)
        enqueue("label", "UPDATE", label.id, entity)
    }

    override suspend fun deleteLabel(id: String) {
        labelDao.deleteById(id)
        enqueue("label", "DELETE", id, null)
    }

    // ── Sync ──────────────────────────────────────────────────────────────

    override suspend fun fetchAllAndSave(spreadsheetId: String) {
        // Collect entity IDs that still have unsent local changes in the queue.
        // These rows must NOT be overwritten by the Sheets pull — the local edit wins.
        // (This prevents a race where the user modifies a task between the push phase
        // and the pull phase of the same SyncWorker run.)
        val pendingIds = syncQueueDao.getAll().map { it.entityId }.toSet()

        val response = sheetsApi.batchGet(
            spreadsheetId = spreadsheetId,
            ranges = listOf("tasks", "folders", "labels"),
        )
        val ranges = response.valueRanges

        ranges.getOrNull(0)?.values?.drop(1)
            ?.mapNotNull { mapper.rowToTask(it) }
            ?.filter { it.id !in pendingIds }
            ?.let { taskDao.upsertAll(it) }

        ranges.getOrNull(1)?.values?.drop(1)
            ?.mapNotNull { mapper.rowToFolder(it) }
            ?.filter { it.id !in pendingIds }
            ?.let { folderDao.upsertAll(it) }

        ranges.getOrNull(2)?.values?.drop(1)
            ?.mapNotNull { mapper.rowToLabel(it) }
            ?.filter { it.id !in pendingIds }
            ?.let { labelDao.upsertAll(it) }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private suspend fun enqueue(type: String, op: String, id: String, payload: Any?) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = type,
                operation = op,
                entityId = id,
                payloadJson = if (payload != null) gson.toJson(payload) else "",
            )
        )
    }

    private fun nowIso(): String = Instant.now().toString()

    /** Advance deadline by recurValue × recurType. Full logic in Stage 8. */
    private fun advanceDeadline(entity: com.stler.tasks.data.local.entity.TaskEntity, now: String): com.stler.tasks.data.local.entity.TaskEntity {
        if (entity.deadlineDate.isBlank()) return entity.copy(updatedAt = now)
        return try {
            val current = LocalDate.parse(entity.deadlineDate, DateTimeFormatter.ISO_LOCAL_DATE)
            val next = when (entity.recurType) {
                "days" -> current.plusDays(entity.recurValue.toLong())
                "weeks" -> current.plusWeeks(entity.recurValue.toLong())
                "months" -> current.plusMonths(entity.recurValue.toLong())
                else -> current.plusDays(1)
            }
            entity.copy(deadlineDate = next.format(DateTimeFormatter.ISO_LOCAL_DATE), updatedAt = now)
        } catch (_: Exception) {
            entity.copy(updatedAt = now)
        }
    }
}
