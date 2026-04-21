package com.stler.tasks.data.repository

import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {

    // ── Observation ───────────────────────────────────────────────────────

    /** Root pending tasks for AllTasks screen (priority → deadline → created) */
    fun observeRootPendingTasks(): Flow<List<Task>>

    /** ALL pending tasks at any depth (for AllTasks / Priority / Label screens) */
    fun observeAllPendingTasks(): Flow<List<Task>>

    /** Root pending tasks with a deadline (for Upcoming screen) */
    fun observePendingTasksWithDeadline(): Flow<List<Task>>

    /** ALL pending tasks at any depth that have a deadline (for Upcoming screen) */
    fun observeAllPendingTasksWithDeadline(): Flow<List<Task>>

    /** All pending tasks in a folder, sorted for hierarchical display */
    fun observePendingInFolder(folderId: String): Flow<List<Task>>

    /** Completed child counts per parentId in a folder (map: parentId → count). */
    fun observeCompletedChildCountsInFolder(folderId: String): Flow<Map<String, Int>>

    /** Direct pending children of a task */
    fun observeChildren(parentId: String): Flow<List<Task>>

    /** Completed tasks, newest first */
    fun observeCompletedTasks(): Flow<List<Task>>

    /** All folders (Inbox first) */
    fun observeFolders(): Flow<List<Folder>>

    /** All labels, alphabetical */
    fun observeLabels(): Flow<List<Label>>

    /** Live count of un-synced operations (drives TopAppBar badge) */
    fun observeSyncPendingCount(): Flow<Int>

    // ── Task mutations ────────────────────────────────────────────────────

    suspend fun createTask(task: Task)
    suspend fun updateTask(task: Task)
    suspend fun deleteTask(id: String)

    /** Toggle is_expanded WITHOUT bumping updated_at (spec §4.5) */
    suspend fun toggleExpanded(id: String, isExpanded: Boolean)

    /** Mark task completed; if recurring → advance deadline instead */
    suspend fun completeTask(id: String)

    /** Restore completed task to pending */
    suspend fun restoreTask(id: String)

    // ── Folder mutations ──────────────────────────────────────────────────

    suspend fun createFolder(folder: Folder)
    suspend fun updateFolder(folder: Folder)

    /** Delete folder; moves its tasks to Inbox */
    suspend fun deleteFolder(id: String)

    // ── Label mutations ───────────────────────────────────────────────────

    suspend fun createLabel(label: Label)
    suspend fun updateLabel(label: Label)
    suspend fun deleteLabel(id: String)

    // ── Sync ──────────────────────────────────────────────────────────────

    /** Pull all sheets → Room (called by SyncWorker) */
    suspend fun fetchAllAndSave(spreadsheetId: String)
}
