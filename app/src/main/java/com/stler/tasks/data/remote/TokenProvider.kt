package com.stler.tasks.data.remote

/**
 * Provides OAuth 2.0 access tokens to the OkHttp interceptor.
 * Implemented by GoogleAuthRepository (Stage 3).
 * Bound to StubTokenProvider for Stage 2 so the build compiles.
 */
interface TokenProvider {
    /** Returns the current access token (from DataStore). */
    suspend fun getAccessToken(): String

    /**
     * Refreshes the token and returns the new one.
     * Returns null if refresh fails (triggers sign-out in Stage 3).
     */
    suspend fun refreshToken(): String?
}
