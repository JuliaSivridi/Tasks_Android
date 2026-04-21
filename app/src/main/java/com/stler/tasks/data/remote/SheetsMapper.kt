package com.stler.tasks.data.remote

import com.stler.tasks.data.local.entity.FolderEntity
import com.stler.tasks.data.local.entity.LabelEntity
import com.stler.tasks.data.local.entity.TaskEntity
import javax.inject.Inject

/**
 * Converts between Google Sheets row arrays and Room entities (bidirectional).
 *
 * Task columns (A–Q):
 *   0=id, 1=parent_id, 2=folder_id, 3=title, 4=status, 5=priority,
 *   6=deadline_date, 7=deadline_time, 8=is_recurring, 9=recur_type,
 *   10=recur_value, 11=labels, 12=sort_order, 13=created_at,
 *   14=updated_at, 15=completed_at, 16=is_expanded
 *
 * Folder columns (A–D):
 *   0=id, 1=name, 2=color, 3=sort_order
 *
 * Label columns (A–C):
 *   0=id, 1=name, 2=color
 */
class SheetsMapper @Inject constructor() {

    // ── Tasks ─────────────────────────────────────────────────────────────

    fun rowToTask(row: List<Any?>): TaskEntity? {
        val id = row.str(0).takeIf { it.isNotBlank() } ?: return null
        return TaskEntity(
            id = id,
            parentId = row.str(1),
            folderId = row.str(2).ifEmpty { "fld-inbox" },
            title = row.str(3),
            status = row.str(4).ifEmpty { "pending" },
            priority = row.str(5).ifEmpty { "normal" },
            deadlineDate = row.dateStr(6),
            deadlineTime = row.str(7),
            isRecurring = row.bool(8),
            recurType = row.str(9),
            recurValue = row.int(10, 1),
            labels = row.str(11),
            sortOrder = row.int(12, 0),
            createdAt = row.str(13),
            updatedAt = row.str(14),
            completedAt = row.str(15),
            isExpanded = row.bool(16),
        )
    }

    fun taskToRow(task: TaskEntity): List<Any?> = listOf(
        task.id, task.parentId, task.folderId, task.title,
        task.status, task.priority, task.deadlineDate, task.deadlineTime,
        if (task.isRecurring) "TRUE" else "FALSE",   // store as text like PWA
        task.recurType, task.recurValue, task.labels,
        task.sortOrder, task.createdAt, task.updatedAt, task.completedAt,
        if (task.isExpanded) "TRUE" else "FALSE",    // store as text like PWA
    )

    // ── Folders ───────────────────────────────────────────────────────────

    fun rowToFolder(row: List<Any?>): FolderEntity? {
        val id = row.str(0).takeIf { it.isNotBlank() } ?: return null
        return FolderEntity(
            id = id,
            name = row.str(1),
            color = row.str(2).ifEmpty { "#6b7280" },
            sortOrder = row.int(3, 0),
        )
    }

    fun folderToRow(folder: FolderEntity): List<Any?> = listOf(
        folder.id, folder.name, folder.color, folder.sortOrder,
    )

    // ── Labels ────────────────────────────────────────────────────────────

    fun rowToLabel(row: List<Any?>): LabelEntity? {
        val id = row.str(0).takeIf { it.isNotBlank() } ?: return null
        return LabelEntity(
            id = id,
            name = row.str(1),
            color = row.str(2).ifEmpty { "#6b7280" },
        )
    }

    fun labelToRow(label: LabelEntity): List<Any?> = listOf(
        label.id, label.name, label.color,
    )

    // ── Row-number lookup (for UPDATE / DELETE push) ───────────────────────

    /**
     * Returns the 1-based row number (in Sheets notation) for the entity with [id].
     * Skips the header row (index 0 → row 1 in Sheets, so entity rows start at row 2).
     */
    fun findRowNumber(rows: List<List<Any?>>, id: String): Int? {
        rows.forEachIndexed { index, row ->
            if (index == 0) return@forEachIndexed  // skip header
            if (row.str(0) == id) return index + 1  // +1: Sheets rows are 1-indexed
        }
        return null
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun List<Any?>.str(index: Int): String =
        getOrNull(index)?.toString()?.trim() ?: ""

    /**
     * Reads a date cell at [index] and always returns an ISO "YYYY-MM-DD" string (or "").
     *
     * When the sheet was written with valueInputOption=USER_ENTERED, Google Sheets
     * automatically converts "2024-01-15" into a native date cell.  Reading back with
     * valueRenderOption=UNFORMATTED_VALUE then returns the Sheets serial number
     * (Double, days since 1899-12-30) instead of the original string.
     * This helper converts the serial number back to ISO format so Room and the UI
     * always see "YYYY-MM-DD".
     */
    private fun List<Any?>.dateStr(index: Int): String {
        val raw = getOrNull(index) ?: return ""
        if (raw is Number) {
            // Sheets serial date: days elapsed since 1899-12-30
            return try {
                java.time.LocalDate.of(1899, 12, 30)
                    .plusDays(raw.toLong())
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) { "" }
        }
        // Already a string — strip whitespace and return as-is (may be "" for empty cell)
        return raw.toString().trim()
    }

    private fun List<Any?>.bool(index: Int): Boolean =
        when (val v = getOrNull(index)) {
            is Boolean -> v
            is String -> v.lowercase() == "true"
            else -> false
        }

    private fun List<Any?>.int(index: Int, default: Int = 0): Int =
        when (val v = getOrNull(index)) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }
}
