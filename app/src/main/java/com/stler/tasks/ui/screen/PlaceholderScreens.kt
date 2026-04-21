package com.stler.tasks.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Temporary placeholder composables for screens implemented in Stage 7.
 * Each will be replaced by a full implementation with its own file and ViewModel.
 */

@Composable
fun UpcomingScreen() = PlaceholderScreen("Upcoming — Stage 7")

@Composable
fun AllTasksScreen() = PlaceholderScreen("All Tasks — Stage 7")

@Composable
fun CompletedScreen() = PlaceholderScreen("Completed — Stage 7")

@Composable
fun FolderScreen(folderId: String) = PlaceholderScreen("Folder: $folderId — Stage 7")

@Composable
fun LabelScreen(labelId: String) = PlaceholderScreen("Label: $labelId — Stage 7")

@Composable
fun PriorityScreen(priority: String) = PlaceholderScreen("Priority: $priority — Stage 7")

@Composable
private fun PlaceholderScreen(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label)
    }
}
