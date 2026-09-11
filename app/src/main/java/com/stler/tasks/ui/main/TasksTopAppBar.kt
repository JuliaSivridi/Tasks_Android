package com.stler.tasks.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import com.stler.tasks.sync.SyncState

/** Filled when filters are active, outlined when idle — mirrors Money's MoneyTopAppBar pattern. */
@Composable
private fun FilterButton(active: Boolean, onClick: () -> Unit) {
    if (active) {
        FilledIconButton(onClick = onClick) {
            Icon(Icons.Outlined.Tune, contentDescription = "Filters active")
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(Icons.Outlined.Tune, contentDescription = "Filters", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksTopAppBar(
    title        : String,
    syncState    : SyncState,
    onSyncClick  : () -> Unit,
    onBackClick  : (() -> Unit)? = null,
    filterActive : Boolean       = false,
    onFilterClick: (() -> Unit)? = null,
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

    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            if (onFilterClick != null) {
                FilterButton(active = filterActive, onClick = onFilterClick)
            }
            IconButton(onClick = onSyncClick) {
                val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                when (syncState) {
                    SyncState.Idle -> Icon(
                        imageVector        = Icons.Outlined.CloudDone,
                        contentDescription = "Synced",
                        tint               = iconTint,
                    )
                    is SyncState.Pending -> BadgedBox(
                        badge = {
                            Badge(
                                containerColor = Color.Transparent,
                                contentColor   = iconTint,
                            ) {
                                Text(
                                    text  = "${syncState.count}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector        = Icons.Outlined.CloudUpload,
                            contentDescription = "Sync — ${syncState.count} pending",
                            tint               = iconTint,
                        )
                    }
                    SyncState.Syncing -> Icon(
                        imageVector        = Icons.Outlined.Sync,
                        contentDescription = "Syncing…",
                        modifier           = Modifier.rotate(syncRotation),
                        tint               = iconTint,
                    )
                }
            }
        },
    )
}
