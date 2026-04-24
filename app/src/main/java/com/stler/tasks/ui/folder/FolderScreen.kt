package com.stler.tasks.ui.folder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.tasks.ui.task.TaskItem
import com.stler.tasks.ui.util.ErrorSnackbarEffect
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Folder task list with drag-to-reorder.
 *
 * ## Performance fix
 * The reorderable library's [onMove] fires on every drag frame (every pixel).
 * Previously, each frame called [FolderViewModel.reorderSiblings] which wrote
 * N rows × N frames to the DB and triggered N widget refreshes.
 *
 * Now:
 *  - [onMove] → only updates the local optimistic [localList] (pure UI, no DB)
 *              + stores the intended final sibling indices in [pendingReorder].
 *  - [isDragging] → false (user lifts finger) → one [reorderSiblings] call
 *                   with the stored from/to indices → 1 batch DB write, 1 widget refresh.
 */
@Composable
fun FolderScreen(
    folderId     : String,
    onEditTask   : (com.stler.tasks.domain.model.Task) -> Unit = {},
    onAddSubtask : (com.stler.tasks.domain.model.Task) -> Unit = {},
    viewModel    : FolderViewModel = hiltViewModel(),
) {
    val displayList by viewModel.displayList.collectAsStateWithLifecycle()
    val labels      by viewModel.labels.collectAsStateWithLifecycle()

    ErrorSnackbarEffect(viewModel)

    if (displayList.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tasks in this folder", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // Local mutable list for optimistic reorder UI (drag preview before DB write)
    var localList by remember(displayList) { mutableStateOf(displayList) }

    // ── Pending reorder — set in onMove, consumed when isDragging → false ─────
    // Plain mutable object (not mutableStateOf) so that intermediate drag frames
    // don't trigger recomposition.
    val pendingReorder = remember {
        object {
            var parentId : String = ""
            var fromIdx  : Int    = -1
            var toIdx    : Int    = -1
        }
    }

    val lazyListState = rememberLazyListState()

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
    ) { from, to ->
        // ── Step 1: update optimistic local list (pure UI, no DB) ─────────────
        val mutable = localList.toMutableList()
        val movedNode = mutable.removeAt(from.index)
        mutable.add(to.index, movedNode)
        localList = mutable

        // ── Step 2: store final sibling indices for persistence on drop ────────
        // fromIdx is always relative to displayList (original DB order, stable during drag).
        // toIdx   is relative to localList (optimistic order, updates each frame).
        val draggedNode     = localList[to.index]
        val parentId        = draggedNode.task.parentId
        val siblings        = displayList.filter { it.task.parentId == parentId }
        val siblingsInLocal = localList.filter { it.task.parentId == parentId }
        val fromSiblingIdx  = siblings.indexOfFirst { it.task.id == draggedNode.task.id }
        val toSiblingIdx    = siblingsInLocal.indexOfFirst { it.task.id == draggedNode.task.id }

        if (fromSiblingIdx >= 0 && toSiblingIdx >= 0) {
            pendingReorder.parentId = parentId
            pendingReorder.fromIdx  = fromSiblingIdx
            pendingReorder.toIdx    = toSiblingIdx
        }
    }

    LazyColumn(
        state    = lazyListState,
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(localList, key = { _, node -> node.task.id }) { index, node ->
            val task  = node.task
            val depth = node.depth

            // Task immediately above in the displayed list — indent target.
            val taskAbove = if (index > 0) localList[index - 1] else null

            ReorderableItem(reorderableState, key = task.id) { isDragging ->

                // ── Persist on drop: isDragging flips true → false ────────────
                // Only the dragged item transitions to false at drop; other items
                // remain false throughout, so their LaunchedEffect fires only on
                // first composition (when pendingReorder is still uninitialised).
                LaunchedEffect(isDragging) {
                    if (!isDragging && pendingReorder.fromIdx >= 0 && pendingReorder.toIdx >= 0
                        && pendingReorder.fromIdx != pendingReorder.toIdx
                    ) {
                        viewModel.reorderSiblings(
                            pendingReorder.parentId,
                            pendingReorder.fromIdx,
                            pendingReorder.toIdx,
                        )
                        // Reset so a stale pending reorder can't fire twice
                        pendingReorder.fromIdx = -1
                        pendingReorder.toIdx   = -1
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Drag handle on the far left
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = "Drag to reorder",
                        modifier = Modifier
                            .size(20.dp)
                            .draggableHandle(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    TaskItem(
                        task                = task,
                        labels              = labels,
                        depth               = depth,
                        hasChildren         = node.childCount > 0,
                        completedChildCount = node.completedChildCount,
                        totalChildCount     = node.childCount + node.completedChildCount,
                        showFolder          = false,
                        onCheckedChange     = { checked -> if (checked) viewModel.completeTask(task.id) },
                        onExpand            = { viewModel.toggleExpanded(task) },
                        onDeadlineChange    = { d, t, isRec, rType, rVal -> viewModel.updateDeadline(task.id, d, t, isRec, rType, rVal) },
                        onPriorityChange    = { p -> viewModel.updatePriority(task.id, p) },
                        onLabelChange       = { l -> viewModel.updateLabels(task.id, l) },
                        onAddSubtask        = { onAddSubtask(node.task) },
                        onEdit              = { onEditTask(node.task) },
                        onDelete            = { viewModel.deleteTask(task.id) },
                        // Disable swipe gestures — conflicts with drag-to-reorder
                        enableSwipe         = false,
                        // Reparent actions: shown in "..." menu only in FolderScreen
                        onIndent  = taskAbove?.let { above ->
                            { viewModel.reparentTask(task.id, above.task.id) }
                        },
                        onOutdent = if (depth > 0) {
                            {
                                val grandparentId = localList
                                    .find { it.task.id == task.parentId }
                                    ?.task?.parentId ?: ""
                                viewModel.reparentTask(task.id, grandparentId)
                            }
                        } else null,
                        modifier            = Modifier.weight(1f),
                    )
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}
