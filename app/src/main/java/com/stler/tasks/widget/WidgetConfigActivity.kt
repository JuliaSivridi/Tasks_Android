package com.stler.tasks.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.stler.tasks.data.repository.CalendarRepository
import com.stler.tasks.data.repository.TaskRepository
import com.stler.tasks.domain.model.CalendarItem
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.ui.theme.TasksTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class WidgetType { UPCOMING, FOLDER, TASK_LIST, CALENDAR }

@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var calendarRepository: CalendarRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(RESULT_CANCELED)

        val providerClassName = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId)
            ?.provider
            ?.className
            ?: ""

        val widgetType = when {
            providerClassName.contains("FolderWidget")    -> WidgetType.FOLDER
            providerClassName.contains("TaskList")        -> WidgetType.TASK_LIST
            providerClassName.contains("CalendarWidget")  -> WidgetType.CALENDAR
            else                                          -> WidgetType.UPCOMING
        }

        // UpcomingWidget needs no config — mark configured and show immediately
        if (widgetType == WidgetType.UPCOMING) {
            lifecycleScope.launch {
                WidgetPrefs.setConfigured(this@WidgetConfigActivity, appWidgetId)
                val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity)
                    .getGlanceIdBy(appWidgetId)
                UpcomingWidget().update(this@WidgetConfigActivity, glanceId)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                finish()
            }
            return
        }

        setContent {
            TasksTheme {
                val folders by taskRepository.observeFolders()
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val labels by taskRepository.observeLabels()
                    .collectAsStateWithLifecycle(initialValue = emptyList())

                WidgetConfigScreen(
                    widgetType         = widgetType,
                    folders            = folders,
                    labels             = labels,
                    calendarRepository = calendarRepository,
                    onConfirmFolder    = { folderId ->
                        lifecycleScope.launch {
                            WidgetPrefs.setFolderId(this@WidgetConfigActivity, appWidgetId, folderId)
                            WidgetPrefs.setConfigured(this@WidgetConfigActivity, appWidgetId)
                            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity)
                                .getGlanceIdBy(appWidgetId)
                            FolderWidget().update(this@WidgetConfigActivity, glanceId)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                            finish()
                        }
                    },
                    onConfirmTaskList  = { folderIds, labelIds, priorityIds ->
                        lifecycleScope.launch {
                            WidgetPrefs.setFilterFolders(this@WidgetConfigActivity, appWidgetId, folderIds)
                            WidgetPrefs.setFilterLabels(this@WidgetConfigActivity, appWidgetId, labelIds)
                            WidgetPrefs.setFilterPriorities(this@WidgetConfigActivity, appWidgetId, priorityIds)
                            WidgetPrefs.setConfigured(this@WidgetConfigActivity, appWidgetId)
                            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity)
                                .getGlanceIdBy(appWidgetId)
                            TaskListWidget().update(this@WidgetConfigActivity, glanceId)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                            finish()
                        }
                    },
                    onConfirmCalendar  = { selectedIds, displayName ->
                        lifecycleScope.launch {
                            WidgetPrefs.setCalendarWidgetIds(this@WidgetConfigActivity, appWidgetId, selectedIds)
                            WidgetPrefs.setCalendarWidgetName(this@WidgetConfigActivity, appWidgetId, displayName)
                            WidgetPrefs.setConfigured(this@WidgetConfigActivity, appWidgetId)
                            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity)
                                .getGlanceIdBy(appWidgetId)
                            CalendarWidget().update(this@WidgetConfigActivity, glanceId)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                            finish()
                        }
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

// ── Screen dispatcher ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(
    widgetType        : WidgetType,
    folders           : List<Folder>,
    labels            : List<Label>,
    calendarRepository: CalendarRepository,
    onConfirmFolder   : (folderId: String) -> Unit,
    onConfirmTaskList : (folderIds: Set<String>, labelIds: Set<String>, priorityIds: Set<String>) -> Unit,
    onConfirmCalendar : (selectedIds: Set<String>, displayName: String) -> Unit,
    onCancel          : () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(text = when (widgetType) {
                    WidgetType.FOLDER    -> "Choose Folder"
                    WidgetType.TASK_LIST -> "Configure Filters"
                    WidgetType.CALENDAR  -> "Choose Calendars"
                    else                 -> "Widget Setup"
                })
            })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when (widgetType) {
                WidgetType.FOLDER    -> FolderSelectorContent(
                    folders   = folders,
                    onConfirm = onConfirmFolder,
                    onCancel  = onCancel,
                )
                WidgetType.TASK_LIST -> TaskListFilterContent(
                    folders   = folders,
                    labels    = labels,
                    onConfirm = onConfirmTaskList,
                    onCancel  = onCancel,
                )
                WidgetType.CALENDAR  -> CalendarSelectorContent(
                    calendarRepository = calendarRepository,
                    onConfirm          = onConfirmCalendar,
                    onCancel           = onCancel,
                )
                else -> Unit
            }
        }
    }
}

// ── Folder widget — single-select radio list ──────────────────────────────────

