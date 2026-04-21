package com.stler.tasks.ui.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stler.tasks.domain.model.Label
import com.stler.tasks.util.toComposeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelPickerSheet(
    allLabels: List<Label>,
    selectedIds: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(selectedIds.toSet()) }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Labels",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = { onConfirm(selected.toList()) }) {
                    Text("Done")
                }
            }

            if (allLabels.isEmpty()) {
                Text(
                    text = "No labels yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                allLabels.forEach { label ->
                    val isSelected = label.id in selected
                    ListItem(
                        headlineContent = { Text(label.name) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Label,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = label.color.toComposeColor(),
                            )
                        },
                        trailingContent = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "Selected",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                        modifier = Modifier.clickable {
                            selected = if (isSelected) {
                                selected - label.id
                            } else {
                                selected + label.id
                            }
                        },
                    )
                }
            }
        }
    }
}
