package com.stler.tasks.ui.alltasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
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
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.ui.theme.Border
import com.stler.tasks.ui.theme.OnChipSelected
import com.stler.tasks.ui.task.TaskItem
import com.stler.tasks.ui.util.ErrorSnackbarEffect
import com.stler.tasks.ui.task.priorityColor
import com.stler.tasks.util.toComposeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTasksScreen(
    onEditTask   : (com.stler.tasks.domain.model.Task) -> Unit = {},
    onAddSubtask : (com.stler.tasks.domain.model.Task) -> Unit = {},
    viewModel    : AllTasksViewModel = hiltViewModel(),
) {
    val filteredTasks  by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val labels         by viewModel.labels.collectAsStateWithLifecycle()
    val folders        by viewModel.folders.collectAsStateWithLifecycle()
    val priorityFilter by viewModel.priorityFilter.collectAsStateWithLifecycle()
    val labelFilter    by viewModel.labelFilter.collectAsStateWithLifecycle()
    val folderFilter   by viewModel.folderFilter.collectAsStateWithLifecycle()

    ErrorSnackbarEffect(viewModel)

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

// ── Neutral chip colors — bypasses Material You warm tint ────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun neutralChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor   = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceVariant else Border,
    selectedLeadingIconColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurfaceVariant else OnChipSelected,
    selectedLabelColor       = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurfaceVariant else OnChipSelected,
)

/**
 * Compact filter bar: three icon-only chips, each opening its own multi-select dropdown.
 *
 *   [✕]  [🚩]  [🏷]  [📁]
 *
 * When a filter is active the chip renders selected (filled background) and shows
 * a small count badge in the label slot — no long text, so the row never wraps.
 * The [✕] reset button is visible only when at least one filter is active.
 *
 * Parameters [showLabelFilter] / [showFolderFilter] hide the respective chip when
 * not relevant (e.g. LabelScreen hides the label chip, FolderScreen hides folder chip).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBar(
    labels          : List<Label>,
    folders         : List<Folder> = emptyList(),
    priorityFilter  : Set<Priority>,
    labelFilter     : Set<String>,
    folderFilter    : Set<String> = emptySet(),
    onTogglePriority: (Priority) -> Unit,
    onToggleLabel   : (String) -> Unit,
    onToggleFolder  : (String) -> Unit = {},
    onClearAll      : () -> Unit = {},
    showLabelFilter : Boolean = true,
    showFolderFilter: Boolean = true,
) {
    val hasFilters = priorityFilter.isNotEmpty() || labelFilter.isNotEmpty() || folderFilter.isNotEmpty()

    var priorityExpanded by remember { mutableStateOf(false) }
    var labelsExpanded   by remember { mutableStateOf(false) }
    var foldersExpanded  by remember { mutableStateOf(false) }

    Row(
        modifier              = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {

        // ── Clear-all button — visible only when a filter is active ───────
        if (hasFilters) {
            IconButton(onClick = onClearAll, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Clear all filters",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Priority chip ─────────────────────────────────────────────────
        Box {
            FilterChip(
                selected    = priorityFilter.isNotEmpty(),
                onClick     = { priorityExpanded = true },
                leadingIcon = { Icon(Icons.Outlined.Flag, null, Modifier.size(16.dp)) },
                label       = {
                    if (priorityFilter.isNotEmpty())
                        Text(priorityFilter.size.toString(), style = MaterialTheme.typography.labelSmall)
                },
                colors = neutralChipColors(),
            )
            DropdownMenu(
                expanded         = priorityExpanded,
                onDismissRequest = { priorityExpanded = false },
            ) {
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
            }
        }

        // ── Labels chip ───────────────────────────────────────────────────
        if (showLabelFilter && labels.isNotEmpty()) {
            Box {
                FilterChip(
                    selected    = labelFilter.isNotEmpty(),
                    onClick     = { labelsExpanded = true },
                    leadingIcon = { Icon(Icons.Outlined.Label, null, Modifier.size(16.dp)) },
                    label       = {
                        if (labelFilter.isNotEmpty())
                            Text(labelFilter.size.toString(), style = MaterialTheme.typography.labelSmall)
                    },
                    colors = neutralChipColors(),
                )
                DropdownMenu(
                    expanded         = labelsExpanded,
                    onDismissRequest = { labelsExpanded = false },
                ) {
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

        // ── Folders chip ──────────────────────────────────────────────────
        if (showFolderFilter && folders.isNotEmpty()) {
            Box {
                FilterChip(
                    selected    = folderFilter.isNotEmpty(),
                    onClick     = { foldersExpanded = true },
                    leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(16.dp)) },
                    label       = {
                        if (folderFilter.isNotEmpty())
                            Text(folderFilter.size.toString(), style = MaterialTheme.typography.labelSmall)
                    },
                    colors = neutralChipColors(),
                )
                DropdownMenu(
                    expanded         = foldersExpanded,
                    onDismissRequest = { foldersExpanded = false },
                ) {
                    folders.forEach { fld ->
                        val active = fld.id in folderFilter
                        DropdownMenuItem(
                            text         = { Text(fld.name, color = fld.color.toComposeColor()) },
                            onClick      = { onToggleFolder(fld.id) },
                            leadingIcon  = {
                                Icon(Icons.Outlined.Folder, null,
                                    tint = fld.color.toComposeColor(), modifier = Modifier.size(16.dp))
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
}
