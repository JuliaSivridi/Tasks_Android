package com.stler.tasks.ui.main

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stler.tasks.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class UpdateState {
    object Idle              : UpdateState()
    object Checking          : UpdateState()
    object UpToDate          : UpdateState()
    data class UpdateAvailable(val version: String, val downloadUrl: String) : UpdateState()
    data class Downloading(val progress: Int)                                 : UpdateState()
    data class ReadyToInstall(val apkFile: File)                              : UpdateState()
    data class Error(val message: String)                                     : UpdateState()
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    // Plain client — no auth interceptor, no read timeout (streaming APK download)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    fun checkForUpdate() {
        if (_updateState.value is UpdateState.Checking) return
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            runCatching {
                withContext(Dispatchers.IO) { fetchLatestRelease() }
            }.onSuccess { (tagName, downloadUrl) ->
                val current = "v${BuildConfig.VERSION_NAME}"
                _updateState.value = if (tagName == current) {
                    UpdateState.UpToDate
                } else {
                    UpdateState.UpdateAvailable(tagName, downloadUrl)
                }
            }.onFailure { e ->
                _updateState.value = UpdateState.Error(e.message ?: "Network error")
            }
        }
    }

    fun downloadUpdate(downloadUrl: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    downloadApk(downloadUrl) { progress ->
                        _updateState.value = UpdateState.Downloading(progress)
                    }
                }
            }.onSuccess { apkFile ->
                _updateState.value = UpdateState.ReadyToInstall(apkFile)
            }.onFailure { e ->
                _updateState.value = UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun resetState() { _updateState.value = UpdateState.Idle }

    // ── Private ───────────────────────────────────────────────────────────

    private fun fetchLatestRelease(): Pair<String, String> {
        val request = Request.Builder()
            .url("https://api.github.com/repos/JuliaSivridi/Tasks_Android/releases/latest")
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        val body = httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            response.body!!.string()
        }
        val json      = JSONObject(body)
        val tagName   = json.getString("tag_name")
        val assets    = json.getJSONArray("assets")
        check(assets.length() > 0) { "No APK asset found in release $tagName" }
        val downloadUrl = assets.getJSONObject(0).getString("browser_download_url")
        return tagName to downloadUrl
    }

    private fun downloadApk(url: String, onProgress: (Int) -> Unit): File {
        val dir     = File(context.cacheDir, "updates").also { it.mkdirs() }
        val apkFile = File(dir, "stler-tasks-update.apk")

        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body  = response.body!!
            val total = body.contentLength()
            var downloaded = 0L

            body.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8_192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) onProgress((downloaded * 100 / total).toInt())
                    }
                }
            }
        }
        return apkFile
    }
}
