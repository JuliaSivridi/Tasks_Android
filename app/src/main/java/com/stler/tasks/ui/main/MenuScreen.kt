package com.stler.tasks.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.stler.tasks.auth.AuthData
import com.stler.tasks.sync.SyncState

@Composable
fun MenuScreen(
    authData             : AuthData,
    syncState            : SyncState,
    onSyncClick          : () -> Unit,
    onNavigateToSettings : () -> Unit,
    onNavigateToHelp     : () -> Unit,
    onNavigateToFeedback : () -> Unit,
    onSignOut            : () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync")
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "syncRotation",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── User info ──────────────────────────────────────────────────────────
        Column(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(8.dp),
        ) {
            if (authData.userAvatarUrl.isNotBlank()) {
                AsyncImage(
                    model              = authData.userAvatarUrl,
                    contentDescription = authData.userName,
                    modifier           = Modifier.size(72.dp).clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector        = Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    modifier           = Modifier.size(72.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (authData.userName.isNotBlank()) {
                Text(authData.userName, style = MaterialTheme.typography.titleMedium)
            }
            if (authData.userEmail.isNotBlank()) {
                Text(
                    text  = authData.userEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider()

        // ── Sync ───────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSyncClick)
                .padding(horizontal = 20.dp)
                .heightIn(min = 56.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            when (syncState) {
                SyncState.Idle -> Icon(
                    imageVector        = Icons.Outlined.CloudDone,
                    contentDescription = "Synced",
                    tint               = iconTint,
                    modifier           = Modifier.size(24.dp),
                )
                is SyncState.Pending -> BadgedBox(
                    badge = {
                        Badge(containerColor = Color.Transparent, contentColor = iconTint) {
                            Text("${syncState.count}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.CloudUpload,
                        contentDescription = "Sync pending",
                        tint               = iconTint,
                        modifier           = Modifier.size(24.dp),
                    )
                }
                SyncState.Syncing -> Icon(
                    imageVector        = Icons.Outlined.Sync,
                    contentDescription = "Syncing…",
                    modifier           = Modifier.size(24.dp).rotate(syncRotation),
                    tint               = iconTint,
                )
            }
            Column {
                Text("Sync", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text  = when (syncState) {
                        SyncState.Idle       -> "Up to date"
                        is SyncState.Pending -> "${syncState.count} pending"
                        SyncState.Syncing    -> "Syncing…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider()

        // ── Menu items ─────────────────────────────────────────────────────────
        MenuRow(icon = Icons.Outlined.Settings,        label = "Settings", onClick = onNavigateToSettings)
        HorizontalDivider()
        MenuRow(icon = Icons.Outlined.HelpOutline,     label = "Help",     onClick = onNavigateToHelp)
        HorizontalDivider()
        MenuRow(icon = Icons.Outlined.Message,         label = "Feedback", onClick = onNavigateToFeedback)
        HorizontalDivider()
        MenuRow(
            icon          = Icons.AutoMirrored.Outlined.Logout,
            label         = "Sign out",
            onClick       = onSignOut,
            isDestructive = true,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MenuRow(
    icon         : ImageVector,
    label        : String,
    onClick      : () -> Unit,
    isDestructive: Boolean = false,
) {
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
            .heightIn(min = 56.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = contentColor,
            modifier           = Modifier.size(24.dp),
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
        )
    }
}
