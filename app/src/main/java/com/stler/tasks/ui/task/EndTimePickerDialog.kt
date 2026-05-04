package com.stler.tasks.ui.task

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Time-only picker dialog for selecting an event end time.
 *
 * @param initialTime  Existing end time "HH:MM", or "" (defaults to 10:00).
 * @param onConfirm    Called with "HH:MM" when the user taps OK.
 * @param onClear      Called when the user taps Clear (clears the end time).
 * @param onDismiss    Called when the dialog is dismissed without saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndTimePickerDialog(
    initialTime: String,
    onConfirm  : (String) -> Unit,
    onClear    : () -> Unit,
    onDismiss  : () -> Unit,
) {
    val (initH, initM) = remember(initialTime) {
        if (initialTime.isBlank()) 10 to 0
        else runCatching {
            val parts = initialTime.split(":")
            parts[0].toInt() to parts[1].toInt()
        }.getOrDefault(10 to 0)
    }
    val timePickerState = rememberTimePickerState(
        initialHour   = initH,
        initialMinute = initM,
        is24Hour      = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("End time") },
        text             = { TimePicker(state = timePickerState) },
        confirmButton    = {
            TextButton(onClick = {
                onConfirm("%02d:%02d".format(timePickerState.hour, timePickerState.minute))
            }) { Text("OK") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear)   { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
