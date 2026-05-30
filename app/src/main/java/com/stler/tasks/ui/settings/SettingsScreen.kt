package com.stler.tasks.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.tasks.domain.model.Label
import com.stler.tasks.ui.main.DeleteLabelDialog
import com.stler.tasks.ui.main.LabelFormDialog
import com.stler.tasks.util.toComposeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val featureFlags     by viewModel.featureFlags.collectAsStateWithLifecycle()
    val spreadsheetId    by viewModel.spreadsheetId.collectAsStateWithLifecycle()
    val spreadsheetName  by viewModel.spreadsheetName.collectAsStateWithLifecycle()
    val files            by viewModel.files.collectAsStateWithLifecycle()
    val loading          by viewModel.loading.collectAsStateWithLifecycle()
    val switching        by viewModel.switching.collectAsStateWithLifecycle()
    val labels           by viewModel.labels.collectAsStateWithLifecycle()
    val calendars        by viewModel.calendars.collectAsStateWithLifecycle()
    val calendarsLoading by viewModel.calendarsLoading.collectAsStateWithLifecycle()
    val calendarsEnabled = featureFlags.calendarsEnabled

    var pickerExpanded by remember { mutableStateOf(false) }

    // Label dialog state
    var showLabelForm   by remember { mutableStateOf(false) }
    var editingLabel    by remember { mutableStateOf<Label?>(null) }
    var deletingLabel   by remember { mutableStateOf<Label?>(null) }

    // Load calendars when enabled and on first display
    LaunchedEffect(calendarsEnabled) {
        if (calendarsEnabled) viewModel.loadCalendars()
    }

    // Dialogs
    if (showLabelForm || editingLabel != null) {
        LabelFormDialog(
            existing  = editingLabel,
            onConfirm = { name, color ->
                if (editingLabel != null) viewModel.updateLabel(editingLabel!!, name, color)
                else viewModel.createLabel(name, color)
                showLabelForm = false; editingLabel = null
            },
            onDismiss = { showLabelForm = false; editingLabel = null },
        )
    }
    deletingLabel?.let { lbl ->
        DeleteLabelDialog(
            label     = lbl,
            onConfirm = { viewModel.deleteLabel(lbl.id); deletingLabel = null },
            onDismiss = { deletingLabel = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {

            // ══ FEATURES ══════════════════════════════════════════════════════

            SectionHeader(text = "FEATURES")

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                FeatureToggleRow(
                    title       = "Folders",
                    subtitle    = "Organize tasks into folders",
                    checked     = featureFlags.foldersEnabled,
                    onToggle    = viewModel::setFoldersEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FeatureToggleRow(
                    title       = "Labels",
                    subtitle    = "Tag tasks with colored labels",
                    checked     = featureFlags.labelsEnabled,
                    onToggle    = viewModel::setLabelsEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FeatureToggleRow(
                    title       = "Priorities",
                    subtitle    = "Mark tasks as Urgent, Important, or Normal",
                    checked     = featureFlags.prioritiesEnabled,
                    onToggle    = viewModel::setPrioritiesEnabled,
                )
            }

            // ══ SPREADSHEET ════════════════════════════════════════════════════

            SectionHeader(text = "SPREADSHEET")

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Outlined.TableChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = spreadsheetName.ifEmpty { spreadsheetId.ifEmpty { "—" } },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Google Sheets data source",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (!pickerExpanded) viewModel.loadFiles()
                            pickerExpanded = !pickerExpanded
                        },
                        enabled = !switching,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        if (switching) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Switching…", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Text(
                                if (pickerExpanded) "Cancel" else "Change",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = pickerExpanded) {
                    Column {
                        HorizontalDivider()
                        when {
                            loading -> Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Loading your sheets…", style = MaterialTheme.typography.bodySmall)
                            }

                            files.isEmpty() -> Text(
                                text = "No Google Sheets found in your Drive.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(16.dp),
                            )

                            else -> Column {
                                files.forEach { file ->
                                    val isActive = file.id == spreadsheetId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !switching) {
                                                pickerExpanded = false
                                                viewModel.switchSpreadsheet(file)
                                            }
                                            .background(
                                                if (isActive) MaterialTheme.colorScheme.surfaceVariant
                                                else Color.Transparent,
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (isActive) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                Icons.Outlined.Edit,
                                                contentDescription = "Active",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ══ LABELS ═════════════════════════════════════════════════════════

            AnimatedVisibility(visible = featureFlags.labelsEnabled) {
                Column {
                    SectionHeader(text = "LABELS")

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        if (labels.isEmpty()) {
                            Text(
                                text = "No labels yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            labels.forEach { label ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(label.color.toComposeColor()),
                                    )
                                    Text(
                                        text = label.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    IconButton(onClick = { editingLabel = label }) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = "Edit",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { deletingLabel = label }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Delete",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
                            }
                        }
                        // Add label button
                        TextButton(
                            onClick = { showLabelForm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add label")
                        }
                    }
                }
            }

            // ══ CALENDARS ══════════════════════════════════════════════════════

            SectionHeader(text = "CALENDARS")

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                // Header row: title + enable/disable switch + refresh button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Google Calendars",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = calendarsEnabled,
                        onCheckedChange = viewModel::setCalendarsEnabled,
                    )
                    AnimatedVisibility(visible = calendarsEnabled) {
                        IconButton(
                            onClick = viewModel::loadCalendars,
                            enabled = !calendarsLoading,
                        ) {
                            if (calendarsLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh calendars", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = calendarsEnabled) {
                    Column {
                        HorizontalDivider()
                        when {
                            calendarsLoading && calendars.isEmpty() -> Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Loading calendars…", style = MaterialTheme.typography.bodySmall)
                            }

                            calendars.isEmpty() -> Text(
                                text = "No calendars found. Tap refresh to load.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )

                            else -> Column {
                                calendars.forEach { calendar ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.toggleCalendar(calendar.id, !calendar.isSelected) }
                                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CalendarMonth,
                                            contentDescription = null,
                                            tint = calendar.color.toComposeColor(),
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Text(
                                            text = calendar.summary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Checkbox(
                                            checked = calendar.isSelected,
                                            onCheckedChange = { viewModel.toggleCalendar(calendar.id, it) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun FeatureToggleRow(
    title   : String,
    subtitle: String,
    checked : Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
