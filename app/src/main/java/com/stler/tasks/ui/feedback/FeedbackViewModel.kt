package com.stler.tasks.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stler.tasks.auth.AuthPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
) : ViewModel() {

    private val _sendResult = MutableStateFlow<SendResult?>(null)
    val sendResult: StateFlow<SendResult?> = _sendResult

    fun send(message: String) {
        viewModelScope.launch {
            val email = authPreferences.userEmail.first()
            _sendResult.value = SendResult.Sending
            _sendResult.value = try {
                post(email = email, message = message)
                SendResult.Success
            } catch (_: Exception) {
                SendResult.Error
            }
        }
    }

    fun clearResult() { _sendResult.value = null }

    private fun post(email: String, message: String) {
        val body = buildString {
            append(URLEncoder.encode("app",     "UTF-8")).append('=').append(URLEncoder.encode("Tasks",   "UTF-8"))
            append('&')
            append(URLEncoder.encode("email",   "UTF-8")).append('=').append(URLEncoder.encode(email,    "UTF-8"))
            append('&')
            append(URLEncoder.encode("message", "UTF-8")).append('=').append(URLEncoder.encode(message,  "UTF-8"))
        }
        val conn = URL(FEEDBACK_URL).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = 10_000
            readTimeout    = 10_000
        }
        conn.outputStream.use { out ->
            OutputStreamWriter(out, "UTF-8").use { it.write(body) }
        }
        // Google Apps Script always returns 200 even for no-cors; just open the stream to finish the request.
        conn.inputStream.use { }
        conn.disconnect()
    }

    sealed interface SendResult {
        data object Sending : SendResult
        data object Success : SendResult
        data object Error   : SendResult
    }

    private companion object {
        const val FEEDBACK_URL =
            "https://script.google.com/macros/s/AKfycbzpQS6F8V0COZlL6gSBrgUP7YMtzz0djuZp7iNiIcGfmRSjdISvpiz5gzg1bCfGzoBB8A/exec"
    }
}
