package com.stler.tasks.ui.task

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Label
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.RecurType
import com.stler.tasks.domain.model.Task
import com.stler.tasks.domain.model.TaskStatus
import com.stler.tasks.ui.theme.DeadlineToday
import com.stler.tasks.util.toComposeColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    showExpandSlot: Boolean = false,
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
    /** Non-null only in FolderScreen when a task exists directly above → shown as "Make subtask of above". */
    onIndent: (() -> Unit)? = null,
    /** Non-null only in FolderScreen when task has a parent → shown as "Move up a level". */
    onOutdent: (() -> Unit)? = null,
    /**
     * Enable swipe gestures:
     *   swipe right → complete task
     *   swipe left  → open deadline dialog (snaps back)
     * Pass false in FolderScreen (conflicts with drag-to-reorder) and CompletedScreen.
     */
    enableSwipe: Boolean = true,
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

    // ── Swipe-to-dismiss ─────────────────────────────────────────────────────
    // key(task.id, task.deadlineDate) forces Compose to rebuild ALL inner state
    // (including rememberSaveable inside rememberSwipeToDismissBoxState) whenever
    // the task ID or deadline changes.  This serves two purposes:
    //
    //  1. After completing a recurring task the deadline advances → key changes →
    //     dismiss state resets to Settled automatically, so swipe works again
    //     without needing snapTo().
    //
    //  2. Prevents the saved StartToEnd state from being restored when the user
    //     navigates back to a screen (rememberSaveable would otherwise re-trigger
    //     the completion LaunchedEffect, auto-completing the task again).
    key(task.id, task.deadlineDate) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                // Allow right swipe to settle (business logic runs in LaunchedEffect below
                // with a short delay so the green flash is visible for ~500 ms).
                SwipeToDismissBoxValue.StartToEnd -> !isCompleted
                // Reject left swipe (state snaps back), but open deadline dialog as a side effect.
                SwipeToDismissBoxValue.EndToStart -> {
                    if (!isCompleted) showDeadlinePicker = true
                    false
                }
                else -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.4f },
    )

    // Fire once when the swipe-right state settles: wait for the green flash, then
    // call onCheckedChange.  No snapTo needed — for recurring tasks the deadline
    // change causes key() above to rebuild the dismiss state fresh (Settled).
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
            delay(500L)
            onCheckedChange(true)
        }
    }

    // Checkbox tap: show checkmark immediately, complete after 400 ms.
    // Only the green background was removed — the checkmark and delay must remain.
    var pendingComplete by remember { mutableStateOf(false) }
    LaunchedEffect(pendingComplete) {
        if (pendingComplete) {
            delay(400L)
            pendingComplete = false
            onCheckedChange(true)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val swipeDir = dismissState.targetValue
            val bgColor by animateColorAsState(
                targetValue = when (swipeDir) {
                    SwipeToDismissBoxValue.StartToEnd -> DeadlineToday.copy(alpha = 0.85f)
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    else                             -> Color.Transparent
                },
                label = "swipeBg",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = when (swipeDir) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else                             -> Alignment.CenterEnd
                },
            ) {
                if (swipeDir != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        imageVector = if (swipeDir == SwipeToDismissBoxValue.StartToEnd)
                            Icons.Outlined.Check else Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        modifier = modifier,
        enableDismissFromStartToEnd = enableSwipe && !isCompleted,
        enableDismissFromEndToStart = enableSwipe && !isCompleted,
    ) {
        // Single row: [expand][checkbox][Column: title + metadata][buttons]
        // Checkbox is to the left of BOTH title and metadata — same structure as the widget.
        // The metadata row sits directly under the title with only 2 dp of breathing room,
        // eliminating the large gap that appeared when the two rows were separate Column children.
        Row(
            modifier = Modifier.padding(
                start  = 8.dp + (depth * 20).dp,
                end    = 4.dp,
                top    = 4.dp,
                bottom = 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Expand/collapse — shown only when task has children OR caller needs alignment slot.
            if (showExpandSlot || hasChildren) {
                Box(
                    modifier = if (hasChildren)
                        Modifier.size(32.dp).clickable { onExpand() }
                    else
                        Modifier.size(32.dp),
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
            }

            TaskCheckbox(
                checked = isCompleted || pendingComplete,
                onCheckedChange = { newChecked ->
                    if (newChecked && !isCompleted) {
                        pendingComplete = true   // show checkmark, delay, then complete
                    } else {
                        onCheckedChange(newChecked)
                    }
                },
                priority = task.priority,
                contentDesc = if (isCompleted) "Mark as incomplete: ${task.title}"
                              else "Mark as complete: ${task.title}",
            )

            Spacer(modifier = Modifier.width(8.dp))

            // ── Title + metadata, aligned together ───────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    modifier = Modifier.clickable { onEdit() },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (isCompleted) 0.7f else 1f,
                    ),
                )

                if (hasMetadata) {
                    FlowRow(
                        modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement   = Arrangement.spacedBy(2.dp),
                    ) {
                        if (task.isRecurring || dlLabel != null) {
                            Row(
                                modifier = Modifier.defaultMinSize(minHeight = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                            }
                        }

                        if (showLabels) {
                            for (label in taskLabels) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Label,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = label.color.toComposeColor(),
                                    )
                                    Text(
                                        text = label.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = label.color.toComposeColor(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
                                SubtaskStat(Icons.AutoMirrored.Outlined.FormatListBulleted, totalChildCount)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // ── Action buttons ───────────────────────────────────────────────
            if (isCompleted) {
                Box(
                    modifier = Modifier.size(40.dp).clickable { onCheckedChange(false) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Replay,
                        contentDescription = "Restore",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier.size(40.dp).clickable { showDeleteConfirm = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                val deadlineTint = if (task.deadlineDate.isNotBlank()) deadlineColor(dlStatus)
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier.size(40.dp).clickable { showDeadlinePicker = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = "Set deadline",
                        modifier = Modifier.size(18.dp),
                        tint = deadlineTint,
                    )
                }
                Box(
                    modifier = Modifier.size(40.dp).clickable { showMobileMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "More options",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    } // end key(task.id, task.deadlineDate)

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
                onIndent  = onIndent?.let { cb -> { showMobileMenu = false; cb() } },
                onOutdent = onOutdent?.let { cb -> { showMobileMenu = false; cb() } },
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
    task     : Task,
    onPriority  : () -> Unit,
    onLabels    : () -> Unit,
    onAddSubtask: () -> Unit,
    /** Non-null → show "Make subtask of above" item. */
    onIndent : (() -> Unit)?,
    /** Non-null → show "Move up a level" item. */
    onOutdent: (() -> Unit)?,
    onEdit  : () -> Unit,
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
                    imageVector = Icons.AutoMirrored.Outlined.Label,
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
        // Make subtask of above — only in FolderScreen when a task exists directly above
        if (onIndent != null) {
            ListItem(
                headlineContent = { Text("Make subtask of above") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { onIndent() },
            )
        }
        // Move up a level — only in FolderScreen when task has a parent
        if (onOutdent != null) {
            ListItem(
                headlineContent = { Text("Move up a level") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { onOutdent() },
            )
        }
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
