package com.stler.tasks.ui.priority

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.ui.task.TaskItem
import com.stler.tasks.ui.util.EmptyState
import com.stler.tasks.ui.util.ErrorSnackbarEffect
import com.stler.tasks.ui.util.ShimmerTaskList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityScreen(
    priority     : String = "",
    onEditTask   : (com.stler.tasks.domain.model.Task) -> Unit = {},
    onAddSubtask : (com.stler.tasks.domain.model.Task) -> Unit = {},
    viewModel    : PriorityViewModel = hiltViewModel(),
) {
    val filteredTasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val isLoading     by viewModel.isLoading.collectAsStateWithLifecycle()
    val labels        by viewModel.labels.collectAsStateWithLifecycle()
    val folders       by viewModel.folders.collectAsStateWithLifecycle()

    ErrorSnackbarEffect(viewModel)

    LaunchedEffect(priority) {
        val p = when (priority) {
            "urgent"    -> Priority.URGENT
            "important" -> Priority.IMPORTANT
            else        -> Priority.NORMAL
        }
        viewModel.selectPriority(p)
    }

    when {
        isLoading -> ShimmerTaskList(modifier = Modifier.fillMaxSize())
        filteredTasks.isEmpty() -> EmptyState(
            icon     = Icons.Outlined.Flag,
            message  = "No tasks",
            subtitle = "No ${priority.replaceFirstChar { it.uppercase() }} priority tasks",
        )
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filteredTasks, key = { it.id }) { task ->
                TaskItem(
                    task = task,
                    labels = labels,
                    showFolder = true,
                    folderName = folders.find { it.id == task.folderId }?.name,
                    folderColor = folders.find { it.id == task.folderId }?.color,
                    onCheckedChange = { checked -> if (checked) viewModel.completeTask(task.id) },
                    onExpand = {},
                    onDeadlineChange = { d, t, isRec, rType, rVal -> viewModel.updateDeadline(task.id, d, t, isRec, rType, rVal) },
                    onPriorityChange = { p -> viewModel.updatePriority(task.id, p) },
                    onLabelChange = { l -> viewModel.updateLabels(task.id, l) },
                    onAddSubtask = { onAddSubtask(task) },
                    onEdit = { onEditTask(task) },
                    onDelete = { viewModel.deleteTask(task.id) },
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}
