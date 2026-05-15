package com.stler.tasks.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Short guide") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HelpSection(label = "Getting started") {
                HelpParagraph("Tasks syncs your data with Google Sheets — everything lives in your own Google account, no separate sign-up needed.")
                HelpList(listOf(
                    "Sign in with Google to get started.",
                    "A spreadsheet is created automatically in your Google Drive.",
                    "All tasks, folders, and labels are saved there in real time.",
                ))
                HelpNote("Your data is yours — you can open and edit the spreadsheet directly in Google Sheets at any time.")
            }

            HelpSection(label = "Tasks") {
                HelpParagraph("Create tasks with a title, priority, deadline, folder, and labels. Tasks support unlimited subtask nesting.")
                HelpTable(rows = listOf(
                    "Title"    to "Required. The main description of the task.",
                    "Priority" to "Urgent, Important, or Normal. Affects sort order in All Tasks.",
                    "Deadline" to "Optional date. Overdue tasks are highlighted in red.",
                    "Folder"   to "Group tasks by project or area of work.",
                    "Labels"   to "Add multiple tags for flexible filtering.",
                    "Subtasks" to "Tap the expand arrow to add child tasks under any task.",
                ))
            }

            HelpSection(label = "Views") {
                HelpParagraph("Navigate between views from the sidebar.")
                HelpList(listOf(
                    "Upcoming — tasks with upcoming or overdue deadlines, sorted by date.",
                    "All Tasks — every active task, sorted by priority.",
                    "Completed — finished tasks, hidden from main views.",
                    "Folders — tasks filtered to a single folder.",
                    "Labels — tasks filtered by a specific tag.",
                    "Priority — tabs for Urgent, Important, and Normal tasks.",
                ))
            }

            HelpSection(label = "Calendar & events") {
                HelpParagraph("Tasks integrates with Google Calendar. Enable calendar sync in Settings to see events alongside your tasks.")
                HelpList(listOf(
                    "View Google Calendar events in Upcoming and All Tasks.",
                    "Open a calendar from the sidebar to see its events in a dedicated list.",
                    "Create new events directly from the + button.",
                    "Events from shared read-only calendars (e.g. holidays) are shown without edit buttons.",
                ))
                HelpNote("Calendar sync requires the Google Calendar scope. You may be asked to re-authorise when enabling it for the first time.")
            }

            HelpSection(label = "Sync & offline") {
                HelpParagraph("Tasks works offline. Changes made without internet are saved locally and synced automatically when you reconnect.")
                HelpList(listOf(
                    "Sync status is shown in the top bar.",
                    "Tap the sync icon to manually trigger a sync.",
                    "Conflicts are resolved automatically — last write wins.",
                ))
                HelpNote("If you edit the same task on two devices simultaneously, the most recent change is kept.")
            }

            HelpSection(label = "Widgets") {
                HelpParagraph("Add home screen widgets for quick access to your tasks.")
                HelpList(listOf(
                    "Upcoming Tasks — tasks with deadlines in the next 7 days.",
                    "Folder — hierarchical view of a single folder with subtasks.",
                    "Task List — flat list with optional folder, label, or priority filters.",
                    "Calendar — events from selected Google calendars.",
                ))
                HelpNote("Long-press your home screen and tap Widgets to add them. Tap a task row to open it in the app.")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HelpSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text  = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun HelpParagraph(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun HelpList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text  = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(16.dp),
                )
                Text(text = item, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun HelpTable(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text     = "Field",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(90.dp),
            )
            Text(
                text  = "Description",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rows.forEach { (field, desc) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text     = field,
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(90.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text  = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HelpNote(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text     = text,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
