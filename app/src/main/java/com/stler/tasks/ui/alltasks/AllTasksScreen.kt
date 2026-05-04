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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FormatListBulleted
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.tasks.domain.model.CalendarItem
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.ListItem
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.ui.calendar.CalendarEventItem
import com.stler.tasks.ui.theme.OnChipSelected
import com.stler.tasks.ui.theme.SelectedHighlightLight
import com.stler.tasks.ui.task.TaskItem
import com.stler.tasks.ui.util.EmptyState
import com.stler.tasks.ui.util.ErrorSnackbarEffect
import com.stler.tasks.ui.util.ShimmerTaskList
import com.stler.tasks.ui.task.priorityColor
import com.stler.tasks.util.toComposeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTasksScreen(
    onEditTask          : (com.stler.tasks.domain.model.Task) -> Unit = {},
    onAddSubtask        : (com.stler.tasks.domain.model.Task) -> Unit = {},
    onEditEvent         : (com.stler.tasks.domain.model.CalendarEvent) -> Unit = {},
    onEditEventSchedule : (com.stler.tasks.domain.model.CalendarEvent) -> Unit = {},
    viewModel           : AllTasksViewModel = hiltViewModel(),
) {
    val filteredItems      by viewModel.filteredItems.collectAsStateWithLifecycle()
    val isLoading          by viewModel.isLoading.collectAsStateWithLifecycle()
    val labels             by viewModel.labels.collectAsStateWithLifecycle()
    val folders            by viewModel.folders.collectAsStateWithLifecycle()
    val priorityFilter     by viewModel.priorityFilter.collectAsStateWithLifecycle()
    val labelFilter        by viewModel.labelFilter.collectAsStateWithLifecycle()
    val folderFilter       by viewModel.folderFilter.collectAsStateWithLifecycle()
    val calendarFilter     by viewModel.calendarFilter.collectAsStateWithLifecycle()
    val calendarsInEvents  by viewModel.calendarsInEvents.collectAsStateWithLifecycle()

    ErrorSnackbarEffect(viewModel)

    val today     = remember { LocalDate.now() }
    val listState = rememberLazyListState()

    // Find the first item index whose date is today or in the future.
    // The list is sorted: past/overdue → today → future → undated.
    val firstTodayIdx = remember(filteredItems) {
        filteredItems.indexOfFirst { item ->
            val date = when (item) {
                is ListItem.TaskItem  -> item.task.deadlineDate
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                is ListItem.EventItem -> runCatching { LocalDate.parse(item.event.startDate) }.getOrNull()
            }
            date != null && !date.isBefore(today)
        }
    }

    // Scroll to today once when data first loads.
    // LaunchedEffect restarts when firstTodayIdx changes (tasks arrive before
    // events, so the index might shift). The delay(200) acts as a debounce:
    // if events arrive within 200 ms, the old coroutine is cancelled and the
    // new one re-runs with the updated index that now includes today's events.
    var hasScrolledToInitial by remember { mutableStateOf(false) }
    LaunchedEffect(firstTodayIdx) {
        if (!hasScrolledToInitial && firstTodayIdx >= 0) {
            delay(200)
            listState.scrollToItem(firstTodayIdx)
            hasScrolledToInitial = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FilterBar(
            labels           = labels,
            folders          = folders,
            priorityFilter   = priorityFilter,
            labelFilter      = labelFilter,
            folderFilter     = folderFilter,
            calendars        = calendarsInEvents,
            calendarFilter   = calendarFilter,
            onTogglePriority = { viewModel.togglePriorityFilter(it) },
            onToggleLabel    = { viewModel.toggleLabelFilter(it) },
            onToggleFolder   = { viewModel.toggleFolderFilter(it) },
            onToggleCalendar = { viewModel.toggleCalendarFilter(it) },
            onClearAll       = { viewModel.clearAllFilters() },
        )
        when {
            isLoading -> ShimmerTaskList(modifier = Modifier.fillMaxSize())
            filteredItems.isEmpty() -> EmptyState(
                icon     = Icons.Outlined.FormatListBulleted,
                message  = "No tasks",
                subtitle = "Add a task to get started",
            )
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(
                    items = filteredItems,
                    key = { item -> when (item) {
                        is ListItem.TaskItem  -> "task_${item.task.id}"
                        is ListItem.EventItem -> "event_${item.event.id}"
                    }},
                ) { item ->
                    when (item) {
                        is ListItem.TaskItem -> {
                            val task = item.task
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
                        }
                        is ListItem.EventItem -> {
                            CalendarEventItem(
                                event           = item.event,
                                showDate        = true,
                                onEdit          = { onEditEvent(item.event) },
                                onEditSchedule  = { onEditEventSchedule(item.event) },
                                onDelete        = { viewModel.deleteEvent(item.event.calendarId, item.event.id) },
                                onDeleteSeries  = { viewModel.deleteEventSeries(item.event.calendarId, item.event.recurringEventId) },
                            )
                        }
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

// ── Neutral chip colors — bypasses Material You warm tint ────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun neutralChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor   = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.primaryContainer else SelectedHighlightLight,
    selectedLeadingIconColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onPrimaryContainer else OnChipSelected,
    selectedLabelColor       = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onPrimaryContainer else OnChipSelected,
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
    calendars       : List<CalendarItem> = emptyList(),
    calendarFilter  : Set<String> = emptySet(),
    onTogglePriority: (Priority) -> Unit,
    onToggleLabel   : (String) -> Unit,
    onToggleFolder  : (String) -> Unit = {},
    onToggleCalendar: (String) -> Unit = {},
    onClearAll      : () -> Unit = {},
    showLabelFilter : Boolean = true,
    showFolderFilter: Boolean = true,
) {
    val hasFilters = priorityFilter.isNotEmpty() || labelFilter.isNotEmpty() ||
        folderFilter.isNotEmpty() || calendarFilter.isNotEmpty()

    var priorityExpanded  by remember { mutableStateOf(false) }
    var labelsExpanded    by remember { mutableStateOf(false) }
    var foldersExpanded   by remember { mutableStateOf(false) }
    var calendarsExpanded by remember { mutableStateOf(false) }

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
                        Text(priorityFilter.size.toString(), style = MaterialTheme.typography.bodyMedium)
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
                            Text(labelFilter.size.toString(), style = MaterialTheme.typography.bodyMedium)
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
                            Text(folderFilter.size.toString(), style = MaterialTheme.typography.bodyMedium)
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

        // ── Calendars chip — visible when events are present OR a filter is active ──
        if (calendars.isNotEmpty() || calendarFilter.isNotEmpty()) {
            Box {
                FilterChip(
                    selected    = calendarFilter.isNotEmpty(),
                    onClick     = { calendarsExpanded = true },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp)) },
                    label       = {
                        if (calendarFilter.isNotEmpty())
                            Text(calendarFilter.size.toString(), style = MaterialTheme.typography.bodyMedium)
                    },
                    colors = neutralChipColors(),
                )
                DropdownMenu(
                    expanded         = calendarsExpanded,
                    onDismissRequest = { calendarsExpanded = false },
                ) {
                    calendars.forEach { cal ->
                        val active = cal.id in calendarFilter
                        val calColor = cal.color.toComposeColor()
                        DropdownMenuItem(
                            text         = { Text(cal.summary, color = calColor) },
                            onClick      = { onToggleCalendar(cal.id) },
                            leadingIcon  = {
                                Icon(Icons.Outlined.CalendarMonth, null,
                                    tint = calColor, modifier = Modifier.size(16.dp))
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
