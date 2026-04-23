package com.stler.tasks.ui.completed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.tasks.ui.alltasks.FilterBar
import com.stler.tasks.ui.task.TaskItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedScreen(viewModel: CompletedViewModel = hiltViewModel()) {
    val filteredTasks  by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val labels         by viewModel.labels.collectAsStateWithLifecycle()
    val folders        by viewModel.folders.collectAsStateWithLifecycle()
    val priorityFilter by viewModel.priorityFilter.collectAsStateWithLifecycle()
    val labelFilter    by viewModel.labelFilter.collectAsStateWithLifecycle()
    val folderFilter   by viewModel.folderFilter.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        FilterBar(
            labels           = labels,
            folders          = folders,
            priorityFilter   = priorityFilter,
            labelFilter      = labelFilter,
            folderFilter     = folderFilter,
            onTogglePriority = { viewModel.togglePriorityFilter(it) },
            onToggleLabel    = { viewModel.toggleLabelFilter(it) },
            onToggleFolder   = { viewModel.toggleFolderFilter(it) },
            onClearAll       = { viewModel.clearAllFilters() },
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filteredTasks, key = { it.id }) { task ->
                TaskItem(
                    task = task,
                    labels = labels,
                    showFolder = true,
                    folderName = folders.find { it.id == task.folderId }?.name,
                    folderColor = folders.find { it.id == task.folderId }?.color,
                    onCheckedChange = { checked -> if (!checked) viewModel.restoreTask(task.id) },
                    onExpand = {},
                    onDeadlineChange = { _, _, _, _, _ -> },
                    onPriorityChange = {},
                    onLabelChange = {},
                    onAddSubtask = {},
                    onEdit = {},
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
