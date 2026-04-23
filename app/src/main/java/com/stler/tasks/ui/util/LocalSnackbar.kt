package com.stler.tasks.ui.util

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import com.stler.tasks.ui.BaseViewModel

/**
 * Provides the root [SnackbarHostState] (created in MainScreen's Scaffold)
 * to any composable in the tree without passing it through every function signature.
 *
 * Usage in MainScreen:
 * ```
 * CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) { … }
 * ```
 *
 * Usage in a screen:
 * ```
 * ErrorSnackbarEffect(viewModel)
 * ```
 */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided — wrap your composable tree with LocalSnackbarHostState in MainScreen")
}

/**
 * Collects [BaseViewModel.uiError] and shows each message as a Snackbar.
 * Call this once per screen, at the top of the composable body.
 *
 * The effect is keyed on [viewModel] so it restarts if the ViewModel instance
 * changes (e.g. the NavBackStackEntry is replaced).
 */
@Composable
fun ErrorSnackbarEffect(viewModel: BaseViewModel) {
    val snackbarHostState = LocalSnackbarHostState.current
    LaunchedEffect(viewModel) {
        viewModel.uiError.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
}
