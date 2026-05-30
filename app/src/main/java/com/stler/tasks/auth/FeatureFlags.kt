package com.stler.tasks.auth

/**
 * Snapshot of all feature-visibility toggles.
 * Exposed as a single [kotlinx.coroutines.flow.Flow] from [AuthPreferences.featureFlags]
 * so ViewModels only need one subscription.
 *
 * All flags default to `true` — existing users see no change on upgrade.
 */
data class FeatureFlags(
    val foldersEnabled   : Boolean = true,
    val labelsEnabled    : Boolean = true,
    val prioritiesEnabled: Boolean = true,
    val calendarsEnabled : Boolean = true,
)
