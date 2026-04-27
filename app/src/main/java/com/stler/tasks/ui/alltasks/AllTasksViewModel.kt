package com.stler.tasks.ui.alltasks

import androidx.lifecycle.viewModelScope
import com.stler.tasks.data.repository.CalendarRepository
import com.stler.tasks.data.repository.TaskRepository
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.ListItem
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.Task
import com.stler.tasks.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
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

    /** Live events for all selected calendars — switches when selected IDs change. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val eventsFlow: Flow<List<CalendarEvent>> =
        calendarRepository.getSelectedCalendarIds().flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyList())
            else calendarRepository.getEventsForCalendars(ids, from, to)
        }

    /**
     * Filtered tasks + calendar events sorted together by date/time.
     * Tasks with deadlines and events are interleaved chronologically.
     * Tasks without deadlines appear after all dated items, in their natural order.
     */
    val filteredItems: StateFlow<List<ListItem>> = combine(
        combine(tasks, eventsFlow) { t, e -> Pair(t, e) },
        _priorityFilter,
        _labelFilter,
        _folderFilter,
    ) { (taskList, events), pf, lf, ff ->
        val filtered = taskList.filter { task ->
            (pf.isEmpty() || task.priority in pf) &&
                (lf.isEmpty() || task.labels.any { it in lf }) &&
                (ff.isEmpty() || task.folderId in ff)
        }

        val (datedTasks, undatedTasks) = filtered.partition { task ->
            task.deadlineDate.isNotBlank() &&
                runCatching { LocalDate.parse(task.deadlineDate) }.isSuccess
        }

        val datedItems: List<ListItem> =
            (datedTasks.map { ListItem.TaskItem(it) } + events.map { ListItem.EventItem(it) })
                .sortedWith(compareBy(
                    // Primary: date string (ISO format sorts correctly)
                    { when (it) {
                        is ListItem.TaskItem  -> it.task.deadlineDate
                        is ListItem.EventItem -> it.event.startDate
                    }},
                    // Timed items (0) before all-day (1) within the same date
                    { when (it) {
                        is ListItem.TaskItem  -> if (it.task.deadlineTime.isBlank()) 1 else 0
                        is ListItem.EventItem -> if (it.event.startTime.isBlank()) 1 else 0
                    }},
                    // Secondary: time string
                    { when (it) {
                        is ListItem.TaskItem  -> it.task.deadlineTime
                        is ListItem.EventItem -> it.event.startTime
                    }},
                ))

        datedItems + undatedTasks.map { ListItem.TaskItem(it) }
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

    fun clearAllFilters() {
        _priorityFilter.value = emptySet()
        _labelFilter.value    = emptySet()
        _folderFilter.value   = emptySet()
    }

    fun completeTask(id: String) = safeLaunch { repository.completeTask(id) }

    fun deleteTask(id: String) = safeLaunch { repository.deleteTask(id) }

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
