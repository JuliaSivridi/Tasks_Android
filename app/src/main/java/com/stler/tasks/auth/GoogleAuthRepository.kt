package com.stler.tasks.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.gson.Gson
import com.stler.tasks.R
import com.stler.tasks.data.local.dao.FolderDao
import com.stler.tasks.data.local.dao.LabelDao
import com.stler.tasks.data.local.dao.SyncQueueDao
import com.stler.tasks.data.local.dao.TaskDao
import com.stler.tasks.data.remote.TokenProvider
import com.stler.tasks.data.remote.dto.DriveFilesResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authPreferences: AuthPreferences,
    private val taskDao: TaskDao,
    private val folderDao: FolderDao,
    private val labelDao: LabelDao,
    private val syncQueueDao: SyncQueueDao,
) : TokenProvider {

    // ── Public state ──────────────────────────────────────────────────────

    val isSignedIn: Flow<Boolean> = authPreferences.accessToken.map { it.isNotBlank() }

    val authData: Flow<AuthData> = combine(
        authPreferences.userEmail,
        authPreferences.userName,
        authPreferences.userAvatarUrl,
        authPreferences.spreadsheetId,
    ) { email, name, avatar, spreadsheetId ->
        AuthData(email, name, avatar, spreadsheetId)
    }

    // ── TokenProvider ─────────────────────────────────────────────────────

    override suspend fun getAccessToken(): String {
        val token  = authPreferences.accessToken.first()
        val expiry = authPreferences.tokenExpiry.first()
        if (token.isBlank()) return ""
        return if (isExpiredSoon(expiry)) refreshToken() ?: "" else token
    }

    /**
     * Refreshes the token silently using the Authorization client.
     * Works in the background (no Activity needed) once scopes are approved.
     * Returns null if the user must re-authenticate interactively.
     */
    override suspend fun refreshToken(): String? = runCatching {
        val result = Identity.getAuthorizationClient(context)
            .authorize(buildAuthRequest())
            .await()
        if (result.hasResolution()) return@runCatching null
        val newToken = result.accessToken ?: return@runCatching null
        authPreferences.saveToken(newToken, expiryInOneHour())
        newToken
    }.getOrNull()

    // ── Sign-in flow ──────────────────────────────────────────────────────

    sealed class SignInStep {
        data object Success : SignInStep()
        /** User must approve scopes via this intent before sign-in completes. */
        class NeedsAuthorization(val pendingIntent: android.app.PendingIntent) : SignInStep()
    }

    /**
     * Full sign-in flow.
     * 1. CredentialManager → Google account picker → ID token (user info)
     * 2. Identity.authorize → Sheets + Drive scope token
     * 3. Drive API → find db_tasks spreadsheet ID
     * 4. Save everything to DataStore
     *
     * [context] must be an Activity context (for CredentialManager UI).
     */
    suspend fun signIn(context: Context): Result<SignInStep> = runCatching {
        // Step 1 — get Google ID token credential
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.google_web_client_id))
            .build()

        val credentialResponse = credentialManager.getCredential(
            context = context,
            request = GetCredentialRequest(listOf(googleIdOption)),
        )
        val googleCredential = GoogleIdTokenCredential
            .createFrom(credentialResponse.credential.data)

        // Step 2 — authorize API scopes
        val authResult = Identity.getAuthorizationClient(context)
            .authorize(buildAuthRequest())
            .await()

        if (authResult.hasResolution()) {
            val intent = authResult.pendingIntent
                ?: throw IllegalStateException("hasResolution() is true but pendingIntent is null")
            return@runCatching SignInStep.NeedsAuthorization(intent)
        }

        // Step 3 & 4 — finalize
        val token = authResult.accessToken
            ?: throw IllegalStateException("No access token in authorization result")
        completeSignIn(token, googleCredential)
        SignInStep.Success
    }

    /**
     * Called after the user approves scopes via the authorization intent.
     * Extracts the token from the result intent and finalizes sign-in.
     */
    suspend fun finalizeAuth(intent: Intent): Result<Unit> = runCatching {
        val authResult = Identity.getAuthorizationClient(context)
            .getAuthorizationResultFromIntent(intent)

        val token = authResult.accessToken
            ?: throw IllegalStateException("No access token in authorization result")

        // Update token; user info was already saved in step 1 of signIn()
        if (authPreferences.spreadsheetId.first().isBlank()) {
            val spreadsheetId = findSpreadsheetId(token)
            authPreferences.saveAll(
                accessToken   = token,
                tokenExpiry   = expiryInOneHour(),
                spreadsheetId = spreadsheetId,
                userEmail     = authPreferences.userEmail.first(),
                userName      = authPreferences.userName.first(),
                userAvatarUrl = authPreferences.userAvatarUrl.first(),
            )
        } else {
            authPreferences.saveToken(token, expiryInOneHour())
        }
    }

    /** One-shot read of the stored auth data. */
    suspend fun getAuthData(): AuthData = authPreferences.getAuthData()

    /**
     * Called from SyncWorker when spreadsheetId is blank but a token exists.
     * Runs the Drive search again and persists the result.
     * Returns the found ID, or "" if still not found.
     */
    suspend fun findAndSaveSpreadsheetId(): String {
        val token = authPreferences.accessToken.first()
        if (token.isBlank()) {
            Log.w(TAG, "findAndSaveSpreadsheetId: no access token")
            return ""
        }
        val id = findSpreadsheetId(token)
        if (id.isNotBlank()) {
            authPreferences.saveSpreadsheetId(id)
            Log.i(TAG, "findAndSaveSpreadsheetId: saved spreadsheetId=$id")
        } else {
            Log.w(TAG, "findAndSaveSpreadsheetId: Drive search returned empty result")
        }
        return id
    }

    // ── Sign-out ──────────────────────────────────────────────────────────

    suspend fun signOut() {
        authPreferences.clearAll()
        taskDao.deleteAll()
        folderDao.deleteAll()
        labelDao.deleteAll()
        syncQueueDao.deleteAll()
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private suspend fun completeSignIn(
        accessToken: String,
        credential: GoogleIdTokenCredential,
    ) {
        val spreadsheetId = findSpreadsheetId(accessToken)
        authPreferences.saveAll(
            accessToken   = accessToken,
            tokenExpiry   = expiryInOneHour(),
            spreadsheetId = spreadsheetId,
            userEmail     = credential.id,
            userName      = credential.displayName ?: "",
            userAvatarUrl = credential.profilePictureUri?.toString() ?: "",
        )
    }

    private fun buildAuthRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope("https://www.googleapis.com/auth/spreadsheets"),
                    Scope("https://www.googleapis.com/auth/drive.metadata.readonly"),
                )
            )
            .build()

    /**
     * Finds the spreadsheetId of `db_tasks` via Drive API v3.
     * Uses a plain OkHttpClient with the token passed directly — avoids circular
     * dependency with the Hilt-managed OkHttpClient that uses TokenProvider.
     */
    private suspend fun findSpreadsheetId(accessToken: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val query = Uri.encode(
                "name='db_tasks' and mimeType='application/vnd.google-apps.spreadsheet' and trashed=false"
            )
            val url = "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name)"
            Log.d(TAG, "Drive search URL: $url")
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .build()

            val response = OkHttpClient().newCall(request).execute()
            val httpCode = response.code
            val body = response.body?.string() ?: ""
            Log.d(TAG, "Drive API HTTP $httpCode, body: $body")

            if (!response.isSuccessful) return@runCatching ""
            val result = Gson().fromJson(body, DriveFilesResponse::class.java)
            val id = result.files.firstOrNull()?.id ?: ""
            Log.i(TAG, "findSpreadsheetId result: '${result.files.firstOrNull()?.name}' id='$id' (${result.files.size} files found)")
            id
        }.onFailure { e ->
            Log.e(TAG, "findSpreadsheetId exception: ${e.message}", e)
        }.getOrDefault("")
    }

    private fun isExpiredSoon(expiry: String): Boolean {
        if (expiry.isBlank()) return true
        return runCatching {
            Instant.now().isAfter(Instant.parse(expiry).minusSeconds(300))
        }.getOrDefault(true)
    }

    private fun expiryInOneHour(): String =
        Instant.now().plusSeconds(3600).toString()

    companion object {
        private const val TAG = "GoogleAuthRepository"
    }
}
