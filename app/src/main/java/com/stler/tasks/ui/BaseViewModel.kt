package com.stler.tasks.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel that provides [safeLaunch] — a drop-in replacement for
 * `viewModelScope.launch` that catches and logs any non-cancellation exception
 * rather than silently swallowing it (the default behaviour when no
 * CoroutineExceptionHandler is set on viewModelScope).
 *
 * All ViewModels that perform repository mutations extend this class so that
 * SQLiteException, IOException, etc. are always visible in logcat.
 * User-visible error feedback (Snackbar / error state) is a Stage 10 concern.
 */
abstract class BaseViewModel : ViewModel() {

    /**
     * Single-shot error events for user-visible Snackbar messages.
     * Screens collect this flow inside a LaunchedEffect and show the message
     * via their SnackbarHostState. Using a Channel (not StateFlow) means each
     * error is delivered exactly once even if the UI is briefly off-screen.
     */
    private val _uiError = Channel<String>(Channel.BUFFERED)
    val uiError = _uiError.receiveAsFlow()

    /**
     * Launches [block] in [viewModelScope].
     * - CancellationException is always rethrown so coroutine machinery stays correct.
     * - Any other exception is caught, logged, and forwarded to [uiError] so the
     *   active screen can show a Snackbar (SQLiteException, IOException, etc.).
     */
    protected fun safeLaunch(block: suspend CoroutineScope.() -> Unit) =
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e   // never swallow cancellation
            } catch (e: Exception) {
                Log.e(
                    this@BaseViewModel::class.simpleName ?: "BaseViewModel",
                    "Unhandled error in safeLaunch",
                    e,
                )
                _uiError.trySend("Something went wrong. Please try again.")
            }
        }
}
