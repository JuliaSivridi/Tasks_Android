package com.stler.tasks.ui.upcoming

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.ui.task.TaskItem
import com.stler.tasks.ui.task.priorityColor
import com.stler.tasks.util.toComposeColor
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UpcomingScreen(
    onEditTask   : (com.stler.tasks.domain.model.Task) -> Unit = {},
    onAddSubtask : (com.stler.tasks.domain.model.Task) -> Unit = {},
    viewModel    : UpcomingViewModel = hiltViewModel(),
) {
    val allGroupedTasks by viewModel.allGroupedTasks.collectAsStateWithLifecycle()
    val weekDays        by viewModel.weekDays.collectAsStateWithLifecycle()
    val weekOffset      by viewModel.weekOffset.collectAsStateWithLifecycle()
    val labels          by viewModel.labels.collectAsStateWithLifecycle()
    val folders         by viewModel.folders.collectAsStateWithLifecycle()
    val priorityFilter  by viewModel.priorityFilter.collectAsStateWithLifecycle()
    val labelFilter     by viewModel.labelFilter.collectAsStateWithLifecycle()

    val today    = remember { LocalDate.now() }
    val listState = rememberLazyListState()
    val scope    = rememberCoroutineScope()

    // Sorted list of dates that have tasks
    val orderedDates = remember(allGroupedTasks) { allGroupedTasks.keys.toList() }

    // Compute the flat item index of a date's header in the LazyColumn
    // Structure: per date → 1 header item + N task items
    fun itemIndexOf(date: LocalDate): Int {
        var idx = 0
        for (d in orderedDates) {
            if (d == date) return idx
            idx += 1 + (allGroupedTasks[d]?.size ?: 0)
        }
        return 0
    }

    // Helper: first date in week starting on [weekMon] that has tasks, or null
    fun firstTaskDateInWeek(weekMon: LocalDate): LocalDate? =
        orderedDates.firstOrNull { it in weekMon..weekMon.plusDays(6) }

    // ── Scroll → week strip sync ──────────────────────────────────────────
    // Use rememberUpdatedState so the snapshotFlow always sees fresh data
    // even though LaunchedEffect restarts only when listState changes.
    val latestDates  = rememberUpdatedState(orderedDates)
    val latestTasks  = rememberUpdatedState(allGroupedTasks)

    LaunchedEffect(listState) {
        snapshotFlow {
            // Both listState (snapshot state) and the rememberUpdatedState values
            // are tracked here, so this re-runs on list scroll AND on data changes.
            val firstIdx = listState.firstVisibleItemIndex
            val dates    = latestDates.value
            val tasks    = latestTasks.value
            var idx = 0
            for (date in dates) {
                val count = tasks[date]?.size ?: 0
                if (firstIdx <= idx + count) return@snapshotFlow date
                idx += 1 + count
            }
            dates.lastOrNull()
        }
            .filterNotNull()
            .distinctUntilChanged()
            .debounce(120)
            // LocalDate.MIN is the synthetic overdue group key — skip week-strip sync for it.
            .collect { date -> if (date != LocalDate.MIN) viewModel.onVisibleDateChanged(date) }
    }

    val isCurrentWeek = weekDays.contains(today)

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Week strip ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    // If the previous week has tasks, scroll the list — the scroll
                    // listener will update the week strip automatically.
                    // If it's empty, update the strip directly (no list movement).
                    val newMon = LocalDate.now().with(DayOfWeek.MONDAY)
                        .plusWeeks((weekOffset - 1).toLong())
                    val target = firstTaskDateInWeek(newMon)
                    if (target != null) {
                        scope.launch { listState.animateScrollToItem(itemIndexOf(target)) }
                    } else {
                        viewModel.shiftWeek(-1)
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous week",
                    modifier = Modifier.size(20.dp))
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                weekDays.forEach { date ->
                    DayPill(
                        date = date,
                        isToday = date == today,
                        hasTasks = allGroupedTasks.containsKey(date),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            IconButton(
                onClick = {
                    val newMon = LocalDate.now().with(DayOfWeek.MONDAY)
                        .plusWeeks((weekOffset + 1).toLong())
                    val target = firstTaskDateInWeek(newMon)
                    if (target != null) {
                        scope.launch { listState.animateScrollToItem(itemIndexOf(target)) }
                    } else {
                        viewModel.shiftWeek(1)
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Next week",
                    modifier = Modifier.size(20.dp))
            }

            IconButton(
                onClick = {
                    viewModel.goToToday()
                    val target = orderedDates.firstOrNull { it >= today } ?: orderedDates.firstOrNull()
                    if (target != null) {
                        scope.launch { listState.animateScrollToItem(itemIndexOf(target)) }
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = "Go to today",
                    modifier = Modifier.size(20.dp),
                    tint = if (isCurrentWeek) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Filter pills ──────────────────────────────────────────────────
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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

        // ── Task list — all dates, scrollable ─────────────────────────────
        if (orderedDates.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No upcoming tasks",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
            ) {
                orderedDates.forEach { date ->
                    val tasksForDate = allGroupedTasks[date] ?: emptyList()

                    item(key = "header_$date") {
                        DayHeader(date = date, today = today)
                    }

                    items(tasksForDate, key = { it.id }) { task ->
                        TaskItem(
                            task = task,
                            labels = labels,
                            showFolder = true,
                            showDateInDeadline = false,
                            folderName = folders.find { it.id == task.folderId }?.name,
                            folderColor = folders.find { it.id == task.folderId }?.color,
                            onCheckedChange = { checked ->
                                if (checked) viewModel.completeTask(task.id)
                            },
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
    }
}

// ── Day header ────────────────────────────────────────────────────────────────

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    // LocalDate.MIN is the synthetic key used to group all overdue tasks together.
    val label = if (date == LocalDate.MIN) {
        "Overdue"
    } else {
        val datePart = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        val special = when (date) {
            today             -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else              -> null
        }
        val weekdayPart = date.dayOfWeek
            .getDisplayName(TextStyle.FULL, Locale.getDefault())
            .replaceFirstChar { it.uppercase() }
        buildString {
            append(datePart)
            if (special != null) { append(" ‧ "); append(special) }
            append(" ‧ ")
            append(weekdayPart)
        }
    }

    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (date == LocalDate.MIN) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

// ── Day pill ──────────────────────────────────────────────────────────────────

@Composable
private fun DayPill(
    date: LocalDate,
    isToday: Boolean,
    hasTasks: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isToday) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            style = MaterialTheme.typography.bodySmall,
            color = if (isToday) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isToday  -> Color(0xFF16a34a)
                        hasTasks -> MaterialTheme.colorScheme.primary
                        else     -> Color.Transparent
                    }
                ),
        )
    }
}
