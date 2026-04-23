package com.stler.tasks.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.sync.SyncState
import com.stler.tasks.ui.navigation.Screen
import com.stler.tasks.ui.theme.AccentDark
import com.stler.tasks.ui.theme.NavSelected
import com.stler.tasks.ui.theme.PriorityImportant
import com.stler.tasks.ui.theme.PriorityNormal
import com.stler.tasks.ui.theme.PriorityUrgent
import com.stler.tasks.util.toComposeColor

@Composable
fun SidebarMenu(
    currentRoute: String?,
    currentFolderId: String?,
    currentLabelId: String?,
    currentPriority: String?,
    folders: List<Folder>,
    labels: List<Label>,
    syncState: SyncState,
    sidebarState: SidebarState,
    onNavigate: (String) -> Unit,
    onToggleSection: (String) -> Unit,
    onAddTask: () -> Unit,
    onAddFolder: () -> Unit,
    onAddLabel: () -> Unit,
    onEditFolder: (Folder) -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    onEditLabel: (Label) -> Unit,
    onDeleteLabel: (Label) -> Unit,
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
                label = "Upcoming",
                icon = Icons.Outlined.CalendarMonth,
                selected = currentRoute == Screen.UPCOMING,
                onClick = { onNavigate(Screen.UPCOMING) },
            )
            SimpleNavItem(
                label = "All Tasks",
                icon = Icons.Outlined.FormatListBulleted,
                selected = currentRoute == Screen.ALL_TASKS,
                onClick = { onNavigate(Screen.ALL_TASKS) },
            )
            SimpleNavItem(
                label = "Completed",
                icon = Icons.Outlined.CheckCircle,
                selected = currentRoute == Screen.COMPLETED,
                onClick = { onNavigate(Screen.COMPLETED) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Priorities ──────────────────────────────────────────────────
            SectionHeader(
                title = "Priorities",
                isOpen = sidebarState.prioritiesOpen,
                onToggle = { onToggleSection("priorities") },
                onAdd = null,
            )
            if (sidebarState.prioritiesOpen) {
                PriorityNavItem("Urgent",    PriorityUrgent,    currentRoute, currentPriority, onNavigate)
                PriorityNavItem("Important", PriorityImportant, currentRoute, currentPriority, onNavigate)
                PriorityNavItem("Normal",    PriorityNormal,    currentRoute, currentPriority, onNavigate)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Labels ──────────────────────────────────────────────────────
            SectionHeader(
                title = "Labels",
                isOpen = sidebarState.labelsOpen,
                onToggle = { onToggleSection("labels") },
                onAdd = onAddLabel,
            )
            if (sidebarState.labelsOpen) {
                labels.forEach { label ->
                    LabelNavItem(
                        label = label,
                        isSelected = currentRoute == Screen.LABEL && currentLabelId == label.id,
                        onClick = { onNavigate(Screen.labelRoute(label.id)) },
                        onEdit = { onEditLabel(label) },
                        onDelete = { onDeleteLabel(label) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Folders ─────────────────────────────────────────────────────
            SectionHeader(
                title = "Folders",
                isOpen = sidebarState.foldersOpen,
                onToggle = { onToggleSection("folders") },
                onAdd = onAddFolder,
            )
            if (sidebarState.foldersOpen) {
                folders.forEach { folder ->
                    FolderNavItem(
                        folder = folder,
                        isSelected = currentRoute == Screen.FOLDER && currentFolderId == folder.id,
                        onClick = { onNavigate(Screen.folderRoute(folder.id)) },
                        onEdit = { onEditFolder(folder) },
                        onDelete = { onDeleteFolder(folder) },
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            HorizontalDivider()

            // ── Sync footer ─────────────────────────────────────────────────
            SyncFooter(syncState = syncState)
        }
    }
}

// ── Shared colors ──────────────────────────────────────────────────────────────

/**
 * Explicit selected-container color for all sidebar nav items.
 * NavigationDrawerItem defaults to primaryContainer, which in light mode is a
 * barely-visible cream (#FDF6ED). We use a neutral gray instead so the active
 * item is always clearly legible regardless of system theme.
 */
@Composable
private fun sidebarItemColors() = NavigationDrawerItemDefaults.colors(
    selectedContainerColor = if (isSystemInDarkTheme()) AccentDark else NavSelected,
)

// ── Reusable items ─────────────────────────────────────────────────────────────

@Composable
private fun SimpleNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
        colors = sidebarItemColors(),
    )
}

@Composable
private fun PriorityNavItem(
    name: String,
    color: Color,
    currentRoute: String?,
    currentPriority: String?,
    onNavigate: (String) -> Unit,
) {
    val key = name.lowercase()
    NavigationDrawerItem(
        icon = { Icon(Icons.Outlined.Flag, contentDescription = null, tint = color) },
        label = { Text(name) },
        selected = currentRoute == Screen.PRIORITY && currentPriority == key,
        onClick = { onNavigate(Screen.priorityRoute(key)) },
        modifier = Modifier.padding(horizontal = 12.dp),
        colors = sidebarItemColors(),
    )
}

@Composable
private fun FolderNavItem(
    folder: Folder,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val folderColor = folder.color.toComposeColor()
    val icon = if (folder.isInbox) Icons.Outlined.Inbox else Icons.Outlined.Folder

    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null, tint = folderColor) },
        label = { Text(folder.name) },
        selected = isSelected,
        onClick = onClick,
        colors = sidebarItemColors(),
        badge = {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Options", modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = { showMenu = false; onEdit() },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = {
                        Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                    },
                )
            }
        },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun LabelNavItem(
    label: Label,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val labelColor = label.color.toComposeColor()

    NavigationDrawerItem(
        icon = { Icon(Icons.Outlined.Label, contentDescription = null, tint = labelColor) },
        label = { Text(label.name) },
        selected = isSelected,
        onClick = onClick,
        colors = sidebarItemColors(),
        badge = {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Options", modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = { showMenu = false; onEdit() },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = {
                        Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                    },
                )
            }
        },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun SectionHeader(
    title: String,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onAdd: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = if (isOpen) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                contentDescription = if (isOpen) "Collapse" else "Expand",
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onAdd != null) {
            IconButton(onClick = onAdd, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SyncFooter(syncState: SyncState) {
    val infiniteTransition = rememberInfiniteTransition(label = "footer_sync")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "footerRotation",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Sync,
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .rotate(if (syncState is SyncState.Syncing) rotation else 0f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = when (syncState) {
                SyncState.Idle       -> "Synced"
                SyncState.Syncing    -> "Syncing…"
                is SyncState.Pending -> "${syncState.count} changes pending"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
