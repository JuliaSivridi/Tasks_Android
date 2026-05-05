package com.stler.tasks.ui.alltasks

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
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AllTasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val calendarRepository: CalendarRepository,
) : BaseViewModel() {

    private val from: LocalDate = LocalDate.now()
    private val to:   LocalDate = LocalDate.now().plusDays(366)

    val tasks: StateFlow<List<Task>> = repository.observeAllPendingTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val labels: StateFlow<List<Label>> = repository.observeLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _priorityFilter = MutableStateFlow<Set<Priority>>(emptySet())
    val priorityFilter: StateFlow<Set<Priority>> = _priorityFilter.asStateFlow()

    private val _labelFilter = MutableStateFlow<Set<String>>(emptySet())
    val labelFilter: StateFlow<Set<String>> = _labelFilter.asStateFlow()

    private val _folderFilter = MutableStateFlow<Set<String>>(emptySet())
    val folderFilter: StateFlow<Set<String>> = _folderFilter.asStateFlow()

    private val _calendarFilter = MutableStateFlow<Set<String>>(emptySet())
    val calendarFilter: StateFlow<Set<String>> = _calendarFilter.asStateFlow()

    /** Live events for all selected calendars — hot StateFlow for immediate reactivity. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val eventsFlow: StateFlow<List<CalendarEvent>> =
        calendarRepository.getSelectedCalendarIds().flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyList())
            else calendarRepository.getEventsForCalendars(ids, from, to)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /**
     * Filtered tasks + calendar events sorted together by date/time.
     * Calendar events are only shown when NO task filter (priority/label/folder) is active.
     * When a calendar filter is active, only events from the selected calendars are shown.
     * Tasks with deadlines and events are interleaved chronologically.
     * Tasks without deadlines appear after all dated items, in their natural order.
     */
    val filteredItems: StateFlow<List<ListItem>> = combine(
        combine(tasks, eventsFlow, _calendarFilter) { t, e, cf -> Triple(t, e, cf) },
        _priorityFilter,
        _labelFilter,
        _folderFilter,
    ) { (taskList, events, cf), pf, lf, ff ->
        // Filter matrix:
        //   only calendar filter → no tasks shown, only filtered events
        //   only task filters    → filtered tasks, no events
        //   both                 → filtered tasks + filtered events
        //   neither              → all tasks + all events
        val taskFiltersActive = pf.isNotEmpty() || lf.isNotEmpty() || ff.isNotEmpty()
        val calFiltersActive  = cf.isNotEmpty()

        // When only the calendar filter is active, tasks are hidden entirely
        val filtered = if (calFiltersActive && !taskFiltersActive) {
            emptyList()
        } else {
            taskList.filter { task ->
                (pf.isEmpty() || task.priority in pf) &&
                    (lf.isEmpty() || task.labels.any { it in lf }) &&
                    (ff.isEmpty() || task.folderId in ff)
            }
        }

        val (datedTasks, undatedTasks) = filtered.partition { task ->
            task.deadlineDate.isNotBlank() &&
                runCatching { LocalDate.parse(task.deadlineDate) }.isSuccess
        }

        val eventItems: List<ListItem> = when {
            calFiltersActive  -> events.filter { it.calendarId in cf }.map { ListItem.EventItem(it) }
            taskFiltersActive -> emptyList()
            else              -> events.map { ListItem.EventItem(it) }
        }

        // Priority weight: URGENT=0, IMPORTANT=1, NORMAL=2
        val priorityOf = { task: Task ->
            when (task.priority) { Priority.URGENT -> 0; Priority.IMPORTANT -> 1; else -> 2 }
        }

        // Dated tasks: primary sort by priority, then date → timed-before-allday → time
        val datedTaskItems: List<ListItem> = datedTasks
            .sortedWith(compareBy(
                { priorityOf(it) },
                { it.deadlineDate },
                { if (it.deadlineTime.isBlank()) 1 else 0 },
                { it.deadlineTime },
            ))
            .map { ListItem.TaskItem(it) }

        // Events: no priority — sorted by date → timed-before-allday → time
        val sortedEventItems: List<ListItem> = eventItems.sortedWith(compareBy(
            { (it as? ListItem.EventItem)?.event?.startDate ?: "" },
            { if ((it as? ListItem.EventItem)?.event?.startTime.isNullOrBlank()) 1 else 0 },
            { (it as? ListItem.EventItem)?.event?.startTime ?: "" },
        ))

        // Undated tasks: sorted by priority only
        val undatedTaskItems: List<ListItem> = undatedTasks
            .sortedWith(compareBy { priorityOf(it) })
            .map { ListItem.TaskItem(it) }

        datedTaskItems + sortedEventItems + undatedTaskItems
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** True until the first emission from [filteredItems], then false. */
    val isLoading: StateFlow<Boolean> = filteredItems
        .map { false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

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

    fun completeTask(id: String) = safeLaunch { repository.completeTask(id) }

    fun deleteTask(id: String) = safeLaunch { repository.deleteTask(id) }

    fun deleteEvent(calendarId: String, eventId: String) = safeLaunch {
        calendarRepository.deleteEvent(calendarId, eventId).getOrThrow()
    }

    fun deleteEventSeries(calendarId: String, seriesId: String) = safeLaunch {
        calendarRepository.deleteEventSeries(calendarId, seriesId).getOrThrow()
    }

    fun updateDeadline(
        id         : String,
        date       : String,
        time       : String,
        isRecurring: Boolean,
        recurType  : com.stler.tasks.domain.model.RecurType,
        recurValue : Int,
    ) = safeLaunch {
        val task = tasks.value.find { it.id == id } ?: return@safeLaunch
        repository.updateTask(
            task.copy(
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
        val task = tasks.value.find { it.id == id } ?: return@safeLaunch
        repository.updateTask(task.copy(priority = p, updatedAt = nowIso()))
    }

    fun updateLabels(id: String, lbls: List<String>) = safeLaunch {
        val task = tasks.value.find { it.id == id } ?: return@safeLaunch
        repository.updateTask(task.copy(labels = lbls, updatedAt = nowIso()))
    }

    private fun nowIso(): String = java.time.Instant.now().toString()
}
