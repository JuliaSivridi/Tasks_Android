package com.stler.tasks.ui.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stler.tasks.domain.model.Priority

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityPickerSheet(
    currentPriority: Priority,
    onSelect: (Priority) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Priority",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            listOf(Priority.URGENT, Priority.IMPORTANT, Priority.NORMAL).forEach { priority ->
                val label = when (priority) {
                    Priority.URGENT -> "Urgent"
                    Priority.IMPORTANT -> "Important"
                    Priority.NORMAL -> "Normal"
                }
                val color = priorityColor(priority)

                ListItem(
                    headlineContent = {
                        Text(text = label, color = color)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = color,
                        )
                    },
                    trailingContent = if (priority == currentPriority) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Selected",
                            )
                        }
                    } else null,
                    modifier = Modifier.clickable { onSelect(priority) },
                )
            }
        }
    }
}
