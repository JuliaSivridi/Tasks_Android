package com.stler.tasks.ui.task

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.ui.theme.Border
import com.stler.tasks.ui.theme.OnChipSelected
import com.stler.tasks.domain.model.RecurType
import com.stler.tasks.domain.model.Task
import com.stler.tasks.util.toComposeColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Single bottom sheet for both Create and Edit task.
 * Edit mode: pass a non-null [task].
 * Create mode: pass [task] = null, optionally [initialFolderId] and [initialParentId].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskFormSheet(
    task: Task? = null,
    initialFolderId: String = "fld-inbox",
    initialParentId: String = "",
    labels: List<Label>,
    folders: List<Folder>,
    onConfirm: (TaskFormResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val isEditing = task != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── Form state ────────────────────────────────────────────────────────

    var title by remember(task) {
        mutableStateOf(task?.title ?: "")
    }
    var priority by remember(task) {
        mutableStateOf(task?.priority ?: Priority.NORMAL)
    }
    var selectedLabelIds by remember(task) {
        mutableStateOf(task?.labels ?: emptyList())
    }
    var deadlineDate by remember(task) {
        mutableStateOf(task?.deadlineDate ?: "")
    }
    var deadlineTime by remember(task) {
        mutableStateOf(task?.deadlineTime ?: "")
    }
    var isRecurring by remember(task) {
        mutableStateOf(task?.isRecurring ?: false)
    }
    var recurType by remember(task) {
        mutableStateOf(task?.recurType ?: RecurType.DAYS)
    }
    var recurValue by remember(task) {
        mutableStateOf(task?.recurValue?.toString() ?: "1")
    }
    var folderId by remember(task) {
        mutableStateOf(task?.folderId ?: initialFolderId)
    }

    // UI sub-states
    var showDeadlinePicker by remember { mutableStateOf(false) }
    var showTimePicker     by remember { mutableStateOf(false) }
    var showFolderPicker   by remember { mutableStateOf(false) }
    var showNewLabel       by remember { mutableStateOf(false) }
    var newLabelName       by remember { mutableStateOf("") }
    var newLabelColor      by remember { mutableStateOf(COLOR_PRESETS[5]) }
    var titleError         by remember { mutableStateOf(false) }

    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }

    // ── Helper ─────────────────────────────────────────────────────────────

    fun submit() {
        val trimmed = title.trim()
        if (trimmed.isBlank()) { titleError = true; return }
        // Smart parse: @FolderName → folder, #LabelName → label
        val parsed = parseSmartTitle(trimmed, folders, labels, folderId, selectedLabelIds)
        onConfirm(
            TaskFormResult(
                title         = parsed.title,
                folderId      = parsed.folderId,
                parentId      = task?.parentId ?: initialParentId,
                priority      = parsed.priority ?: priority,
                labelIds      = parsed.labelIds,
                deadlineDate  = deadlineDate,
                deadlineTime  = deadlineTime,
                isRecurring   = isRecurring,
                recurType     = recurType,
                recurValue    = recurValue.toIntOrNull() ?: 1,
            )
        )
    }

    // ── Sheet ─────────────────────────────────────────────────────────────

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            // ── Title ─────────────────────────────────────────────────────
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; titleError = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus),
                placeholder = { Text("Task name") },
                isError = titleError,
                supportingText = if (titleError) ({ Text("Title is required") }) else null,
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )

            // ── Deadline — date chip + time chip + clear icon on one row ─────
            SectionLabel("Deadline")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val dlStatus = deadlineStatus(deadlineDate)
                // Date chip — uses selected=false (border-only style) like time chip for consistency.
                // Active state indicated by primary-colored icon/text.
                FilterChip(
                    selected = false,
                    onClick  = { showDeadlinePicker = true },
                    label    = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.CalendarMonth, null,
                                modifier = Modifier.size(13.dp),
                                tint = if (deadlineDate.isNotBlank()) deadlineColor(dlStatus)
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text  = if (deadlineDate.isBlank()) "No date"
                                        else formatExplicitDate(deadlineDate),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (deadlineDate.isNotBlank()) deadlineColor(dlStatus)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                // Time chip — only visible when date is set
                if (deadlineDate.isNotBlank()) {
                    FilterChip(
                        selected = false,
                        onClick  = { showTimePicker = true },
                        label    = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // Time chip inherits the same status color as the date chip —
                                // both belong to the same deadline. dlStatus is computed above.
                                Icon(
                                    Icons.Outlined.Schedule, null,
                                    modifier = Modifier.size(13.dp),
                                    tint = if (deadlineTime.isNotBlank()) deadlineColor(dlStatus)
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text  = if (deadlineTime.isBlank()) "No time" else deadlineTime,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (deadlineTime.isNotBlank()) deadlineColor(dlStatus)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }

            // ── Repeat — only shown when a date is set ────────────────────
            if (deadlineDate.isNotBlank()) {
                RepeatRow(
                    isChecked     = isRecurring,
                    onToggle      = { isRecurring = it },
                    recurValue    = recurValue,
                    onValueChange = { recurValue = it },
                    recurType     = recurType,
                    onTypeChange  = { recurType = it },
                )
            }

            // ── Priority ──────────────────────────────────────────────────
            SectionLabel("Priority")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Priority.URGENT    to "Urgent",
                    Priority.IMPORTANT to "Important",
                    Priority.NORMAL    to "Normal",
                ).forEach { (p, label) ->
                    val selected = priority == p
                    val pColor   = priorityColor(p)
                    FilterChip(
                        selected    = selected,
                        onClick     = { priority = p },
                        label       = {
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        },
                        leadingIcon = {
                            Icon(
                                if (selected) Icons.Outlined.Check else Icons.Outlined.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = pColor,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor  = pColor.copy(alpha = 0.18f),
                            selectedLabelColor      = pColor,
                            selectedLeadingIconColor = pColor,
                        ),
                    )
                }
            }

            // ── Labels ────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SectionLabel("Labels")
                if (!showNewLabel) {
                    TextButton(onClick = { showNewLabel = true }) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (labels.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEach { lbl ->
                        val sel    = lbl.id in selectedLabelIds
                        val lColor = lbl.color.toComposeColor()
                        FilterChip(
                            selected    = sel,
                            onClick     = {
                                selectedLabelIds = if (sel)
                                    selectedLabelIds - lbl.id
                                else
                                    selectedLabelIds + lbl.id
                            },
                            label       = {
                                Text(
                                    lbl.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = lColor,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (sel) Icons.Outlined.Check else Icons.Outlined.Label,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = lColor,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor   = lColor.copy(alpha = 0.18f),
                                selectedLabelColor       = lColor,
                                selectedLeadingIconColor = lColor,
                            ),
                        )
                    }
                }
            }

            // Inline new-label creator
            if (showNewLabel) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Color swatches
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        COLOR_PRESETS.forEach { hex ->
                            val c = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .then(
                                        if (hex == newLabelColor)
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { newLabelColor = hex },
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = newLabelName,
                            onValueChange = { newLabelName = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Label name") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                        TextButton(onClick = {
                            if (newLabelName.isNotBlank()) {
                                // Caller will handle creation via onNewLabel; we emit a sentinel
                                // by including "__new__:color:name" in label IDs
                                val sentinel = "__new__:$newLabelColor:${newLabelName.trim()}"
                                selectedLabelIds = selectedLabelIds + sentinel
                                newLabelName = ""
                                newLabelColor = COLOR_PRESETS[5]
                                showNewLabel = false
                            }
                        }) { Text("OK") }
                        IconButton(onClick = {
                            showNewLabel = false
                            newLabelName = ""
                        }) {
                            Icon(Icons.Outlined.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ── Folder ────────────────────────────────────────────────────
            SectionLabel("Folder")
            val folderName  = folders.find { it.id == folderId }?.name ?: "Inbox"
            val folderColor = folders.find { it.id == folderId }?.color
            val fColor      = folderColor?.toComposeColor()
                              ?: MaterialTheme.colorScheme.primary
            FilterChip(
                selected    = true,
                onClick     = { showFolderPicker = true },
                label       = {
                    Text(
                        folderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = fColor,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = fColor,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor   = fColor.copy(alpha = 0.18f),
                    selectedLabelColor       = fColor,
                    selectedLeadingIconColor = fColor,
                ),
            )

            Spacer(Modifier.height(4.dp))

            // ── Buttons ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: Clear / Postpone — only in edit mode
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isEditing && (deadlineDate.isNotBlank() || deadlineTime.isNotBlank())) {
                        TextButton(onClick = {
                            deadlineDate = ""
                            deadlineTime = ""
                            isRecurring  = false
                            recurType    = RecurType.DAYS
                            recurValue   = "1"
                        }) { Text("Clear") }
                    }
                    if (isEditing && isRecurring && deadlineDate.isNotBlank()) {
                        TextButton(onClick = {
                            runCatching {
                                val n = recurValue.toIntOrNull() ?: 1
                                val base = LocalDate.parse(deadlineDate)
                                deadlineDate = when (recurType) {
                                    RecurType.WEEKS  -> base.plusWeeks(n.toLong())
                                    RecurType.MONTHS -> base.plusMonths(n.toLong())
                                    else             -> base.plusDays(n.toLong())
                                }.toString()
                            }
                        }) { Text("Postpone") }
                    }
                }
                // Right: Cancel + Save/Create
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { submit() }) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isEditing) "Save" else "Create")
                    }
                }
            }
        }
    }

    // ── Sub-dialogs ────────────────────────────────────────────────────────

    // Date picker — plain Material3 calendar dialog (no quick buttons or time)
    if (showDeadlinePicker) {
        val initialMillis = remember(deadlineDate) {
            if (deadlineDate.isBlank()) null
            else runCatching {
                LocalDate.parse(deadlineDate)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDeadlinePicker = false },
            confirmButton    = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        deadlineDate = LocalDate
                            .ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC)
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    showDeadlinePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        deadlineDate = ""
                        deadlineTime = ""
                        showDeadlinePicker = false
                    }) { Text("Clear") }
                    TextButton(onClick = { showDeadlinePicker = false }) { Text("Cancel") }
                }
            },
        ) { DatePicker(state = datePickerState) }
    }

    // Time picker sub-dialog
    if (showTimePicker) {
        val (initH, initM) = remember(deadlineTime) {
            if (deadlineTime.isBlank()) 9 to 0
            else runCatching {
                val p = deadlineTime.split(":"); p[0].toInt() to p[1].toInt()
            }.getOrDefault(9 to 0)
        }
        val timePickerState = androidx.compose.material3.rememberTimePickerState(
            initialHour   = initH,
            initialMinute = initM,
            is24Hour      = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title            = { Text("Set time") },
            text             = { androidx.compose.material3.TimePicker(state = timePickerState) },
            confirmButton    = {
                TextButton(onClick = {
                    deadlineTime = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        deadlineTime = ""
                        showTimePicker = false
                    }) { Text("Clear") }
                    TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            folders   = folders,
            currentId = folderId,
            onSelect  = { id -> folderId = id; showFolderPicker = false },
            onDismiss = { showFolderPicker = false },
        )
    }
}

