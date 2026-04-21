package com.stler.tasks.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.stler.tasks.sync.SyncState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksTopAppBar(
    title: String,
    syncState: SyncState,
    userName: String,
    userEmail: String,
    userAvatarUrl: String,
    onMenuClick: () -> Unit,
    onSyncClick: () -> Unit,
    onSignOut: () -> Unit,
) {
    var showUserMenu by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "sync")
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "syncRotation",
    )

    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Outlined.Menu, contentDescription = "Open menu")
            }
        },
        actions = {
            // Sync icon — spins while syncing, static otherwise
            IconButton(onClick = onSyncClick) {
                val isSyncing = syncState is SyncState.Syncing
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = when (syncState) {
                        SyncState.Idle       -> "Sync"
                        SyncState.Syncing    -> "Syncing…"
                        is SyncState.Pending -> "Sync (pending)"
                    },
                    modifier = Modifier.rotate(if (isSyncing) syncRotation else 0f),
                )
            }

            // Avatar → user dropdown
            Box {
                IconButton(onClick = { showUserMenu = true }) {
                    if (userAvatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = userAvatarUrl,
                            contentDescription = userName,
                            modifier = Modifier.size(32.dp).clip(CircleShape),
                        )
                    } else {
                        Icon(Icons.Outlined.AccountCircle, contentDescription = userName)
                    }
                }

                DropdownMenu(
                    expanded = showUserMenu,
                    onDismissRequest = { showUserMenu = false },
                ) {
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Text(
                        text = userEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                    DropdownMenuItem(
                        text = { Text("Sign out", color = MaterialTheme.colorScheme.error) },
                        onClick = { showUserMenu = false; onSignOut() },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        },
    )
}
