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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.stler.tasks.data.repository.TaskRepository
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.ui.theme.TasksTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class WidgetType { UPCOMING, FOLDER, TASK_LIST }

@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var taskRepository: TaskRepository

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

        // Default result is CANCELED in case user backs out
        setResult(RESULT_CANCELED)

        val providerClassName = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId)
            ?.provider
            ?.className
            ?: ""

        val widgetType = when {
            providerClassName.contains("FolderWidget") -> WidgetType.FOLDER
            providerClassName.contains("TaskList") -> WidgetType.TASK_LIST
            else -> WidgetType.UPCOMING
        }

        // For UpcomingWidget there's nothing to configure — just confirm immediately
        if (widgetType == WidgetType.UPCOMING) {
            lifecycleScope.launch {
                val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity)
                    .getGlanceIdBy(appWidgetId)
                UpcomingWidget().update(this@WidgetConfigActivity, glanceId)
                setResult(
                    RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                )
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
                    widgetType = widgetType,
                    folders = folders,
                    labels = labels,
                    onConfirm = { selectedFolder, selectedLabel, selectedPriority ->
                        lifecycleScope.launch {
                            // Save config keyed by raw appWidgetId — same key the widget reads
                            // via GlanceAppWidgetManager(context).getAppWidgetId(id)
                            if (widgetType == WidgetType.FOLDER) {
                                WidgetPrefs.setFolderId(
                                    this@WidgetConfigActivity, appWidgetId,
                                    selectedFolder ?: "fld-inbox",
                                )
                            } else {
                                WidgetPrefs.setFilterFolder(this@WidgetConfigActivity, appWidgetId, selectedFolder)
                                WidgetPrefs.setFilterLabel(this@WidgetConfigActivity, appWidgetId, selectedLabel)
                                WidgetPrefs.setFilterPriority(this@WidgetConfigActivity, appWidgetId, selectedPriority)
                            }

                            // Trigger first render (getGlanceIdBy only needed for update())
                            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity)
                                .getGlanceIdBy(appWidgetId)
                            when (widgetType) {
                                WidgetType.FOLDER    -> FolderWidget().update(this@WidgetConfigActivity, glanceId)
                                WidgetType.TASK_LIST -> TaskListWidget().update(this@WidgetConfigActivity, glanceId)
                                else -> Unit
                            }

                            setResult(
                                RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                            )
                            finish()
                        }
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(
    widgetType: WidgetType,
    folders: List<Folder>,
    labels: List<Label>,
    onConfirm: (selectedFolder: String?, selectedLabel: String?, selectedPriority: String?) -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (widgetType) {
                            WidgetType.FOLDER -> "Choose Folder"
                            WidgetType.TASK_LIST -> "Configure Widget"
                            else -> "Widget Setup"
                        }
                    )
                }
            )
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
                WidgetType.FOLDER -> FolderSelectorContent(
                    folders = folders,
                    onConfirm = { folderId -> onConfirm(folderId, null, null) },
                    onCancel = onCancel,
                )
                WidgetType.TASK_LIST -> TaskListFilterContent(
                    folders = folders,
                    labels = labels,
                    onConfirm = onConfirm,
                    onCancel = onCancel,
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun FolderSelectorContent(
    folders: List<Folder>,
    onConfirm: (folderId: String) -> Unit,
    onCancel: () -> Unit,
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
                onClick = { selectedFolderId = folder.id },
            )
            Text(
                text = folder.name,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    ActionButtons(
        onConfirm = { onConfirm(selectedFolderId) },
        onCancel = onCancel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListFilterContent(
    folders: List<Folder>,
    labels: List<Label>,
    onConfirm: (selectedFolder: String?, selectedLabel: String?, selectedPriority: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    var selectedPriority by remember { mutableStateOf<String?>(null) }

    Text("Filter tasks (all optional):", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(12.dp))

    // Folder filter
    Text("Folder", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    val folderOptions = listOf(null to "All Folders") + folders.map { it.id to it.name }
    SimpleDropdown(
        options = folderOptions,
        selected = selectedFolder,
        onSelected = { selectedFolder = it },
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Label filter
    Text("Label", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    val labelOptions = listOf(null to "All Labels") + labels.map { it.id to it.name }
    SimpleDropdown(
        options = labelOptions,
        selected = selectedLabel,
        onSelected = { selectedLabel = it },
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Priority filter
    Text("Priority", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    val priorityOptions = listOf(
        null to "All Priorities",
        "urgent" to "Urgent",
        "important" to "Important",
        "normal" to "Normal",
    )
    SimpleDropdown(
        options = priorityOptions,
        selected = selectedPriority,
        onSelected = { selectedPriority = it },
    )

    Spacer(modifier = Modifier.height(24.dp))
    ActionButtons(
        onConfirm = { onConfirm(selectedFolder, selectedLabel, selectedPriority) },
        onCancel = onCancel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDropdown(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = options.find { it.first == selected }?.second ?: options.firstOrNull()?.second ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        TextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
    ) {
        OutlinedButton(onClick = onCancel) {
            Text("Cancel")
        }
        Button(onClick = onConfirm) {
            Text("Add Widget")
        }
    }
}
