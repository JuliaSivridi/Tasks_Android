package com.stler.tasks.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
     * Launches [block] in [viewModelScope].
     * - CancellationException is always rethrown so coroutine machinery stays correct.
     * - Any other exception is caught and logged; it does NOT cancel sibling coroutines
     *   (viewModelScope uses a SupervisorJob).
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
            }
        }
}
