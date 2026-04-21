package com.stler.tasks.ui.completed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.ui.task.TaskItem
import com.stler.tasks.ui.task.priorityColor
import com.stler.tasks.util.toComposeColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CompletedScreen(viewModel: CompletedViewModel = hiltViewModel()) {
    val filteredTasks  by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val labels         by viewModel.labels.collectAsStateWithLifecycle()
    val folders        by viewModel.folders.collectAsStateWithLifecycle()
    val priorityFilter by viewModel.priorityFilter.collectAsStateWithLifecycle()
    val labelFilter    by viewModel.labelFilter.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Priority filter chips (icon-only)
            listOf(
                Priority.URGENT    to "Urgent",
                Priority.IMPORTANT to "Important",
                Priority.NORMAL    to "Normal",
            ).forEach { (p, label) ->
                FilterChip(
                    selected = p in priorityFilter,
                    onClick = { viewModel.togglePriorityFilter(p) },
                    label = {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = label,
                            modifier = Modifier.size(14.dp),
                            tint = priorityColor(p),
                        )
                    },
                )
            }
            // Label filter chips (colored text, no icon)
            labels.forEach { lbl ->
                FilterChip(
                    selected = lbl.id in labelFilter,
                    onClick = { viewModel.toggleLabelFilter(lbl.id) },
                    label = {
                        Text(
                            lbl.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = lbl.color.toComposeColor(),
                        )
                    },
                )
            }
        }

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
