package com.stler.tasks.ui.task

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import java.time.DayOfWeek
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.RecurType
import com.stler.tasks.domain.model.Task
import com.stler.tasks.ui.theme.Border
import com.stler.tasks.ui.theme.OnChipSelected
import com.stler.tasks.ui.util.ErrorSnackbarEffect
import com.stler.tasks.ui.util.LocalSnackbarHostState
import com.stler.tasks.util.toComposeColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Single bottom sheet for both Create and Edit task/event.
 *
 * TASK mode — Edit mode: pass a non-null [task]. Create mode: pass [task] = null.
 * EVENT mode — always creates a new Google Calendar event; [task] is ignored.
 *
 * [viewModel] handles direct Calendar API calls (EVENT mode) and label sentinel resolution.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskFormSheet(
    task            : Task? = null,
    calendarEvent   : CalendarEvent? = null,
    /**
     * When true (Schedule-icon edit path): the form shows only the date/time/repeat
     * fields — title and calendar picker are hidden. The event title is preserved
     * from [calendarEvent] but not editable.
     */
    scheduleOnly    : Boolean = false,
    initialFolderId : String = "fld-inbox",
    initialParentId : String = "",
    labels          : List<Label>,
    folders         : List<Folder>,
    onConfirm       : (TaskFormResult) -> Unit,
    onDismiss       : () -> Unit,
    viewModel       : TaskFormViewModel = hiltViewModel(),
) {
    val isEditing      = task != null
    val isEditingEvent = calendarEvent != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── Forward ViewModel errors to the root Snackbar ─────────────────────
    ErrorSnackbarEffect(viewModel)

    // ── Close sheet + show success snackbar after event create/update ────
    val snackbarHost = LocalSnackbarHostState.current
    LaunchedEffect(Unit) {
        viewModel.eventCreated.collect { message ->
            onDismiss()
            // Show snackbar in NonCancellable context: onDismiss() removes this composable
            // from the tree on the next recomposition, which would cancel this coroutine
            // before showSnackbar() runs. NonCancellable ensures the snackbar always shows.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                snackbarHost?.showSnackbar(message, duration = androidx.compose.material3.SnackbarDuration.Short)
            }
        }
    }

    // ── Form state ────────────────────────────────────────────────────────

    var title by remember(task, calendarEvent) {
        mutableStateOf(task?.title ?: calendarEvent?.title ?: "")
    }
    var priority by remember(task) {
        mutableStateOf(task?.priority ?: Priority.NORMAL)
    }
    var selectedLabelIds by remember(task) {
        mutableStateOf(task?.labels ?: emptyList())
    }
    var deadlineDate by remember(task, calendarEvent) {
        mutableStateOf(task?.deadlineDate ?: calendarEvent?.startDate ?: "")
    }
    var deadlineTime by remember(task, calendarEvent) {
        mutableStateOf(task?.deadlineTime ?: calendarEvent?.startTime ?: "")
    }
    var isRecurring by remember(task, calendarEvent) {
        mutableStateOf(task?.isRecurring ?: false)
    }
    var recurType by remember(task, calendarEvent) {
        mutableStateOf(task?.recurType ?: RecurType.DAYS)
    }
    var recurValue by remember(task, calendarEvent) {
        mutableStateOf(task?.recurValue?.toString() ?: "1")
    }
    var folderId by remember(task) {
        mutableStateOf(task?.folderId ?: initialFolderId)
    }

    // UI sub-states
    var showDeadlinePicker    by remember { mutableStateOf(false) }
    var showTimePicker        by remember { mutableStateOf(false) }
    var showEndTimePicker     by remember { mutableStateOf(false) }
    var showEndsDatePicker    by remember { mutableStateOf(false) }
    var showFolderPicker      by remember { mutableStateOf(false) }
    var showLabelPicker       by remember { mutableStateOf(false) }
    var showEditSeriesDialog  by remember { mutableStateOf(false) }
    var titleError            by remember { mutableStateOf(false) }
    var startDateError        by remember { mutableStateOf(false) }
    var endTimeError          by remember { mutableStateOf(false) }
    var calendarError         by remember { mutableStateOf(false) }

    // Force Monday as first day of week in DatePicker for locales that default to Sunday
    val currentConfig = LocalConfiguration.current
    val mondayLocale = remember(currentConfig.locales) {
        val loc = currentConfig.locales.get(0)
        if (WeekFields.of(loc).firstDayOfWeek == DayOfWeek.SUNDAY) Locale("en", "GB") else loc
    }
    val mondayConfig = remember(mondayLocale) {
        Configuration(currentConfig).apply { setLocale(mondayLocale) }
    }

    val titleFocus = remember { FocusRequester() }
    // Mutable vars updated asynchronously when the base event is fetched for recurring edits
    var baseEventLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!scheduleOnly) titleFocus.requestFocus()
        // Reset event-specific ViewModel state for each new form opening
        viewModel.crsByDay         = emptySet()
        viewModel.crsMonthlyIdx    = 0
        viewModel.crsEnds          = EndsType.NEVER
        viewModel.crsEndDate       = null
        viewModel.crsAfterCountStr = "13"
        if (isEditingEvent) {
            viewModel.formMode           = FormMode.EVENT
            viewModel.endTime            = calendarEvent!!.endTime
            viewModel.selectedCalendarId = calendarEvent.calendarId
            viewModel.loadCalendars()
            // For recurring events, fetch the series base event to read the RRULE.
            // We do NOT override date/time/title — the form shows the specific instance
            // the user tapped on. Only recurrence state is applied from the base event.
            if (calendarEvent.isRecurring) {
                viewModel.loadBaseEvent(
                    calendarId = calendarEvent.calendarId,
                    seriesId   = calendarEvent.recurringEventId,
                ) { _, _, _, _, parsed ->
                    if (parsed != null) {
                        isRecurring  = true
                        recurType    = when (parsed.freq) {
                            RRuleFreq.DAILY   -> RecurType.DAYS
                            RRuleFreq.WEEKLY  -> RecurType.WEEKS
                            RRuleFreq.MONTHLY -> RecurType.MONTHS
                            RRuleFreq.YEARLY  -> RecurType.YEARS
                        }
                        recurValue           = parsed.interval.toString()
                        viewModel.crsByDay   = parsed.byDay
                        viewModel.crsEnds    = parsed.ends
                        viewModel.crsEndDate = parsed.endDate
                        viewModel.crsAfterCountStr = parsed.afterCount.toString()
                        // Monthly: compute options from the instance's own date
                        if (parsed.freq == RRuleFreq.MONTHLY && deadlineDate.isNotBlank()) {
                            runCatching {
                                val opts = monthlyOptions(java.time.LocalDate.parse(deadlineDate))
                                val byDayStr = parsed.byDay.firstOrNull()
                                    ?.let { DOW_CODES[it] }
                                    ?.let { code -> opts.indexOfFirst { o -> o.byDayStr.endsWith(code) } }
                                    ?: -1
                                viewModel.crsMonthlyIdx = if (byDayStr >= 0) byDayStr else 0
                            }
                        }
                    }
                    baseEventLoaded = true
                }
            }
        } else {
            viewModel.endTime = ""
            // Always open in TASK mode unless explicitly editing a calendar event.
            // Resets mode for both new creates and task edits, so EVENT mode never
            // carries over from a previous form session.
            viewModel.formMode = FormMode.TASK
        }
    }

    val baseEventLoading by remember { derivedStateOf { viewModel.baseEventLoading } }

    // Calendars for EVENT mode
    val calendars       by viewModel.selectedCalendars.collectAsStateWithLifecycle()
    val calendarsLoading by viewModel.calendarsLoading.collectAsStateWithLifecycle()

    // ── Helpers ───────────────────────────────────────────────────────────

    fun submitTask() {
        val trimmed = title.trim()
        if (trimmed.isBlank()) { titleError = true; return }
        val parsed = parseSmartTitle(trimmed, folders, labels, folderId, selectedLabelIds)
        onConfirm(
            TaskFormResult(
                title         = parsed.title,
                folderId      = parsed.folderId,
                parentId      = task?.parentId ?: initialParentId,
                priority      = parsed.priority ?: priority,
                labelIds      = parsed.labelIds,
                deadlineDate  = deadlineDate,
                deadlineTime  = deadlineTime,
                isRecurring   = isRecurring,
                recurType     = recurType,
                recurValue    = recurValue.toIntOrNull() ?: 1,
            )
        )
    }

    fun submitEvent() {
        val trimmed = title.trim()
        if (!scheduleOnly && trimmed.isBlank()) { titleError = true; return }
        if (deadlineDate.isBlank()) { startDateError = true; return }
        // endTime is optional: buildEndDateTime defaults to start+1h when blank
        if (!scheduleOnly && calendars.isEmpty() && !isEditingEvent) {
            calendarError = true; return
        }

        // Build RRULE from the inline repeat settings
        val rrule: String? = if (isRecurring) {
            val freq = when (recurType) {
                RecurType.DAYS   -> RRuleFreq.DAILY
                RecurType.WEEKS  -> RRuleFreq.WEEKLY
                RecurType.MONTHS -> RRuleFreq.MONTHLY
                RecurType.YEARS  -> RRuleFreq.YEARLY
                else             -> RRuleFreq.DAILY
            }
            val interval = recurValue.toIntOrNull() ?: 1
            val monthlyOpt: MonthlyOption? = if (recurType == RecurType.MONTHS) {
                runCatching {
                    monthlyOptions(LocalDate.parse(deadlineDate)).getOrNull(viewModel.crsMonthlyIdx)
                }.getOrNull()
            } else null
            buildRRule(
                frequency     = freq,
                interval      = interval,
                byDay         = if (recurType == RecurType.WEEKS) viewModel.crsByDay else emptySet(),
                monthlyOption = monthlyOpt,
                ends          = viewModel.crsEnds,
                endDate       = viewModel.crsEndDate,
                afterCount    = viewModel.crsAfterCountStr.toIntOrNull()?.coerceIn(1, 999) ?: 13,
            )
        } else null

        if (isEditingEvent) {
            // For recurring events, patch the series base event (recurringEventId), not the instance
            val targetId = if (calendarEvent!!.isRecurring) calendarEvent.recurringEventId
                           else calendarEvent.id
            viewModel.updateEvent(
                calendarId         = viewModel.selectedCalendarId,
                eventId            = targetId,
                title              = trimmed,
                startDate          = deadlineDate,
                startTime          = deadlineTime,
                endTime            = viewModel.endTime,
                rrule              = rrule,
                originalCalendarId = calendarEvent.calendarId,
            )
        } else {
            viewModel.createEvent(
                calendarId = viewModel.selectedCalendarId,
                title      = trimmed,
                startDate  = deadlineDate,
                startTime  = deadlineTime,
                endTime    = viewModel.endTime,
                rrule      = rrule,
            )
        }
    }

    /**
     * Submits an update for the specific recurring event INSTANCE only
     * (uses [calendarEvent.id], not the series base ID, and strips the RRULE).
     * Called when the user chooses "Edit this event only" in the series dialog.
     */
    fun submitEventForInstance() {
        val trimmed = if (scheduleOnly) (calendarEvent?.title ?: "") else title.trim()
        if (!scheduleOnly && trimmed.isBlank()) { titleError = true; return }
        if (deadlineDate.isBlank()) { startDateError = true; return }
        // endTime is optional: buildEndDateTime defaults to start+1h when blank
        viewModel.updateEvent(
            calendarId         = viewModel.selectedCalendarId,
            eventId            = calendarEvent!!.id,   // instance ID, not recurringEventId
            title              = trimmed,
            startDate          = deadlineDate,
            startTime          = deadlineTime,
            endTime            = viewModel.endTime,
            rrule              = null,                  // no recurrence for a single instance
            originalCalendarId = calendarEvent.calendarId,
        )
    }

    // ── Sheet ─────────────────────────────────────────────────────────────

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            // ── Title (hidden in schedule-only mode) ──────────────────────
            if (!scheduleOnly) {
                OutlinedTextField(
                    value         = title,
                    onValueChange = { title = it; titleError = false },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocus),
                    placeholder   = {
                        Text(if (viewModel.formMode == FormMode.TASK) "Task name" else "Event name")
                    },
                    isError       = titleError,
                    supportingText = if (titleError) ({ Text("Title is required") }) else null,
                    singleLine    = false,
                    maxLines      = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (viewModel.formMode == FormMode.TASK) submitTask() else submitEvent()
                    }),
                )
            }

            // Loading indicator while fetching recurring series base event
            if (baseEventLoading) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            // ── Mode toggle (hidden when editing an existing task or event) ──
            if (!isEditing && !isEditingEvent) SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = viewModel.formMode == FormMode.TASK,
                    onClick  = { viewModel.formMode = FormMode.TASK },
                    shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label    = { Text("Task") },
                    icon     = {
                        Icon(Icons.Outlined.CheckBox, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                    },
                )
                SegmentedButton(
                    selected = viewModel.formMode == FormMode.EVENT,
                    onClick  = {
                        viewModel.formMode = FormMode.EVENT
                        viewModel.loadCalendars()
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Event") },
                    icon  = {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                    },
                )
            }

            // ── Deadline ──────────────────────────────────────────────────
            SectionLabel("Deadline")
            val dlStatus = deadlineStatus(deadlineDate)

            if (viewModel.formMode == FormMode.EVENT) {
                // EVENT mode: [ Date ] [ Start time ] — [ End time ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Date chip (required; error state if missing)
                    FilterChip(
                        selected = startDateError && deadlineDate.isBlank(),
                        onClick  = { showDeadlinePicker = true; startDateError = false },
                        label    = {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(13.dp),
                                    tint = if (deadlineDate.isNotBlank()) deadlineColor(dlStatus)
                                           else if (startDateError) MaterialTheme.colorScheme.error
                                           else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text  = if (deadlineDate.isBlank()) "Date *"
                                            else formatExplicitDate(deadlineDate),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (deadlineDate.isNotBlank()) deadlineColor(dlStatus)
                                            else if (startDateError) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    // Start time chip (always visible in EVENT mode)
                    FilterChip(
                        selected = false,
                        onClick  = { showTimePicker = true },
                        label    = {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.Schedule, null, Modifier.size(13.dp),
                                    tint = if (deadlineTime.isNotBlank()) deadlineColor(dlStatus)
                                           else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text  = if (deadlineTime.isBlank()) "HH:MM" else deadlineTime,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (deadlineTime.isNotBlank()) deadlineColor(dlStatus)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    // Dash separator
                    Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    // End time chip (required when start time is set)
                    val endTimeHasError = endTimeError && viewModel.endTime.isBlank()
                    // Show * proactively once start time is set, so user knows end time is required
                    val endTimeRequired = deadlineTime.isNotBlank() && viewModel.endTime.isBlank()
                    FilterChip(
                        selected = false,
                        onClick  = { showEndTimePicker = true; endTimeError = false },
                        label    = {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.Schedule, null, Modifier.size(13.dp),
                                    tint = when {
                                        viewModel.endTime.isNotBlank() -> deadlineColor(dlStatus)
                                        endTimeHasError                -> MaterialTheme.colorScheme.error
                                        else                           -> MaterialTheme.colorScheme.onSurfaceVariant
                                    })
                                Text(
                                    text  = when {
                                        viewModel.endTime.isNotBlank() -> viewModel.endTime
                                        endTimeHasError                -> "End time *"
                                        endTimeRequired                -> "HH:MM*"
                                        else                           -> "HH:MM"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        viewModel.endTime.isNotBlank() -> deadlineColor(dlStatus)
                                        endTimeHasError                -> MaterialTheme.colorScheme.error
                                        else                           -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        },
                    )
                }
            } else {
                // TASK mode: [ Date ] [ Time ] (time only visible when date set)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = false,
                        onClick  = { showDeadlinePicker = true },
                        label    = {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(13.dp),
                                    tint = if (deadlineDate.isNotBlank()) deadlineColor(dlStatus)
                                           else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text  = if (deadlineDate.isBlank()) "No date"
                                            else formatExplicitDate(deadlineDate),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (deadlineDate.isNotBlank()) deadlineColor(dlStatus)
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    if (deadlineDate.isNotBlank()) {
                        FilterChip(
                            selected = false,
                            onClick  = { showTimePicker = true },
                            label    = {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Outlined.Schedule, null, Modifier.size(13.dp),
                                        tint = if (deadlineTime.isNotBlank()) deadlineColor(dlStatus)
                                               else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text  = if (deadlineTime.isBlank()) "HH:MM" else deadlineTime,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (deadlineTime.isNotBlank()) deadlineColor(dlStatus)
                                               else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }

            // ── Repeat ────────────────────────────────────────────────────
            val showRepeat = deadlineDate.isNotBlank() || viewModel.formMode == FormMode.EVENT
            if (showRepeat) {
                RepeatRow(
                    isChecked     = isRecurring,
                    onToggle      = { isRecurring = it },
                    recurValue    = recurValue,
                    onValueChange = { recurValue = it },
                    recurType     = recurType,
                    onTypeChange  = { recurType = it },
                )
            }

            // ── EVENT-mode inline recurrence: Repeat on + Ends ───────────
            if (viewModel.formMode == FormMode.EVENT && isRecurring) {

                // Seed crsByDay from the deadline date the first time WEEKLY is active
                LaunchedEffect(recurType) {
                    if (recurType == RecurType.WEEKS && viewModel.crsByDay.isEmpty()) {
                        runCatching { LocalDate.parse(deadlineDate) }.getOrNull()?.let {
                            viewModel.crsByDay = setOf(it.dayOfWeek)
                        }
                    }
                }

                // "Repeat on" — day circles (WEEKLY only)
                if (recurType == RecurType.WEEKS) {
                    SectionLabel("Repeat on")
                    val weekDays = listOf(
                        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
                    )
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        weekDays.forEach { dow ->
                            val selected = dow in viewModel.crsByDay
                            val dayLabel = dow.getDisplayName(
                                java.time.format.TextStyle.NARROW, Locale.getDefault()
                            ).replaceFirstChar { it.uppercase() }
                            FilledTonalButton(
                                onClick = {
                                    viewModel.crsByDay =
                                        if (selected) viewModel.crsByDay - dow
                                        else          viewModel.crsByDay + dow
                                },
                                modifier       = Modifier.size(36.dp),
                                shape          = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                colors         = if (selected) ButtonDefaults.filledTonalButtonColors()
                                                 else          ButtonDefaults.textButtonColors(),
                            ) {
                                Text(dayLabel, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // "Repeat by" — monthly options dropdown (MONTHLY only)
                if (recurType == RecurType.MONTHS) {
                    val parsedDeadline = remember(deadlineDate) {
                        runCatching { LocalDate.parse(deadlineDate) }.getOrNull()
                    }
                    val monthlyOpts = remember(parsedDeadline) {
                        if (parsedDeadline != null) monthlyOptions(parsedDeadline) else emptyList()
                    }
                    LaunchedEffect(parsedDeadline) {
                        if (monthlyOpts.isNotEmpty() && viewModel.crsMonthlyIdx >= monthlyOpts.size) {
                            viewModel.crsMonthlyIdx = 0
                        }
                    }
                    if (monthlyOpts.isNotEmpty()) {
                        SectionLabel("Repeat by")
                        var monthlyExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded         = monthlyExpanded,
                            onExpandedChange = { monthlyExpanded = it },
                        ) {
                            // Same compact Row trigger as the frequency dropdown
                            Row(
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    monthlyOpts.getOrNull(viewModel.crsMonthlyIdx)?.label ?: "",
                                    style    = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthlyExpanded)
                            }
                            ExposedDropdownMenu(
                                expanded        = monthlyExpanded,
                                onDismissRequest = { monthlyExpanded = false },
                            ) {
                                monthlyOpts.forEachIndexed { i, opt ->
                                    DropdownMenuItem(
                                        text    = { Text(opt.label) },
                                        onClick = { viewModel.crsMonthlyIdx = i; monthlyExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                }

                // "Ends" — shown for all recurring types in EVENT mode
                SectionLabel("Ends")
                // Tight column — no extra spacing between radio options
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = viewModel.crsEnds == EndsType.NEVER,
                            onClick  = { viewModel.crsEnds = EndsType.NEVER },
                        )
                        Text(
                            "Never",
                            style    = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { viewModel.crsEnds = EndsType.NEVER },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = viewModel.crsEnds == EndsType.ON_DATE,
                            onClick  = { viewModel.crsEnds = EndsType.ON_DATE },
                        )
                        Text(
                            "On",
                            style    = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .width(48.dp)
                                .clickable { viewModel.crsEnds = EndsType.ON_DATE },
                        )
                        if (viewModel.crsEnds == EndsType.ON_DATE) {
                            TextButton(onClick = { showEndsDatePicker = true }) {
                                Text(
                                    viewModel.crsEndDate
                                        ?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
                                        ?: "Pick date"
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = viewModel.crsEnds == EndsType.AFTER_COUNT,
                            onClick  = { viewModel.crsEnds = EndsType.AFTER_COUNT },
                        )
                        Text(
                            "After",
                            style    = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .width(48.dp)
                                .clickable { viewModel.crsEnds = EndsType.AFTER_COUNT },
                        )
                        if (viewModel.crsEnds == EndsType.AFTER_COUNT) {
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicTextField(
                                    value         = viewModel.crsAfterCountStr,
                                    onValueChange = { v ->
                                        if (v.isEmpty() || (v.all { it.isDigit() } && v.length <= 3))
                                            viewModel.crsAfterCountStr = v
                                    },
                                    singleLine      = true,
                                    textStyle       = MaterialTheme.typography.bodyMedium.copy(
                                        textAlign = TextAlign.Center,
                                        color     = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    cursorBrush     = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("occurrences", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // ── TASK-mode fields ──────────────────────────────────────────
            if (viewModel.formMode == FormMode.TASK) {

                // Folder
                SectionLabel("Folder")
                val folderName  = folders.find { it.id == folderId }?.name ?: "Inbox"
                val folderColor = folders.find { it.id == folderId }?.color
                val fColor      = folderColor?.toComposeColor()
                                  ?: MaterialTheme.colorScheme.primary
                FilterChip(
                    selected    = true,
                    onClick     = { showFolderPicker = true },
                    label       = {
                        Text(folderName, style = MaterialTheme.typography.bodyMedium, color = fColor)
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Folder, null, Modifier.size(13.dp), tint = fColor)
                    },
                    colors      = FilterChipDefaults.filterChipColors(
                        selectedContainerColor   = fColor.copy(alpha = 0.18f),
                        selectedLabelColor       = fColor,
                        selectedLeadingIconColor = fColor,
                    ),
                )

                // Labels — trigger row (styled like SectionLabel + chevron)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showLabelPicker = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Labels",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    if (selectedLabelIds.isNotEmpty()) {
                        Text(
                            "${selectedLabelIds.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                    Icon(
                        Icons.Outlined.ChevronRight, null,
                        modifier = Modifier.size(14.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Selected-label chips (quick-remove via ×)
                if (selectedLabelIds.isNotEmpty()) {
                    val selectedLabels = selectedLabelIds.mapNotNull { id ->
                        labels.find { it.id == id }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedLabels.forEach { lbl ->
                            val lColor = lbl.color.toComposeColor()
                            FilterChip(
                                selected    = true,
                                onClick     = { selectedLabelIds = selectedLabelIds - lbl.id },
                                label       = {
                                    Text(lbl.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = lColor)
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Label, null,
                                        modifier = Modifier.size(13.dp), tint = lColor)
                                },
                                trailingIcon = {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove",
                                        modifier = Modifier.size(12.dp))
                                },
                                colors      = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor   = lColor.copy(alpha = 0.18f),
                                    selectedLabelColor       = lColor,
                                    selectedLeadingIconColor = lColor,
                                ),
                            )
                        }
                    }
                }

                // Priority
                SectionLabel("Priority")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        Priority.URGENT    to "Urgent",
                        Priority.IMPORTANT to "Important",
                        Priority.NORMAL    to "Normal",
                    ).forEach { (p, label) ->
                        val sel    = priority == p
                        val pColor = priorityColor(p)
                        FilterChip(
                            selected    = sel,
                            onClick     = { priority = p },
                            label       = {
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            },
                            leadingIcon = {
                                Icon(
                                    if (sel) Icons.Outlined.Check else Icons.Outlined.Flag,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint     = pColor,
                                )
                            },
                            colors      = FilterChipDefaults.filterChipColors(
                                selectedContainerColor   = pColor.copy(alpha = 0.18f),
                                selectedLabelColor       = pColor,
                                selectedLeadingIconColor = pColor,
                            ),
                        )
                    }
                }
            }

            // ── EVENT-mode fields ─────────────────────────────────────────
            if (viewModel.formMode == FormMode.EVENT) {

                // Calendar picker — hidden only in schedule-only mode
                // When editing an existing event, the calendar can be changed (uses the move API)
                if (!scheduleOnly) SectionLabel("Calendar")
                if (!scheduleOnly) {
                    when {
                        calendarsLoading -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                        calendars.isEmpty() -> {
                            Text(
                                text  = "No calendars selected. Go to Settings → Calendars.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (calendarError) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            var calExpanded by remember { mutableStateOf(false) }
                            val selectedCal = calendars.find { it.id == viewModel.selectedCalendarId }
                                ?: calendars.first()
                            ExposedDropdownMenuBox(
                                expanded        = calExpanded,
                                onExpandedChange = { calExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value         = selectedCal.summary,
                                    onValueChange = {},
                                    readOnly      = true,
                                    modifier      = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    leadingIcon   = {
                                        Icon(
                                            Icons.Outlined.CalendarMonth,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint     = selectedCal.color.toComposeColor(),
                                        )
                                    },
                                    trailingIcon  = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(calExpanded)
                                    },
                                    isError       = calendarError,
                                )
                                ExposedDropdownMenu(
                                    expanded        = calExpanded,
                                    onDismissRequest = { calExpanded = false },
                                ) {
                                    calendars.forEach { cal ->
                                        DropdownMenuItem(
                                            text    = { Text(cal.summary) },
                                            onClick = {
                                                viewModel.selectedCalendarId = cal.id
                                                calendarError = false
                                                calExpanded   = false
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Outlined.CalendarMonth,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint     = cal.color.toComposeColor(),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Buttons ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: Clear / Postpone — only in TASK edit mode
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (viewModel.formMode == FormMode.TASK) {
                        if (isEditing && (deadlineDate.isNotBlank() || deadlineTime.isNotBlank())) {
                            TextButton(onClick = {
                                deadlineDate = ""
                                deadlineTime = ""
                                isRecurring  = false
                                recurType    = RecurType.DAYS
                                recurValue   = "1"
                            }) { Text("Clear") }
                        }
                        if (isEditing && isRecurring && deadlineDate.isNotBlank()) {
                            TextButton(onClick = {
                                runCatching {
                                    val n    = recurValue.toIntOrNull() ?: 1
                                    val base = LocalDate.parse(deadlineDate)
                                    deadlineDate = when (recurType) {
                                        RecurType.WEEKS  -> base.plusWeeks(n.toLong())
                                        RecurType.MONTHS -> base.plusMonths(n.toLong())
                                        RecurType.YEARS  -> base.plusYears(n.toLong())
                                        else             -> base.plusDays(n.toLong())
                                    }.toString()
                                }
                            }) { Text("Postpone") }
                        }
                    }
                }
                // Right: Cancel + Save/Create
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        when {
                            viewModel.formMode == FormMode.TASK -> submitTask()
                            // For recurring event edits, show the "this event / all events" dialog
                            isEditingEvent && calendarEvent!!.isRecurring ->
                                showEditSeriesDialog = true
                            else -> submitEvent()
                        }
                    }) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isEditing || isEditingEvent) "Save" else "Create")
                    }
                }
            }
        }
    }

    // ── Edit recurring event dialog ───────────────────────────────────────
    if (showEditSeriesDialog) {
        AlertDialog(
            onDismissRequest = { showEditSeriesDialog = false },
            title = { Text("Edit recurring event") },
            text  = { Text("\"${title.trim().ifBlank { calendarEvent?.title ?: "" }}\"") },
            confirmButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = {
                        showEditSeriesDialog = false
                        submitEventForInstance()
                    }) { Text("Edit this event only") }
                    TextButton(onClick = {
                        showEditSeriesDialog = false
                        submitEvent()
                    }) { Text("Edit all events in series") }
                    TextButton(onClick = { showEditSeriesDialog = false }) { Text("Cancel") }
                }
            },
            dismissButton = null,
        )
    }

    // ── Sub-dialogs ────────────────────────────────────────────────────────

    // Date picker
    if (showDeadlinePicker) {
        val initialMillis = remember(deadlineDate) {
            if (deadlineDate.isBlank()) null
            else runCatching {
                LocalDate.parse(deadlineDate)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )
        CompositionLocalProvider(LocalConfiguration provides mondayConfig) {
            DatePickerDialog(
                onDismissRequest = { showDeadlinePicker = false },
                confirmButton    = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { ms ->
                            deadlineDate = LocalDate
                                .ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC)
                                .format(DateTimeFormatter.ISO_LOCAL_DATE)
                        }
                        showDeadlinePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            deadlineDate = ""
                            deadlineTime = ""
                            showDeadlinePicker = false
                        }) { Text("Clear") }
                        TextButton(onClick = { showDeadlinePicker = false }) { Text("Cancel") }
                    }
                },
            ) { DatePicker(state = datePickerState) }
        }
    }

    // Start time picker
    if (showTimePicker) {
        val (initH, initM) = remember(deadlineTime) {
            if (deadlineTime.isBlank()) 9 to 0
            else runCatching {
                val p = deadlineTime.split(":"); p[0].toInt() to p[1].toInt()
            }.getOrDefault(9 to 0)
        }
        val timePickerState = rememberTimePickerState(
            initialHour   = initH,
            initialMinute = initM,
            is24Hour      = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title            = { Text("Set time") },
            text             = { TimePicker(state = timePickerState) },
            confirmButton    = {
                TextButton(onClick = {
                    val newTime = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                    deadlineTime = newTime
                    // In EVENT mode: if end time is no longer after the new start time,
                    // clear it so buildEndDateTime will default to start+1h.
                    if (viewModel.formMode == FormMode.EVENT && viewModel.endTime.isNotBlank()) {
                        runCatching {
                            val start = java.time.LocalTime.parse(newTime)
                            val end   = java.time.LocalTime.parse(viewModel.endTime)
                            if (!end.isAfter(start)) viewModel.endTime = ""
                        }
                    }
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        deadlineTime = ""
                        showTimePicker = false
                    }) { Text("Clear") }
                    TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                }
            },
        )
    }

    // End time picker (EVENT mode)
    if (showEndTimePicker) {
        EndTimePickerDialog(
            initialTime = viewModel.endTime,
            onConfirm   = { viewModel.endTime = it; endTimeError = false; showEndTimePicker = false },
            onClear     = { viewModel.endTime = ""; showEndTimePicker = false },
            onDismiss   = { showEndTimePicker = false },
        )
    }

    // Folder picker (TASK mode)
    if (showFolderPicker) {
        FolderPickerDialog(
            folders   = folders,
            currentId = folderId,
            onSelect  = { id -> folderId = id; showFolderPicker = false },
            onDismiss = { showFolderPicker = false },
        )
    }

    // Label picker (TASK mode)
    if (showLabelPicker) {
        LabelPickerSheet(
            allLabels   = labels,
            selectedIds = selectedLabelIds,
            onConfirm   = { selectedLabelIds = it; showLabelPicker = false },
            onDismiss   = { showLabelPicker = false },
        )
    }

    // Ends "On date" picker (EVENT mode)
    if (showEndsDatePicker) {
        val initialMillis = remember(viewModel.crsEndDate) {
            viewModel.crsEndDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        }
        val endsDateState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        CompositionLocalProvider(LocalConfiguration provides mondayConfig) {
            DatePickerDialog(
                onDismissRequest = { showEndsDatePicker = false },
                confirmButton    = {
                    TextButton(onClick = {
                        endsDateState.selectedDateMillis?.let { ms ->
                            viewModel.crsEndDate =
                                LocalDate.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC)
                        }
                        showEndsDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showEndsDatePicker = false }) { Text("Cancel") }
                },
            ) { DatePicker(state = endsDateState) }
        }
    }
}

// ── Result ─────────────────────────────────────────────────────────────────────

data class TaskFormResult(
    val title       : String,
    val folderId    : String,
    val parentId    : String,
    val priority    : Priority,
    val labelIds    : List<String>,
    val deadlineDate: String,
    val deadlineTime: String,
    val isRecurring : Boolean,
    val recurType   : RecurType,
    val recurValue  : Int,
)

// ── Helpers ────────────────────────────────────────────────────────────────────

/** Formats a "YYYY-MM-DD" string as "d MMM" (e.g. "18 Apr"). */
private fun formatExplicitDate(dateStr: String): String =
    runCatching {
        LocalDate.parse(dateStr)
            .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }.getOrDefault(dateStr)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Inline repeat row: [☐] Repeat  /  [☑] Repeat every [3] [day ▾]
 *
 * Works for both TASK and EVENT modes.
 * The dropdown offers day / week / month / year.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RepeatRow(
    isChecked    : Boolean,
    onToggle     : (Boolean) -> Unit,
    recurValue   : String,
    onValueChange: (String) -> Unit,
    recurType    : RecurType,
    onTypeChange : (RecurType) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier.fillMaxWidth(),
    ) {
        Checkbox(checked = isChecked, onCheckedChange = onToggle)
        Text(
            text     = if (isChecked) "Repeat every" else "Repeat",
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable { onToggle(!isChecked) },
        )
        if (isChecked) {
            Spacer(Modifier.width(8.dp))
            // ── Interval number input ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value         = recurValue,
                    onValueChange = { v ->
                        if (v.isEmpty() || (v.all { it.isDigit() } && v.length <= 3)) onValueChange(v)
                    },
                    singleLine      = true,
                    textStyle       = MaterialTheme.typography.bodyMedium.copy(
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush     = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Spacer(Modifier.width(6.dp))
            // ── Frequency dropdown (day / week / month / year) ────────────
            var freqExpanded by remember { mutableStateOf(false) }
            val freqLabel = when (recurType) {
                RecurType.DAYS   -> "day"
                RecurType.WEEKS  -> "week"
                RecurType.MONTHS -> "month"
                RecurType.YEARS  -> "year"
                else             -> "day"
            }
            ExposedDropdownMenuBox(
                expanded         = freqExpanded,
                onExpandedChange = { freqExpanded = it },
            ) {
                Row(
                    modifier = Modifier
                        .menuAnchor()
                        .widthIn(min = 110.dp)   // wide enough so "month" never wraps
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(freqLabel, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded)
                }
                ExposedDropdownMenu(
                    expanded        = freqExpanded,
                    onDismissRequest = { freqExpanded = false },
                ) {
                    listOf(
                        RecurType.DAYS   to "day",
                        RecurType.WEEKS  to "week",
                        RecurType.MONTHS to "month",
                        RecurType.YEARS  to "year",
                    ).forEach { (rt, lbl) ->
                        DropdownMenuItem(
                            text    = { Text(lbl, style = MaterialTheme.typography.bodyMedium) },
                            onClick = { onTypeChange(rt); freqExpanded = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderPickerDialog(
    folders  : List<Folder>,
    currentId: String,
    onSelect : (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select folder") },
        text  = {
            Column {
                FolderPickerItem(
                    name     = "Inbox",
                    color    = null,
                    selected = currentId == "fld-inbox",
                    onClick  = { onSelect("fld-inbox") },
                )
                folders.filter { !it.isInbox }.forEach { f ->
                    FolderPickerItem(
                        name     = f.name,
                        color    = f.color,
                        selected = currentId == f.id,
                        onClick  = { onSelect(f.id) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun FolderPickerItem(
    name    : String,
    color   : String?,
    selected: Boolean,
    onClick : () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint     = color?.toComposeColor() ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            name,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color    = color?.toComposeColor() ?: MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint     = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ── Smart title parsing ────────────────────────────────────────────────────────

private data class ParsedTitle(
    val title   : String,
    val folderId: String,
    val labelIds: List<String>,
    val priority: Priority? = null,
)

private fun parseSmartTitle(
    raw          : String,
    folders      : List<Folder>,
    labels       : List<Label>,
    baseFolderId : String,
    baseLabelIds : List<String>,
): ParsedTitle {
    var title    = raw
    var folderId = baseFolderId
    val labelIds = baseLabelIds.toMutableList()
    var parsedPriority: Priority? = null

    title = title.replace(Regex("@(\\S+)")) { match ->
        val name  = match.groupValues[1]
        val found = folders.find { it.name.equals(name, ignoreCase = true) }
        if (found != null) { folderId = found.id; "" } else match.value
    }
    title = title.replace(Regex("#(\\S+)")) { match ->
        val name  = match.groupValues[1]
        val found = labels.find { it.name.equals(name, ignoreCase = true) }
        if (found != null && found.id !in labelIds) { labelIds.add(found.id); "" } else match.value
    }
    title = title.replace(Regex("!(\\d)")) { match ->
        val p = when (match.groupValues[1]) {
            "1"  -> Priority.URGENT
            "2"  -> Priority.IMPORTANT
            "3"  -> Priority.NORMAL
            else -> null
        }
        if (p != null) { parsedPriority = p; "" } else match.value
    }

    return ParsedTitle(
        title    = title.replace(Regex("\\s{2,}"), " ").trim(),
        folderId = folderId,
        labelIds = labelIds,
        priority = parsedPriority,
    )
}

// ── Constants ──────────────────────────────────────────────────────────────────

/**
 * Color presets for label and folder creation dialogs.
 * Defined here (internal) so FolderLabelDialogs.kt can import it.
 */
internal val COLOR_PRESETS = listOf(
    "#ef4444", "#f97316", "#eab308", "#22c55e",
    "#06b6d4", "#3b82f6", "#8b5cf6", "#6b7280",
)
