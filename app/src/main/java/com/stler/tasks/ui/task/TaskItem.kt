package com.stler.tasks.ui.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.RecurType
import com.stler.tasks.domain.model.Task
import com.stler.tasks.domain.model.TaskStatus
import com.stler.tasks.util.toComposeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskItem(
    task: Task,
    labels: List<Label>,
    depth: Int = 0,
    hasChildren: Boolean = false,
    completedChildCount: Int = 0,
    totalChildCount: Int = 0,
    showFolder: Boolean = false,
    showLabels: Boolean = true,
    showDateInDeadline: Boolean = true,
    folderName: String? = null,
    folderColor: String? = null,
    onCheckedChange: (Boolean) -> Unit,
    onExpand: () -> Unit,
    onDeadlineChange: (date: String, time: String, isRecurring: Boolean, recurType: RecurType, recurValue: Int) -> Unit,
    onPriorityChange: (Priority) -> Unit,
    onLabelChange: (List<String>) -> Unit,
    onAddSubtask: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompleted = task.status == TaskStatus.COMPLETED

    // Compute deadline metadata
    val dlStatus = deadlineStatus(task.deadlineDate)
    val dlLabel = deadlineLabel(task.deadlineDate, task.deadlineTime, includeDate = showDateInDeadline)

    // Task labels resolved from IDs
    val taskLabels = remember(task.labels, labels) {
        task.labels.mapNotNull { id -> labels.find { it.id == id } }
    }

    // Sheet / dialog visibility
    var showMobileMenu by remember { mutableStateOf(false) }
    var showDeadlinePicker by remember { mutableStateOf(false) }
    var showPriorityPicker by remember { mutableStateOf(false) }
    var showLabelPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val hasMetadata = dlLabel != null
            || (showLabels && taskLabels.isNotEmpty())
            || (showFolder && folderName != null)
            || task.isRecurring
            || totalChildCount > 0

    Column(modifier = modifier) {
        // ── Row 1: main task row ──────────────────────────────────────────────
        Row(
            modifier = Modifier.padding(
                start = (depth * 20).dp,
                end = 4.dp,
                top = 4.dp,
                bottom = 2.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Expand/collapse icon or empty placeholder.
            // Box = 28dp tap target; Icon = 24dp so the ChevronRight/ExpandMore glyph
            // (which only fills ~60 % of the icon canvas) renders at a visible size.
            Box(
                modifier = if (hasChildren)
                    Modifier.size(28.dp).clickable { onExpand() }
                else
                    Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (hasChildren) {
                    Icon(
                        imageVector = if (task.isExpanded) Icons.Outlined.ExpandMore
                        else Icons.Outlined.ChevronRight,
                        contentDescription = if (task.isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            TaskCheckbox(
                checked = isCompleted,
                onCheckedChange = onCheckedChange,
                priority = task.priority,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = task.title,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (isCompleted) 0.7f else 1f,
                ),
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Action buttons
            if (isCompleted) {
                IconButton(
                    onClick = { onCheckedChange(false) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Replay,
                        contentDescription = "Restore",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                // Deadline button
                val deadlineTint = if (task.deadlineDate.isNotBlank()) {
                    deadlineColor(dlStatus)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                IconButton(
                    onClick = { showDeadlinePicker = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = "Set deadline",
                        modifier = Modifier.size(15.dp),
                        tint = deadlineTint,
                    )
                }
                // More options
                IconButton(
                    onClick = { showMobileMenu = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "More options",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── Row 2: metadata chips ─────────────────────────────────────────────
        if (hasMetadata) {
            Row(
                modifier = Modifier.padding(
                    start = (depth * 20 + 52).dp,
                    end = 8.dp,
                    bottom = 4.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (task.isRecurring) {
                    Icon(
                        imageVector = Icons.Outlined.Autorenew,
                        contentDescription = "Recurring",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }

                if (dlLabel != null) {
                    Text(
                        text = dlLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = deadlineColor(dlStatus),
                    )
                }

                if (showLabels) {
                    for (label in taskLabels) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Label,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = label.color.toComposeColor(),
                            )
                            Text(
                                text = label.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = label.color.toComposeColor(),
                            )
                        }
                    }
                }

                if (showFolder && folderName != null) {
                    val fColor = if (folderColor != null) folderColor.toComposeColor()
                                 else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = fColor,
                        )
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = fColor,
                        )
                    }
                }

                if (totalChildCount > 0) {
                    val remaining = totalChildCount - completedChildCount
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SubtaskStat(Icons.Outlined.Check, completedChildCount)
                        SubtaskStat(Icons.Outlined.RadioButtonUnchecked, remaining)
                        SubtaskStat(Icons.Outlined.FormatListBulleted, totalChildCount)
                    }
                }
            }
        }
    }

    // ── Sheets and dialogs ────────────────────────────────────────────────────

    if (showMobileMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMobileMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            TaskMobileMenu(
                task = task,
                onPriority = {
                    showMobileMenu = false
                    showPriorityPicker = true
                },
                onLabels = {
                    showMobileMenu = false
                    showLabelPicker = true
                },
                onAddSubtask = {
                    showMobileMenu = false
                    onAddSubtask()
                },
                onEdit = {
                    showMobileMenu = false
                    onEdit()
                },
                onDelete = {
                    showMobileMenu = false
                    showDeleteConfirm = true
                },
            )
        }
    }

    if (showDeadlinePicker) {
        DeadlinePickerDialog(
            initialDate        = task.deadlineDate,
            initialTime        = task.deadlineTime,
            initialIsRecurring = task.isRecurring,
            initialRecurType   = task.recurType,
            initialRecurValue  = task.recurValue,
            onConfirm = { date, time, isRecurring, recurType, recurValue ->
                showDeadlinePicker = false
                onDeadlineChange(date, time, isRecurring, recurType, recurValue)
            },
            onDismiss = { showDeadlinePicker = false },
        )
    }

    if (showPriorityPicker) {
        PriorityPickerSheet(
            currentPriority = task.priority,
            onSelect = { priority ->
                showPriorityPicker = false
                onPriorityChange(priority)
            },
            onDismiss = { showPriorityPicker = false },
        )
    }

    if (showLabelPicker) {
        LabelPickerSheet(
            allLabels = labels,
            selectedIds = task.labels,
            onConfirm = { ids ->
                showLabelPicker = false
                onLabelChange(ids)
            },
            onDismiss = { showLabelPicker = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete task") },
            text = {
                Text("Delete \"${task.title}\"? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ── TaskMobileMenu ────────────────────────────────────────────────────────────

@Composable
private fun TaskMobileMenu(
    task: Task,
    onPriority: () -> Unit,
    onLabels: () -> Unit,
    onAddSubtask: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        // Priority
        ListItem(
            headlineContent = { Text("Priority") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = null,
                    tint = priorityColor(task.priority),
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.clickable { onPriority() },
        )
        // Labels
        ListItem(
            headlineContent = { Text("Labels") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.Label,
                    contentDescription = null,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.clickable { onLabels() },
        )
        // Add subtask
        ListItem(
            headlineContent = { Text("Add subtask") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                )
            },
            modifier = Modifier.clickable { onAddSubtask() },
        )
        // Edit
        ListItem(
            headlineContent = { Text("Edit") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                )
            },
            modifier = Modifier.clickable { onEdit() },
        )
        // Delete
        ListItem(
            headlineContent = {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error,
                )
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            modifier = Modifier.clickable { onDelete() },
        )
    }
}

// ── SubtaskStat ───────────────────────────────────────────────────────────────

@Composable
private fun SubtaskStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