@Composable
private fun FolderSelectorContent(
    folders  : List<Folder>,
    onConfirm: (folderId: String) -> Unit,
    onCancel : () -> Unit,
) {
    var selectedFolderId by remember { mutableStateOf(folders.firstOrNull()?.id ?: "fld-inbox") }

    Text("Select a folder to show in the widget:", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(12.dp))

    folders.forEach { folder ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selectedFolderId == folder.id,
                onClick  = { selectedFolderId = folder.id },
            )
            Text(text = folder.name, modifier = Modifier.padding(start = 8.dp))
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    ActionButtons(onConfirm = { onConfirm(selectedFolderId) }, onCancel = onCancel)
}

// ── TaskList widget — multi-select filter UI ──────────────────────────────────

private val PRIORITY_OPTIONS = listOf(
    "urgent"    to "Urgent (!1)",
    "important" to "Important (!2)",
    "normal"    to "Normal (!3)",
)

@Composable
private fun TaskListFilterContent(
    folders  : List<Folder>,
    labels   : List<Label>,
    onConfirm: (folderIds: Set<String>, labelIds: Set<String>, priorityIds: Set<String>) -> Unit,
    onCancel : () -> Unit,
) {
    val selectedFolderIds   = remember { mutableStateListOf<String>() }
    val selectedLabelIds    = remember { mutableStateListOf<String>() }
    val selectedPriorityIds = remember { mutableStateListOf<String>() }

    Text(
        text  = "Choose filters (leave all unchecked to show everything):",
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(16.dp))

    // ── Folders ───────────────────────────────────────────────────────────────
    if (folders.isNotEmpty()) {
        FilterSectionHeader(
            title = "Folder",
            hint  = if (selectedFolderIds.isEmpty()) "Any" else "${selectedFolderIds.size} selected",
        )
        folders.forEach { folder ->
            val checked = folder.id in selectedFolderIds
            CheckboxRow(
                label   = "@${folder.name}",
                checked = checked,
                onToggle = {
                    if (checked) selectedFolderIds.remove(folder.id)
                    else         selectedFolderIds.add(folder.id)
                },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // ── Labels ────────────────────────────────────────────────────────────────
    if (labels.isNotEmpty()) {
        FilterSectionHeader(
            title = "Label",
            hint  = if (selectedLabelIds.isEmpty()) "Any" else "${selectedLabelIds.size} selected",
        )
        labels.forEach { label ->
            val checked = label.id in selectedLabelIds
            CheckboxRow(
                label   = "#${label.name}",
                checked = checked,
                onToggle = {
                    if (checked) selectedLabelIds.remove(label.id)
                    else         selectedLabelIds.add(label.id)
                },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // ── Priority ──────────────────────────────────────────────────────────────
    FilterSectionHeader(
        title = "Priority",
        hint  = if (selectedPriorityIds.isEmpty()) "Any" else "${selectedPriorityIds.size} selected",
    )
    PRIORITY_OPTIONS.forEach { (value, label) ->
        val checked = value in selectedPriorityIds
        CheckboxRow(
            label   = label,
            checked = checked,
            onToggle = {
                if (checked) selectedPriorityIds.remove(value)
                else         selectedPriorityIds.add(value)
            },
        )
    }

    Spacer(modifier = Modifier.height(24.dp))
    ActionButtons(
        onConfirm = {
            onConfirm(
                selectedFolderIds.toSet(),
                selectedLabelIds.toSet(),
                selectedPriorityIds.toSet(),
            )
        },
        onCancel = onCancel,
    )
}

@Composable
private fun FilterSectionHeader(title: String, hint: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text  = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            text  = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(
            text     = label,
            modifier = Modifier.padding(start = 8.dp),
            style    = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ── Calendar widget — multi-select checkbox list ──────────────────────────────

@Composable
private fun CalendarSelectorContent(
    calendarRepository: CalendarRepository,
    onConfirm         : (selectedIds: Set<String>, displayName: String) -> Unit,
    onCancel          : () -> Unit,
) {
    var calendars by remember { mutableStateOf<List<CalendarItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val selectedIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val fetched = calendarRepository.fetchCalendarsAndSave()
            calendars = fetched
            fetched.filter { it.isSelected }.forEach { cal ->
                if (cal.id !in selectedIds) selectedIds.add(cal.id)
            }
        } catch (_: Exception) { /* leave empty */ } finally {
            isLoading = false
        }
    }

    Text("Select calendars to show in the widget:", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(12.dp))

    when {
        isLoading -> Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }

        calendars.isEmpty() -> Text(
            text  = "No calendars found. Make sure you are signed in and calendar access is enabled.",
            style = MaterialTheme.typography.bodyMedium,
        )

        else -> calendars.forEach { cal ->
            val isChecked = cal.id in selectedIds
            CheckboxRow(
                label   = cal.summary,
                checked = isChecked,
                onToggle = {
                    if (isChecked) selectedIds.remove(cal.id)
                    else           selectedIds.add(cal.id)
                },
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
    ) {
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
        Button(onClick = {
            val ids = selectedIds.toSet()
            val name = when (ids.size) {
                1    -> calendars.find { it.id in ids }?.summary ?: "Calendar"
                0    -> "Calendar"
                else -> "Calendars"
            }
            onConfirm(ids, name)
        }) { Text("Add Widget") }
    }
}

// ── Shared ────────────────────────────────────────────────────────────────────

@Composable
private fun ActionButtons(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
    ) {
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
        Button(onClick = onConfirm) { Text("Add Widget") }
    }
}
