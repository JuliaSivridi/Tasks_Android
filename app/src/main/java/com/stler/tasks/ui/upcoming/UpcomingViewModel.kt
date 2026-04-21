package com.stler.tasks.ui.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stler.tasks.data.repository.TaskRepository
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class UpcomingViewModel @Inject constructor(
    private val repository: TaskRepository,
) : ViewModel() {

    private val tasksWithDeadline: StateFlow<List<Task>> =
        repository.observeAllPendingTasksWithDeadline()
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

    /** Mon–Sun of the currently focused week (drives the week strip). */
    val weekDays: StateFlow<List<LocalDate>> = _weekOffset.map { offset ->
        val monday = LocalDate.now()
            .with(DayOfWeek.MONDAY)
            .plusWeeks(offset.toLong())
        (0..6).map { monday.plusDays(it.toLong()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * ALL pending tasks with deadlines, filtered, grouped by date and sorted.
     * Errors inside the combine lambda are caught per-invocation so the Flow
     * never terminates — future filter changes still re-trigger the block.
     */
    val allGroupedTasks: StateFlow<Map<LocalDate, List<Task>>> = combine(
        tasksWithDeadline,
        _priorityFilter,
        _labelFilter,
    ) { tasks, pf, lf ->
        try {
            val today = LocalDate.now()
            tasks
                .filter { task ->
                    (pf.isEmpty() || task.priority in pf) &&
                        (lf.isEmpty() || task.labels.any { it in lf }) &&
                        runCatching { LocalDate.parse(task.deadlineDate) }.isSuccess
                }
                // All overdue dates (< today) collapse into LocalDate.MIN so they appear
                // under a single "Overdue" header — same behaviour as the PWA.
                .groupBy { task ->
                    val date = LocalDate.parse(task.deadlineDate)
                    if (date < today) LocalDate.MIN else date
                }
                .toSortedMap()
                .mapValues { (key, list) ->
                    list.sortedWith(compareBy(
                        // Within the overdue group, sort by actual deadline date first
                        { if (key == LocalDate.MIN) it.deadlineDate else "" },
                        { if (it.deadlineTime.isBlank()) 1 else 0 },
                        { it.deadlineTime },
                        { it.createdAt },
                    ))
                }
        } catch (_: Exception) {
            emptyMap()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ── Week strip navigation ─────────────────────────────────────────────

    fun shiftWeek(delta: Int) {
        _weekOffset.update { it + delta }
    }

    fun goToToday() {
        _weekOffset.value = 0
    }

    /**
     * Called by the screen when the topmost visible date section changes while scrolling.
     * Updates the week strip to show the week containing [date].
     * No-op if the new week offset is the same as current (prevents recomposition churn).
     */
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

    // ── Task mutations ────────────────────────────────────────────────────

    fun completeTask(id: String) = viewModelScope.launch { repository.completeTask(id) }
    fun deleteTask(id: String)   = viewModelScope.launch { repository.deleteTask(id) }

    fun updateDeadline(
        id         : String,
        date       : String,
        time       : String,
        isRecurring: Boolean,
        recurType  : com.stler.tasks.domain.model.RecurType,
        recurValue : Int,
    ) = viewModelScope.launch {
        val t = tasksWithDeadline.value.find { it.id == id } ?: return@launch
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

    fun updatePriority(id: String, p: Priority) = viewModelScope.launch {
        val t = tasksWithDeadline.value.find { it.id == id } ?: return@launch
        repository.updateTask(t.copy(priority = p, updatedAt = nowIso()))
    }

    fun updateLabels(id: String, lbls: List<String>) = viewModelScope.launch {
        val t = tasksWithDeadline.value.find { it.id == id } ?: return@launch
        repository.updateTask(t.copy(labels = lbls, updatedAt = nowIso()))
    }

    private fun nowIso() = java.time.Instant.now().toString()
}
