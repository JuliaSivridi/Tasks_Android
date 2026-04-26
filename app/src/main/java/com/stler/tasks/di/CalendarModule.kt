package com.stler.tasks.di

import com.google.gson.Gson
import com.stler.tasks.data.remote.CalendarApi
import com.stler.tasks.data.repository.CalendarRepository
import com.stler.tasks.data.repository.CalendarRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CalendarModule {

    private const val CALENDAR_BASE_URL = "https://www.googleapis.com/"

    /**
     * Separate Retrofit instance for the Calendar API.
     * Reuses the same [OkHttpClient] (with Bearer-token interceptor + 401 authenticator)
     * provided by [NetworkModule], but with a different base URL.
     */
    @Provides
    @Singleton
    @Named("calendar")
    fun provideCalendarRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(CALENDAR_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideCalendarApi(@Named("calendar") retrofit: Retrofit): CalendarApi =
        retrofit.create(CalendarApi::class.java)

    @Provides
    @Singleton
    fun provideCalendarRepository(impl: CalendarRepositoryImpl): CalendarRepository = impl
}
