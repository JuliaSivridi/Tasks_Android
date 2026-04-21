package com.stler.tasks.ui.alltasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        FilterDropdown(
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

/**
 * Compact single-chip filter control that opens a dropdown with multi-select for
 * priority and label filters. Replaces the old FlowRow of chips which consumed too
 * much vertical space when many labels were present.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(
    labels          : List<Label>,
    priorityFilter  : Set<Priority>,
    labelFilter     : Set<String>,
    onTogglePriority: (Priority) -> Unit,
    onToggleLabel   : (String) -> Unit,
) {
    val activeCount = priorityFilter.size + labelFilter.size
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        FilterChip(
            selected = activeCount > 0,
            onClick  = { expanded = true },
            label    = {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(
                        text  = if (activeCount > 0) "Filters ($activeCount)" else "Filters",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        )
        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // ── Priority ──────────────────────────────────────────────────────
            DropdownMenuItem(
                text    = { Text("Priority", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = {},
                enabled = false,
            )
            listOf(Priority.URGENT to "Urgent", Priority.IMPORTANT to "Important", Priority.NORMAL to "Normal")
                .forEach { (p, name) ->
                    val active = p in priorityFilter
                    DropdownMenuItem(
                        text         = { Text(name) },
                        onClick      = { onTogglePriority(p) },
                        leadingIcon  = {
                            Icon(Icons.Outlined.Flag, null,
                                tint = priorityColor(p), modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = if (active) ({
                            Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp))
                        }) else null,
                    )
                }
            // ── Labels ────────────────────────────────────────────────────────
            if (labels.isNotEmpty()) {
                HorizontalDivider()
                DropdownMenuItem(
                    text    = { Text("Labels", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {},
                    enabled = false,
                )
                labels.forEach { lbl ->
                    val active = lbl.id in labelFilter
                    DropdownMenuItem(
                        text         = { Text(lbl.name, color = lbl.color.toComposeColor()) },
                        onClick      = { onToggleLabel(lbl.id) },
                        leadingIcon  = {
                            Icon(Icons.Outlined.Label, null,
                                tint = lbl.color.toComposeColor(), modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = if (active) ({
                            Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp))
                        }) else null,
                    )
                }
            }
        }
    }
}