// ── Result ─────────────────────────────────────────────────────────────────────

data class TaskFormResult(
    val title       : String,
    val folderId    : String,
    val parentId    : String,
    val priority    : Priority,
    val labelIds    : List<String>,   // may contain "__new__:color:name" sentinels
    val deadlineDate: String,
    val deadlineTime: String,
    val isRecurring : Boolean,
    val recurType   : RecurType,
    val recurValue  : Int,
)

// ── Helpers ────────────────────────────────────────────────────────────────────

/** Formats a "YYYY-MM-DD" string as "d MMM" (e.g. "18 Apr") — never "Today"/"Tomorrow". */
private fun formatExplicitDate(dateStr: String): String =
    runCatching {
        LocalDate.parse(dateStr)
            .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }.getOrDefault(dateStr)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Inline repeat row: [☐] Repeat  /  [☑] Repeat every [3] [D] [W] [M]
 * Replaces the old Switch + separate RecurRow pattern.
 */
@Composable
internal fun RepeatRow(
    isChecked    : Boolean,
    onToggle     : (Boolean) -> Unit,
    recurValue   : String,
    onValueChange: (String) -> Unit,
    recurType    : RecurType,
    onTypeChange : (RecurType) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked         = isChecked,
            onCheckedChange = onToggle,
        )
        Text(
            text     = if (isChecked) "Repeat every" else "Repeat",
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable { onToggle(!isChecked) },
        )
        if (isChecked) {
            Spacer(Modifier.width(8.dp))
            // Compact outlined number input (BasicTextField avoids OutlinedTextField's tall minimum height)
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value         = recurValue,
                    onValueChange = { v ->
                        if (v.isEmpty() || (v.all { it.isDigit() } && v.length <= 3)) onValueChange(v)
                    },
                    singleLine    = true,
                    textStyle     = MaterialTheme.typography.bodyMedium.copy(
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush   = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Spacer(Modifier.width(4.dp))
            listOf(RecurType.DAYS to "D", RecurType.WEEKS to "W", RecurType.MONTHS to "M")
                .forEach { (rt, lbl) ->
                    FilterChip(
                        selected = recurType == rt,
                        onClick  = { onTypeChange(rt) },
                        label    = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor  = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceVariant else Border,
                            selectedLabelColor      = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurfaceVariant else OnChipSelected,
                        ),
                    )
                    Spacer(Modifier.width(4.dp))
                }
        }
    }
}

