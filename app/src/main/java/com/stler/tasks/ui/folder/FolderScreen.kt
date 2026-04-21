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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun FolderScreen(
    folderId     : String,
    onEditTask   : (com.stler.tasks.domain.model.Task) -> Unit = {},
    onAddSubtask : (com.stler.tasks.domain.model.Task) -> Unit = {},
    viewModel    : FolderViewModel = hiltViewModel(),
) {
    val displayList by viewModel.displayList.collectAsStateWithLifecycle()
    val labels by viewModel.labels.collectAsStateWithLifecycle()

    if (displayList.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tasks in this folder", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // Local mutable list for optimistic reorder UI (drag preview before DB write)
    var localList by remember(displayList) { mutableStateOf(displayList) }

    val lazyListState = rememberLazyListState()

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
    ) { from, to ->
        // Optimistic reorder in local list (pure UI swap)
        val mutable = localList.toMutableList()
        val moved = mutable.removeAt(from.index)
        mutable.add(to.index, moved)
        localList = mutable

        // Persist only when drag ends (handled via onDragStopped below)
        // Here we update the DB immediately by sibling index:
        val draggedNode = localList[to.index]
        val parentId = draggedNode.task.parentId

        // Compute from/to within the sibling group
        val siblings = displayList.filter { it.task.parentId == parentId }
        val siblingsInLocal = localList.filter { it.task.parentId == parentId }
        val fromSiblingIdx = siblings.indexOfFirst { it.task.id == draggedNode.task.id }
        val toSiblingIdx = siblingsInLocal.indexOfFirst { it.task.id == draggedNode.task.id }

        if (fromSiblingIdx >= 0 && toSiblingIdx >= 0 && fromSiblingIdx != toSiblingIdx) {
            viewModel.reorderSiblings(parentId, fromSiblingIdx, toSiblingIdx)
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(localList, key = { _, node -> node.task.id }) { _, node ->
            val task = node.task
            val depth = node.depth

            ReorderableItem(reorderableState, key = task.id) { isDragging ->
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
                        task = task,
                        labels = labels,
                        depth = depth,
                        hasChildren = node.childCount > 0,
                        completedChildCount = node.completedChildCount,
                        totalChildCount = node.childCount + node.completedChildCount,
                        showFolder = false,
                        onCheckedChange = { checked -> if (checked) viewModel.completeTask(task.id) },
                        onExpand = { viewModel.toggleExpanded(task) },
                        onDeadlineChange = { d, t, isRec, rType, rVal -> viewModel.updateDeadline(task.id, d, t, isRec, rType, rVal) },
                        onPriorityChange = { p -> viewModel.updatePriority(task.id, p) },
                        onLabelChange = { l -> viewModel.updateLabels(task.id, l) },
                        onAddSubtask = { onAddSubtask(node.task) },
                        onEdit = { onEditTask(node.task) },
                        onDelete = { viewModel.deleteTask(task.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}
