package com.stler.tasks.sync

/**
 * Represents the current synchronization status, used by the TopAppBar icon.
 *
 * Idle    → cloud-check icon
 * Pending → cloud + badge with pending count
 * Syncing → spinning RefreshCw icon
 */
sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Pending(val count: Int) : SyncState()
}