@Composable
private fun FolderPickerDialog(
    folders  : List<Folder>,
    currentId: String,
    onSelect : (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select folder") },
        text  = {
            Column {
                // Inbox first
                FolderPickerItem(
                    name      = "Inbox",
                    color     = null,
                    selected  = currentId == "fld-inbox",
                    onClick   = { onSelect("fld-inbox") },
                )
                folders.filter { !it.isInbox }.forEach { f ->
                    FolderPickerItem(
                        name    = f.name,
                        color   = f.color,
                        selected = currentId == f.id,
                        onClick = { onSelect(f.id) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun FolderPickerItem(
    name    : String,
    color   : String?,
    selected: Boolean,
    onClick : () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = color?.toComposeColor() ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = color?.toComposeColor() ?: MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ── Smart title parsing ────────────────────────────────────────────────────────

private data class ParsedTitle(
    val title   : String,
    val folderId: String,
    val labelIds: List<String>,
    val priority: Priority? = null,
)

private fun parseSmartTitle(
    raw          : String,
    folders      : List<Folder>,
    labels       : List<Label>,
    baseFolderId : String,
    baseLabelIds : List<String>,
): ParsedTitle {
    var title    = raw
    var folderId = baseFolderId
    val labelIds = baseLabelIds.toMutableList()
    var parsedPriority: Priority? = null

    // @FolderName → sets folder, strips token
    title = title.replace(Regex("@(\\S+)")) { match ->
        val name = match.groupValues[1]
        val found = folders.find { it.name.equals(name, ignoreCase = true) }
        if (found != null) { folderId = found.id; "" } else match.value
    }

    // #LabelName → adds label, strips token
    title = title.replace(Regex("#(\\S+)")) { match ->
        val name  = match.groupValues[1]
        val found = labels.find { it.name.equals(name, ignoreCase = true) }
        if (found != null && found.id !in labelIds) { labelIds.add(found.id); "" } else match.value
    }

    // !1 → URGENT, !2 → IMPORTANT, !3 → NORMAL, strips token
    title = title.replace(Regex("!(\\d)")) { match ->
        val p = when (match.groupValues[1]) {
            "1"  -> Priority.URGENT
            "2"  -> Priority.IMPORTANT
            "3"  -> Priority.NORMAL
            else -> null
        }
        if (p != null) { parsedPriority = p; "" } else match.value
    }

    return ParsedTitle(
        title    = title.replace(Regex("\\s{2,}"), " ").trim(),
        folderId = folderId,
        labelIds = labelIds,
        priority = parsedPriority,
    )
}

// ── Constants ──────────────────────────────────────────────────────────────────

internal val COLOR_PRESETS = listOf(
    "#ef4444", "#f97316", "#eab308", "#22c55e",
    "#06b6d4", "#3b82f6", "#8b5cf6", "#6b7280",
)
