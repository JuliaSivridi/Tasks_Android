package com.stler.tasks.ui.main

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.stler.tasks.auth.FeatureFlags
import com.stler.tasks.domain.model.CalendarItem
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.ui.navigation.Screen
import com.stler.tasks.ui.theme.NavSelected
import com.stler.tasks.ui.theme.SelectedHighlightDark
import com.stler.tasks.util.toComposeColor

@Composable
fun SidebarMenu(
    currentRoute     : String?,
    currentFolderId  : String?,
    currentCalendarId: String?,
    folders          : List<Folder>,
    selectedCalendars: List<CalendarItem>,
    featureFlags     : FeatureFlags,
    sidebarState     : SidebarState,
    onNavigate       : (String) -> Unit,
    onToggleSection  : (String) -> Unit,
    onAddTask        : () -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.70f)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── + Add task ──────────────────────────────────────────────────
            Button(
                onClick = onAddTask,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add task")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Main nav items ──────────────────────────────────────────────
            SimpleNavItem(
                label    = "Upcoming",
                icon     = Icons.Outlined.CalendarMonth,
                selected = currentRoute == Screen.UPCOMING,
                onClick  = { onNavigate(Screen.UPCOMING) },
            )
            SimpleNavItem(
                label    = "All Tasks",
                icon     = Icons.Outlined.FormatListBulleted,
                selected = currentRoute == Screen.ALL_TASKS,
                onClick  = { onNavigate(Screen.ALL_TASKS) },
            )
            SimpleNavItem(
                label    = "Completed",
                icon     = Icons.Outlined.CheckCircle,
                selected = currentRoute == Screen.COMPLETED,
                onClick  = { onNavigate(Screen.COMPLETED) },
            )

            // ── Folders ─────────────────────────────────────────────────────
            if (featureFlags.foldersEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionHeader(
                    title    = "Folders",
                    isOpen   = sidebarState.foldersOpen,
                    onToggle = { onToggleSection("folders") },
                )
                if (sidebarState.foldersOpen) {
                    folders.forEach { folder ->
                        FolderNavItem(
                            folder     = folder,
                            isSelected = currentRoute == Screen.FOLDER && currentFolderId == folder.id,
                            onClick    = { onNavigate(Screen.folderRoute(folder.id)) },
                        )
                    }
                }
            }

            // ── Calendars ────────────────────────────────────────────────────
            if (featureFlags.calendarsEnabled && selectedCalendars.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionHeader(
                    title    = "Calendars",
                    isOpen   = sidebarState.calendarsOpen,
                    onToggle = { onToggleSection("calendars") },
                )
                if (sidebarState.calendarsOpen) {
                    selectedCalendars.forEach { cal ->
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    tint = cal.color.toComposeColor(),
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label    = { Text(cal.summary) },
                            selected = currentRoute == Screen.CALENDAR && currentCalendarId == cal.id,
                            onClick  = { onNavigate(Screen.calendarRoute(cal.id)) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                            colors   = sidebarItemColors(),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

// ── Shared colors ──────────────────────────────────────────────────────────────

@Composable
private fun sidebarItemColors() = NavigationDrawerItemDefaults.colors(
    selectedContainerColor = if (isSystemInDarkTheme()) SelectedHighlightDark else NavSelected,
)

// ── Reusable items ─────────────────────────────────────────────────────────────

@Composable
private fun SimpleNavItem(
    label   : String,
    icon    : ImageVector,
    selected: Boolean,
    onClick : () -> Unit,
) {
    NavigationDrawerItem(
        icon     = { Icon(icon, contentDescription = null) },
        label    = { Text(label) },
        selected = selected,
        onClick  = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
        colors   = sidebarItemColors(),
    )
}

@Composable
private fun FolderNavItem(
    folder    : Folder,
    isSelected: Boolean,
    onClick   : () -> Unit,
) {
    val folderColor = folder.color.toComposeColor()
    val icon = if (folder.isInbox) Icons.Outlined.Inbox else Icons.Outlined.Folder

    NavigationDrawerItem(
        icon     = { Icon(icon, contentDescription = null, tint = folderColor) },
        label    = { Text(folder.name) },
        selected = isSelected,
        onClick  = onClick,
        colors   = sidebarItemColors(),
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun SectionHeader(
    title   : String,
    isOpen  : Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (isOpen) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                contentDescription = if (isOpen) "Collapse" else "Expand",
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
