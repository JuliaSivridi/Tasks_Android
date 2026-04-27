package com.stler.tasks.ui.upcoming

import androidx.lifecycle.viewModelScope
import com.stler.tasks.data.repository.CalendarRepository
import com.stler.tasks.data.repository.TaskRepository
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.domain.model.CalendarItem
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.ListItem
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.Task
import com.stler.tasks.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class UpcomingViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val calendarRepository: CalendarRepository,
) : BaseViewModel() {

    private val from: LocalDate = LocalDate.now()
    private val to:   LocalDate = LocalDate.now().plusDays(366)

    private val tasksWithDeadline: StateFlow<List<Task>> =
        repository.observeAllPendingTasksWithDeadline()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Live events for all selected calendars — hot StateFlow for immediate reactivity. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val eventsFlow: StateFlow<List<CalendarEvent>> =
        calendarRepository.getSelectedCalendarIds().flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyList())
            else calendarRepository.getEventsForCalendars(ids, from, to)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val labels: StateFlow<List<Label>> = repository.observeLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _weekOffset = MutableStateFlow(0)
    val weekOffset: StateFlow<Int> = _weekOffset.asStateFlow()

    private val _priorityFilter = MutableStateFlow<Set<Priority>>(emptySet())
    val priorityFilter: StateFlow<Set<Priority>> = _priorityFilter.asStateFlow()

    private val _labelFilter = MutableStateFlow<Set<String>>(emptySet())
    val labelFilter: StateFlow<Set<String>> = _labelFilter.asStateFlow()

    private val _folderFilter = MutableStateFlow<Set<String>>(emptySet())
    val folderFilter: StateFlow<Set<String>> = _folderFilter.asStateFlow()

    private val _calendarFilter = MutableStateFlow<Set<String>>(emptySet())
    val calendarFilter: StateFlow<Set<String>> = _calendarFilter.asStateFlow()

    /** Distinct calendars derived from loaded events — used for the calendar filter chip. */
    val calendarsInEvents: StateFlow<List<CalendarItem>> = eventsFlow
        .map { events ->
            events.distinctBy { it.calendarId }.map { e ->
                CalendarItem(
                    id         = e.calendarId,
                    summary    = e.calendarName,
                    color      = e.calendarColor,
                    isSelected = true,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Mon–Sun of the currently focused week (drives the week strip). */
    val weekDays: StateFlow<List<LocalDate>> = _weekOffset.map { offset ->
        val monday = LocalDate.now()
            .with(DayOfWeek.MONDAY)
            .plusWeeks(offset.toLong())
        (0..6).map { monday.plusDays(it.toLong()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * ALL pending tasks with deadlines + calendar events, filtered, grouped by date and sorted.
     * Calendar events are only shown when NO task filter (priority/label/folder) is active.
     * When a calendar filter is active, only events from the selected calendars are shown.
     */
    val allGroupedTasks: StateFlow<Map<LocalDate, List<ListItem>>> = combine(
        combine(tasksWithDeadline, eventsFlow, _calendarFilter) { t, e, cf -> Triple(t, e, cf) },
        _priorityFilter,
        _labelFilter,
        _folderFilter,
    ) { (tasks, events, cf), pf, lf, ff ->
        val today = LocalDate.now()

        // Filter matrix:
        //   only calendar filter → no tasks shown, only filtered events
        //   only task filters    → filtered tasks, no events
        //   both                 → filtered tasks + filtered events
        //   neither              → all tasks + all events
        val taskFiltersActive = pf.isNotEmpty() || lf.isNotEmpty() || ff.isNotEmpty()
        val calFiltersActive  = cf.isNotEmpty()

        val taskItems: List<ListItem> = if (calFiltersActive && !taskFiltersActive) {
            emptyList()
        } else {
            tasks
                .filter { task ->
                    (pf.isEmpty() || task.priority in pf) &&
                        (lf.isEmpty() || task.labels.any { it in lf }) &&
                        (ff.isEmpty() || task.folderId in ff) &&
                        task.deadlineDate.isNotBlank() &&
                        runCatching { LocalDate.parse(task.deadlineDate) }.isSuccess
                }
                .map { ListItem.TaskItem(it) }
        }

        val eventItems: List<ListItem> = when {
            calFiltersActive  -> events
                .filter { runCatching { LocalDate.parse(it.startDate) }.isSuccess }
                .filter { it.calendarId in cf }
                .map { ListItem.EventItem(it) }
            taskFiltersActive -> emptyList()
            else              -> events
                .filter { runCatching { LocalDate.parse(it.startDate) }.isSuccess }
                .map { ListItem.EventItem(it) }
        }

        (taskItems + eventItems)
            .groupBy { item ->
                val date = when (item) {
                    is ListItem.TaskItem  -> LocalDate.parse(item.task.deadlineDate)
                    is ListItem.EventItem -> LocalDate.parse(item.event.startDate)
                }
                if (date < today) LocalDate.MIN else date
            }
            .toSortedMap()
            .mapValues { (key, list) ->
                list.sortedWith(compareBy(
                    // Within overdue: secondary sort by actual date
                    { when (it) {
                        is ListItem.TaskItem  -> if (key == LocalDate.MIN) it.task.deadlineDate else ""
                        is ListItem.EventItem -> if (key == LocalDate.MIN) it.event.startDate   else ""
                    }},
                    // Timed items (0) before all-day (1)
                    { when (it) {
                        is ListItem.TaskItem  -> if (it.task.deadlineTime.isBlank()) 1 else 0
                        is ListItem.EventItem -> if (it.event.startTime.isBlank())  1 else 0
                    }},
                    // Sort by time string within each group
                    { when (it) {
                        is ListItem.TaskItem  -> it.task.deadlineTime
                        is ListItem.EventItem -> it.event.startTime
                    }},
                ))
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** True until the first emission from [allGroupedTasks], then false. */
    val isLoading: StateFlow<Boolean> = allGroupedTasks
        .map { false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ── Week strip navigation ─────────────────────────────────────────────

    fun shiftWeek(delta: Int) {
        _weekOffset.update { it + delta }
    }

    fun goToToday() {
        _weekOffset.value = 0
    }

    fun onVisibleDateChanged(date: LocalDate) {
        val todayMonday = LocalDate.now().with(DayOfWeek.MONDAY)
        val dateMonday  = date.with(DayOfWeek.MONDAY)
        val newOffset   = ChronoUnit.WEEKS.between(todayMonday, dateMonday).toInt()
        if (newOffset != _weekOffset.value) {
            _weekOffset.value = newOffset
        }
    }

    // ── Filters ───────────────────────────────────────────────────────────

    fun togglePriorityFilter(p: Priority) {
        _priorityFilter.update { if (p in it) it - p else it + p }
    }

    fun toggleLabelFilter(id: String) {
        _labelFilter.update { if (id in it) it - id else it + id }
    }

    fun toggleFolderFilter(id: String) {
        _folderFilter.update { if (id in it) it - id else it + id }
    }

    fun toggleCalendarFilter(id: String) {
        _calendarFilter.update { if (id in it) it - id else it + id }
    }

    fun clearAllFilters() {
        _priorityFilter.value  = emptySet()
        _labelFilter.value     = emptySet()
        _folderFilter.value    = emptySet()
        _calendarFilter.value  = emptySet()
    }

    // ── Task mutations ────────────────────────────────────────────────────

    fun completeTask(id: String) = safeLaunch { repository.completeTask(id) }
    fun deleteTask(id: String)   = safeLaunch { repository.deleteTask(id) }

    fun updateDeadline(
        id         : String,
        date       : String,
        time       : String,
        isRecurring: Boolean,
        recurType  : com.stler.tasks.domain.model.RecurType,
        recurValue : Int,
    ) = safeLaunch {
        val t = tasksWithDeadline.value.find { it.id == id } ?: return@safeLaunch
        repository.updateTask(
            t.copy(
                deadlineDate = date,
                deadlineTime = time,
                isRecurring  = isRecurring,
                recurType    = recurType,
                recurValue   = recurValue,
                updatedAt    = nowIso(),
            )
        )
    }

    fun updatePriority(id: String, p: Priority) = safeLaunch {
        val t = tasksWithDeadline.value.find { it.id == id } ?: return@safeLaunch
        repository.updateTask(t.copy(priority = p, updatedAt = nowIso()))
    }

    fun updateLabels(id: String, lbls: List<String>) = safeLaunch {
        val t = tasksWithDeadline.value.find { it.id == id } ?: return@safeLaunch
        repository.updateTask(t.copy(labels = lbls, updatedAt = nowIso()))
    }

    private fun nowIso() = java.time.Instant.now().toString()
}
