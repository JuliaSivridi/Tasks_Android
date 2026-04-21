package com.stler.tasks.ui.alltasks

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
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.ui.task.TaskItem
import com.stler.tasks.ui.task.priorityColor
import com.stler.tasks.util.toComposeColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AllTasksScreen(
    onEditTask   : (com.stler.tasks.domain.model.Task) -> Unit = {},
    onAddSubtask : (com.stler.tasks.domain.model.Task) -> Unit = {},
    viewModel    : AllTasksViewModel = hiltViewModel(),
) {
    val filteredTasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val priorityFilter by viewModel.priorityFilter.collectAsStateWithLifecycle()
    val labelFilter by viewModel.labelFilter.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        FilterPillsRow(
            labels = labels,
            priorityFilter = priorityFilter,
            labelFilter = labelFilter,
            onTogglePriority = { viewModel.togglePriorityFilter(it) },
            onToggleLabel = { viewModel.toggleLabelFilter(it) },
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun FilterPillsRow(
    labels: List<Label>,
    priorityFilter: Set<Priority>,
    labelFilter: Set<String>,
    onTogglePriority: (Priority) -> Unit,
    onToggleLabel: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            Priority.URGENT to "Urgent",
            Priority.IMPORTANT to "Important",
            Priority.NORMAL to "Normal",
        ).forEach { (p, label) ->
            FilterChip(
                selected = p in priorityFilter,
                onClick = { onTogglePriority(p) },
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
        labels.forEach { lbl ->
            FilterChip(
                selected = lbl.id in labelFilter,
                onClick = { onToggleLabel(lbl.id) },
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
}
