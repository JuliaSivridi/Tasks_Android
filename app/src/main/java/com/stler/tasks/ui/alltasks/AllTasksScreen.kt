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
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.tasks.domain.model.ListItem
import com.stler.tasks.ui.calendar.CalendarEventItem
import com.stler.tasks.ui.task.TaskItem
import com.stler.tasks.ui.util.EmptyState
import com.stler.tasks.ui.util.ErrorSnackbarEffect
import com.stler.tasks.ui.util.ShimmerTaskList

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
    val featureFlags       by viewModel.featureFlags.collectAsStateWithLifecycle()

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
                                task        = task,
                                labels      = labels,
                                showFolder  = featureFlags.foldersEnabled,
                                showLabels  = featureFlags.labelsEnabled,
                                featureFlags = featureFlags,
                                folderName  = folders.find { it.id == task.folderId }?.name,
                                folderColor = folders.find { it.id == task.folderId }?.color,
                                onCheckedChange  = { checked -> if (checked) viewModel.completeTask(task.id) },
                                onExpand         = {},
                                onDeadlineChange = { d, t, isRec, rType, rVal -> viewModel.updateDeadline(task.id, d, t, isRec, rType, rVal) },
                                onPriorityChange = { p -> viewModel.updatePriority(task.id, p) },
                                onLabelChange    = { l -> viewModel.updateLabels(task.id, l) },
                                onAddSubtask     = { onAddSubtask(task) },
                                onEdit           = { onEditTask(task) },
                                onDelete         = { viewModel.deleteTask(task.id) },
                            )
                        }
                        is ListItem.EventItem -> {
                            val event = item.event
                            CalendarEventItem(
                                event          = event,
                                showDate       = true,
                                onEdit         = if (event.isEditable) ({ onEditEvent(event) }) else null,
                                onEditSchedule = if (event.isEditable) ({ onEditEventSchedule(event) }) else null,
                                onDelete       = if (event.isEditable) ({ viewModel.deleteEvent(event.calendarId, event.id) }) else null,
                                onDeleteSeries = if (event.isEditable) ({ viewModel.deleteEventSeries(event.calendarId, event.recurringEventId) }) else null,
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

