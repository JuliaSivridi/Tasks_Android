package com.stler.tasks.ui.alltasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stler.tasks.auth.FeatureFlags
import com.stler.tasks.domain.model.CalendarItem
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.ui.common.PillChip
import com.stler.tasks.ui.task.priorityColor
import com.stler.tasks.util.toComposeColor

/**
 * Mirrors Money-Android's FilterMenu: a ModalBottomSheet with Priority /
 * Labels / Folders / Calendars sections. Every toggle fires immediately —
 * no Apply button. Selected chips sort to front when the sheet opens (same
 * behaviour as Money's selected-first ordering in accounts/categories).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskFilterSheet(
    filterState     : TaskFilterState,
    labels          : List<Label>,
    folders         : List<Folder>,
    calendars       : List<CalendarItem>,
    featureFlags    : FeatureFlags,
    showCalendars   : Boolean = true,
    onTogglePriority: (Priority) -> Unit,
    onToggleLabel   : (String) -> Unit,
    onToggleFolder  : (String) -> Unit,
    onToggleCalendar: (String) -> Unit,
    onClearAll      : () -> Unit,
    onDismiss       : () -> Unit,
) {
    val initialLabelSel    = remember { filterState.labelFilter }
    val initialFolderSel   = remember { filterState.folderFilter }
    val initialCalendarSel = remember { filterState.calendarFilter }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        if (filterState.hasFilters) {
            TextButton(
                onClick  = onClearAll,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text("Clear all filters", color = MaterialTheme.colorScheme.error)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (featureFlags.prioritiesEnabled) {
                FilterSection("Priority") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            Priority.URGENT    to "Urgent",
                            Priority.IMPORTANT to "Important",
                            Priority.NORMAL    to "Normal",
                        ).forEach { (priority, label) ->
                            PillChip(
                                selected = priority in filterState.priorityFilter,
                                onClick  = { onTogglePriority(priority) },
                                label    = label,
                                leadingContent = {
                                    Icon(
                                        Icons.Outlined.Flag, null,
                                        modifier = Modifier.size(12.dp),
                                        tint     = priorityColor(priority),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            if (featureFlags.labelsEnabled && labels.isNotEmpty()) {
                FilterSection("Labels") {
                    Column(modifier = Modifier.heightIn(max = 156.dp).verticalScroll(rememberScrollState())) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement   = Arrangement.spacedBy(8.dp),
                        ) {
                            labels
                                .sortedWith(
                                    compareByDescending<Label> { it.id in initialLabelSel }
                                        .thenBy { it.sortOrder }
                                )
                                .forEach { lbl ->
                                    val color = lbl.color.toComposeColor()
                                    PillChip(
                                        selected = lbl.id in filterState.labelFilter,
                                        onClick  = { onToggleLabel(lbl.id) },
                                        label    = lbl.name,
                                        leadingContent = {
                                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                                        },
                                    )
                                }
                        }
                    }
                }
            }

            if (featureFlags.foldersEnabled && folders.isNotEmpty()) {
                FilterSection("Folders") {
                    Column(modifier = Modifier.heightIn(max = 156.dp).verticalScroll(rememberScrollState())) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement   = Arrangement.spacedBy(8.dp),
                        ) {
                            folders
                                .sortedWith(
                                    compareByDescending<Folder> { it.id in initialFolderSel }
                                        .thenBy { it.sortOrder }
                                )
                                .forEach { fld ->
                                    val color = fld.color.toComposeColor()
                                    PillChip(
                                        selected = fld.id in filterState.folderFilter,
                                        onClick  = { onToggleFolder(fld.id) },
                                        label    = fld.name,
                                        leadingContent = {
                                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                                        },
                                    )
                                }
                        }
                    }
                }
            }

            if (showCalendars && calendars.isNotEmpty()) {
                FilterSection("Calendars") {
                    Column(modifier = Modifier.heightIn(max = 156.dp).verticalScroll(rememberScrollState())) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement   = Arrangement.spacedBy(8.dp),
                        ) {
                            calendars
                                .sortedWith(
                                    compareByDescending<CalendarItem> { it.id in initialCalendarSel }
                                        .thenBy { it.summary }
                                )
                                .forEach { cal ->
                                    val color = cal.color.toComposeColor()
                                    PillChip(
                                        selected = cal.id in filterState.calendarFilter,
                                        onClick  = { onToggleCalendar(cal.id) },
                                        label    = cal.summary,
                                        leadingContent = {
                                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                                        },
                                    )
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text     = title.uppercase(),
            style    = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}
