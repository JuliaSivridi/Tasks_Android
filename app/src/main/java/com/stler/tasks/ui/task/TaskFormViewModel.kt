package com.stler.tasks.ui.task

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.stler.tasks.data.remote.dto.CalendarEventRequest
import com.stler.tasks.data.remote.dto.EventDateTime
import com.stler.tasks.data.repository.CalendarRepository
import com.stler.tasks.data.repository.TaskRepository
import com.stler.tasks.domain.model.CalendarItem
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.RecurType
import com.stler.tasks.domain.model.Task
import com.stler.tasks.domain.model.TaskStatus
import com.stler.tasks.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

/** Generates a short ID matching PWA format: prefix_XXXXXXXX (8 lowercase hex chars). */
private fun generateId(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(8)}"

/**
 * Handles create / edit / "add subtask" task form submissions,
 * and direct Google Calendar event creation (EVENT mode).
 * Injected via hiltViewModel() in any screen that needs it.
 *
 * Label sentinel: "__new__:colorHex:name" — creates a new label first, then attaches it.
 */
@HiltViewModel
class TaskFormViewModel @Inject constructor(
    private val repository         : TaskRepository,
    private val calendarRepository : CalendarRepository,
) : BaseViewModel() {

    // ── Event-mode state (observable from Compose) ────────────────────────
    var formMode           by mutableStateOf(FormMode.TASK)
    var endTime            by mutableStateOf("")
    var selectedCalendarId by mutableStateOf("primary")

    /** True while [loadBaseEvent] is fetching the series base event. */
    var baseEventLoading   by mutableStateOf(false)

    // ── Inline recurrence state for EVENT mode (persists between form openings) ─
    // Stores the advanced repeat options shown inline in the event creation form.
    // EndsType is internal → the property must also be internal.
    var             crsByDay         by mutableStateOf<Set<DayOfWeek>>(emptySet())
    var             crsMonthlyIdx    by mutableStateOf(0)
    internal var    crsEnds          by mutableStateOf(EndsType.NEVER)
    var             crsEndDate       by mutableStateOf<LocalDate?>(null)
    var             crsAfterCountStr by mutableStateOf("13")

    private val _selectedCalendars = MutableStateFlow<List<CalendarItem>>(emptyList())
    val selectedCalendars: StateFlow<List<CalendarItem>> = _selectedCalendars.asStateFlow()

    private val _calendarsLoading = MutableStateFlow(false)
    val calendarsLoading: StateFlow<Boolean> = _calendarsLoading.asStateFlow()

    /** Emits a success message after an event is created or updated — sheet shows snackbar then closes. */
    private val _eventCreated = MutableSharedFlow<String>(replay = 0)
    val eventCreated: SharedFlow<String> = _eventCreated.asSharedFlow()

    private var calendarsLoaded = false

    // ── Calendar loading ──────────────────────────────────────────────────

    /**
     * Fetches the user's calendars once (guard: [calendarsLoaded]).
     * Automatically pre-selects "primary" or the first available calendar.
     * Any network error is forwarded to [uiError] via [safeLaunch].
     */
    fun loadCalendars() {
        if (calendarsLoaded) return
        calendarsLoaded = true
        safeLaunch {
            _calendarsLoading.value = true
            val all = calendarRepository.fetchCalendarsAndSave()   // throws on failure → safeLaunch handles it
            // Only calendars the user can write to (owner/writer); filters out holidays, subscriptions, etc.
            val selected = all.filter { it.isSelected && it.accessRole in listOf("writer", "owner") }
            _selectedCalendars.value = selected
            // Keep "primary" if it's in the list; otherwise fall back to the first available.
            if (selected.none { it.id == selectedCalendarId }) {
                selectedCalendarId = selected.firstOrNull()?.id ?: "primary"
            }
            _calendarsLoading.value = false
        }
    }

    // ── Calendar event creation ───────────────────────────────────────────

    /**
     * Creates a Google Calendar event via [CalendarRepository].
     * On success → emits [eventCreated] so the sheet can close itself.
     * On failure → [safeLaunch] logs and forwards a message to [uiError].
     *
     * @param endTime  End time "HH:MM", or "" for all-day / default +1h.
     * @param rrule    RRULE string (e.g. "RRULE:FREQ=WEEKLY;BYDAY=MO"), or null.
     */
    fun createEvent(
        calendarId: String,
        title     : String,
        startDate : String,
        startTime : String,
        endTime   : String,
        rrule     : String?,
    ) = safeLaunch {
        val startDt = buildEventDateTime(startDate, startTime)
        val endDt   = buildEndDateTime(startDate, startTime, endTime)
        val request = CalendarEventRequest(
            summary    = title,
            start      = startDt,
            end        = endDt,
            recurrence = rrule?.let { listOf(it) },
        )
        calendarRepository.createEvent(calendarId, request).getOrThrow()  // re-throw → safeLaunch catches
        _eventCreated.emit("Event created")
    }

    /**
     * Updates an existing Google Calendar event.
     * If [originalCalendarId] is provided and differs from [calendarId], the event is
     * moved to [calendarId] first via the Calendar API move endpoint.
     * On success → emits [eventCreated] (reused signal) so the sheet can close itself.
     */
    fun updateEvent(
        calendarId        : String,
        eventId           : String,
        title             : String,
        startDate         : String,
        startTime         : String,
        endTime           : String,
        rrule             : String?,
        originalCalendarId: String? = null,
    ) = safeLaunch {
        // Move to a different calendar first if the user changed it
        if (originalCalendarId != null && originalCalendarId != calendarId) {
            calendarRepository.moveEvent(originalCalendarId, calendarId, eventId).getOrThrow()
        }
        val startDt = buildEventDateTime(startDate, startTime)
        val endDt   = buildEndDateTime(startDate, startTime, endTime)
        val request = CalendarEventRequest(
            summary    = title,
            start      = startDt,
            end        = endDt,
            recurrence = rrule?.let { listOf(it) },
        )
        calendarRepository.updateEvent(calendarId, eventId, request).getOrThrow()
        _eventCreated.emit("Event updated")
    }

    /**
     * Deletes a single event instance from the API and Room.
     * Errors are forwarded to [uiError] via [safeLaunch].
     */
    fun deleteEvent(calendarId: String, eventId: String) = safeLaunch {
        calendarRepository.deleteEvent(calendarId, eventId).getOrThrow()
    }

    /**
     * Deletes the entire recurring series from the API and all instances from Room.
     * Errors are forwarded to [uiError] via [safeLaunch].
     */
    fun deleteEventSeries(calendarId: String, seriesId: String) = safeLaunch {
        calendarRepository.deleteEventSeries(calendarId, seriesId).getOrThrow()
    }

    /**
     * Fetches the recurring series base event and applies its data to the sheet form state.
     * Called by [TaskFormSheet] when opening an edit for a recurring event instance.
     *
     * @param onResult callback with (title, startDate, startTime, endTime, parsedRRule) —
     *                 all non-null; empty strings where data is absent.
     */
    internal fun loadBaseEvent(
        calendarId: String,
        seriesId  : String,
        onResult  : (title: String, startDate: String, startTime: String, endTime: String,
                     rrule: ParsedRRule?) -> Unit,
    ) = safeLaunch {
        baseEventLoading = true
        val result = calendarRepository.getBaseEvent(calendarId, seriesId)
        baseEventLoading = false
        result.onSuccess { data ->
            val parsed = if (data.rrule.isNotBlank()) parseRRule(data.rrule) else null
            onResult(data.event.title, data.event.startDate, data.event.startTime, data.endTime, parsed)
        }.onFailure {
            // Fall through — form stays pre-filled with the instance data
        }
    }

    // ── DateTime helpers ──────────────────────────────────────────────────

    /**
     * Builds an [EventDateTime] for a start date+time.
     * All-day (time blank) → uses [EventDateTime.date].
     * Timed → uses [EventDateTime.dateTime] with device timezone offset (e.g. "+03:00").
     */
    private fun buildEventDateTime(date: String, time: String): EventDateTime =
        if (time.isBlank()) {
            EventDateTime(date = date)
        } else {
            val zone = ZoneId.systemDefault()
            val zdt  = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)
            EventDateTime(
                dateTime = zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                // Required for recurring timed events; safe to include for non-recurring too.
                timeZone = zone.id,
            )
        }

    /**
     * Builds the end [EventDateTime]:
     * - All-day (startTime blank) → end.date = next day (Google API convention).
     * - Timed, endTime blank → end = start + 1 hour.
     * - Timed, endTime set → end = startDate + endTime (same date, different time).
     */
    private fun buildEndDateTime(startDate: String, startTime: String, endTime: String): EventDateTime {
        if (startTime.isBlank()) {
            // All-day event: end.date must be the day AFTER the last day
            val nextDay = LocalDate.parse(startDate).plusDays(1).toString()
            return EventDateTime(date = nextDay)
        }
        val startLocal = LocalTime.parse(startTime)
        val endT = when {
            endTime.isBlank() -> {
                // Default end = start + 1 hour
                startLocal.plusHours(1).format(DateTimeFormatter.ofPattern("HH:mm"))
            }
            !LocalTime.parse(endTime).isAfter(startLocal) -> {
                // End time is at or before start time (happens when user moves start
                // past the old end without updating end) — clamp to start + 1 hour.
                startLocal.plusHours(1).format(DateTimeFormatter.ofPattern("HH:mm"))
            }
            else -> endTime
        }
        val zone = ZoneId.systemDefault()
        val zdt  = ZonedDateTime.of(LocalDate.parse(startDate), LocalTime.parse(endT), zone)
        return EventDateTime(
            dateTime = zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            timeZone = zone.id,
        )
    }

    // ── Task operations (unchanged) ───────────────────────────────────────

    /**
     * Creates a new task (and any new labels embedded as sentinels in [result.labelIds]).
     */
    fun createTask(result: TaskFormResult, sortOrder: Int = 0) = viewModelScope.launch {
        val resolvedLabelIds = resolveLabelSentinels(result.labelIds)
        val now = nowIso()
        repository.createTask(
            Task(
                id           = generateId("tsk"),
                parentId     = result.parentId,
                folderId     = result.folderId,
                title        = result.title,
                status       = TaskStatus.PENDING,
                priority     = result.priority,
                deadlineDate = result.deadlineDate,
                deadlineTime = result.deadlineTime,
                isRecurring  = result.isRecurring,
                recurType    = result.recurType,
                recurValue   = result.recurValue,
                labels       = resolvedLabelIds,
                sortOrder    = sortOrder,
                createdAt    = now,
                updatedAt    = now,
            )
        )
    }

    /**
     * Updates an existing task in place, preserving fields not present in the form
     * (status, completedAt, isExpanded, etc.).
     */
    fun updateTask(original: Task, result: TaskFormResult) = viewModelScope.launch {
        val resolvedLabelIds = resolveLabelSentinels(result.labelIds)
        repository.updateTask(
            original.copy(
                title        = result.title,
                folderId     = result.folderId,
                parentId     = result.parentId,
                priority     = result.priority,
                labels       = resolvedLabelIds,
                deadlineDate = result.deadlineDate,
                deadlineTime = result.deadlineTime,
                isRecurring  = result.isRecurring,
                recurType    = result.recurType,
                recurValue   = result.recurValue,
                updatedAt    = nowIso(),
            )
        )
    }

    /**
     * Processes label sentinels of the form "__new__:colorHex:name".
     * For each sentinel: creates the label in Room + Sheets, then returns its real ID.
     * Normal IDs are passed through unchanged.
     */
    private suspend fun resolveLabelSentinels(labelIds: List<String>): List<String> {
        val result = mutableListOf<String>()
        for (id in labelIds) {
            if (id.startsWith("__new__:")) {
                val parts = id.split(":", limit = 3)
                if (parts.size == 3) {
                    val color = parts[1]
                    val name  = parts[2]
                    val newLabel = Label(
                        id    = generateId("lbl"),
                        name  = name,
                        color = color,
                    )
                    repository.createLabel(newLabel)
                    result.add(newLabel.id)
                }
            } else {
                result.add(id)
            }
        }
        return result
    }

    private fun nowIso(): String = Instant.now().toString()
}
