package com.stler.tasks.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.stler.tasks.auth.AuthPreferences
import com.stler.tasks.auth.GoogleAuthRepository
import com.stler.tasks.data.local.dao.SyncQueueDao
import com.stler.tasks.data.local.entity.FolderEntity
import com.stler.tasks.data.local.entity.LabelEntity
import com.stler.tasks.data.local.entity.SyncQueueEntity
import com.stler.tasks.data.local.entity.TaskEntity
import com.stler.tasks.data.remote.SheetsApi
import com.stler.tasks.data.remote.SheetsMapper
import com.stler.tasks.data.remote.dto.BatchUpdateValuesBody
import com.stler.tasks.data.remote.dto.ValuesBody
import com.stler.tasks.data.repository.TaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * WorkManager worker that handles bidirectional sync with Google Sheets.
 *
 * Execution order:
 *  1. Push — drain SyncQueue → Sheets API (INSERT / UPDATE / DELETE)
 *  2. Pull — batchGet all sheets → Room upsert
 *  3. Widget refresh (Stage 9)
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val taskRepository: TaskRepository,
    private val sheetsApi: SheetsApi,
    private val mapper: SheetsMapper,
    private val syncQueueDao: SyncQueueDao,
    private val authPreferences: AuthPreferences,
    private val authRepository: GoogleAuthRepository,
    private val gson: Gson,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        var spreadsheetId = authPreferences.spreadsheetId.first()

        // If spreadsheetId is missing but we have a token, try to find it via Drive API
        if (spreadsheetId.isBlank()) {
            Log.w(TAG, "spreadsheetId is blank — attempting Drive search")
            spreadsheetId = authRepository.findAndSaveSpreadsheetId()
        }

        if (spreadsheetId.isBlank()) {
            Log.w(TAG, "spreadsheetId still blank after Drive search — aborting")
            return Result.success()
        }

        return try {
            val hadPendingChanges = syncQueueDao.getAll().isNotEmpty()
            push(spreadsheetId)
            // If we just wrote data to Sheets, wait briefly before reading back.
            // The Sheets API can sometimes serve a cached (pre-write) response if a
            // batchGet arrives immediately after a batchUpdate, causing the fresh local
            // deadline to be overwritten with stale data.  A short pause makes this
            // race condition negligible in practice.
            if (hadPendingChanges) delay(1_000L)
            taskRepository.fetchAllAndSave(spreadsheetId)
            // TODO Stage 9: GlanceAppWidgetManager.getInstance(applicationContext).updateAll()
            Log.d(TAG, "Sync complete")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed (attempt ${runAttemptCount + 1}): ${e.message}", e)
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }

    // ── Push ──────────────────────────────────────────────────────────────

    private suspend fun push(spreadsheetId: String) {
        val queue = syncQueueDao.getAll()
        if (queue.isEmpty()) return

        // Cache of sheet rows fetched for row-number lookup (fetched once per sheet per push)
        val rowsCache = mutableMapOf<String, List<List<Any?>>>()

        for (item in queue) {
            runCatching { executeOperation(item, spreadsheetId, rowsCache) }
                .onSuccess { syncQueueDao.deleteById(item.id) }
                .onFailure { syncQueueDao.incrementRetry(item.id) }
        }

        syncQueueDao.deleteExhausted()   // remove items that exceeded retry limit
    }

    private suspend fun executeOperation(
        item: SyncQueueEntity,
        spreadsheetId: String,
        rowsCache: MutableMap<String, List<List<Any?>>>,
    ) {
        val sheetName = sheetOf(item.entityType)

        when (item.operation) {
            "INSERT" -> {
                // Use explicit column range (e.g. "tasks!A:Q") so Sheets' table-detection
                // is constrained to our columns only, preventing column-shift bugs when the
                // sheet has extra columns from other sources (e.g. PWA metadata).
                val appendRange = "$sheetName!A:${lastColOf(item.entityType)}"
                sheetsApi.append(
                    spreadsheetId = spreadsheetId,
                    range = appendRange,
                    body = ValuesBody(range = appendRange, values = listOf(entityRow(item))),
                )
            }
            "UPDATE" -> {
                val rows = cachedRows(sheetName, spreadsheetId, rowsCache)
                val rowNum = mapper.findRowNumber(rows, item.entityId) ?: return
                val range = "$sheetName!A$rowNum:${lastColOf(item.entityType)}$rowNum"
                sheetsApi.batchUpdate(
                    spreadsheetId,
                    BatchUpdateValuesBody(data = listOf(ValuesBody(range, values = listOf(entityRow(item))))),
                )
            }
            "DELETE" -> {
                val rows = cachedRows(sheetName, spreadsheetId, rowsCache)
                val rowNum = mapper.findRowNumber(rows, item.entityId) ?: return
                val range = "$sheetName!A$rowNum:${lastColOf(item.entityType)}$rowNum"
                sheetsApi.clear(spreadsheetId, range)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Fetch rows for [sheetName], using [cache] to avoid redundant network calls. */
    private suspend fun cachedRows(
        sheetName: String,
        spreadsheetId: String,
        cache: MutableMap<String, List<List<Any?>>>,
    ): List<List<Any?>> = cache.getOrPut(sheetName) {
        sheetsApi.batchGet(spreadsheetId, listOf(sheetName))
            .valueRanges.firstOrNull()?.values.orEmpty()
    }

    /** Deserialize the queue item's payload JSON into a Sheets row array. */
    private fun entityRow(item: SyncQueueEntity): List<Any?> = when (item.entityType) {
        "task"   -> mapper.taskToRow(gson.fromJson(item.payloadJson, TaskEntity::class.java))
        "folder" -> mapper.folderToRow(gson.fromJson(item.payloadJson, FolderEntity::class.java))
        "label"  -> mapper.labelToRow(gson.fromJson(item.payloadJson, LabelEntity::class.java))
        else     -> emptyList()
    }

    private fun sheetOf(entityType: String) = when (entityType) {
        "task"   -> "tasks"
        "folder" -> "folders"
        "label"  -> "labels"
        else     -> entityType
    }

    private fun lastColOf(entityType: String) = when (entityType) {
        "task"   -> "Q"   // 17 columns A–Q
        "folder" -> "D"   // 4 columns A–D (id, name, color, sort_order)
        "label"  -> "D"   // 4 columns A–D (id, name, color, sort_order)
        else     -> "Z"
    }

    companion object {
        private const val TAG = "SyncWorker"
    }
}
