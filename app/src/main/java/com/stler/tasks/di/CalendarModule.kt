package com.stler.tasks.di

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.stler.tasks.data.remote.CalendarApi
import com.stler.tasks.data.remote.dto.EventDateTime
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

/**
 * Custom Gson TypeAdapter for [EventDateTime] that correctly handles the mutually-exclusive
 * `date` / `dateTime` fields required by the Google Calendar API v3.
 *
 * **Write (serialization)**: null fields are silently omitted (Gson default, `serializeNulls=false`).
 * This means a timed event produces `{"dateTime":"..."}` and an all-day event produces
 * `{"date":"..."}` — exactly what the API requires. Sending an explicit `"date":null` alongside
 * `"dateTime":"..."` causes 400 errors for recurring timed events even though the API accepts it
 * for non-recurring ones.
 *
 * **Read (deserialization)**: handles both the normal case (`"dateTime":"..."`) and the edge case
 * where the server returns `"date":null` or `"dateTime":null` as explicit JSON nulls.
 */
private class EventDateTimeAdapter : TypeAdapter<EventDateTime>() {
    override fun write(out: JsonWriter, value: EventDateTime?) {
        if (value == null) { out.nullValue(); return }
        // Do NOT enable serializeNulls here. With PUT semantics, a null field means
        // "this field is absent" — i.e. Gson simply omits it from the JSON body.
        // Google Calendar API requires the start/date and start/dateTime fields to be
        // mutually exclusive and fully absent (not null-valued) when not applicable,
        // especially for recurring timed events. Explicit "date": null in the request
        // body causes 400 errors for recurring timed events even though the API accepts
        // it for non-recurring ones.
        //
        // Result (default serializeNulls = false):
        //   timed event:    {"dateTime": "2026-05-04T10:00:00+03:00"}  — date field absent ✓
        //   all-day event:  {"date": "2026-05-04"}                     — dateTime field absent ✓
        out.beginObject()
        out.name("date").value(value.date)         // null → field is silently omitted (correct)
        out.name("dateTime").value(value.dateTime) // null → field is silently omitted (correct)
        out.name("timeZone").value(value.timeZone) // null → omitted for all-day; set for timed recurring
        out.endObject()
    }

    override fun read(`in`: JsonReader): EventDateTime {
        var date: String? = null
        var dateTime: String? = null
        var timeZone: String? = null
        `in`.beginObject()
        while (`in`.hasNext()) {
            when (`in`.nextName()) {
                "date"     -> date     = if (`in`.peek() == JsonToken.NULL) { `in`.nextNull(); null } else `in`.nextString()
                "dateTime" -> dateTime = if (`in`.peek() == JsonToken.NULL) { `in`.nextNull(); null } else `in`.nextString()
                "timeZone" -> timeZone = if (`in`.peek() == JsonToken.NULL) { `in`.nextNull(); null } else `in`.nextString()
                else       -> `in`.skipValue()
            }
        }
        `in`.endObject()
        return EventDateTime(date = date, dateTime = dateTime, timeZone = timeZone)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object CalendarModule {

    private const val CALENDAR_BASE_URL = "https://www.googleapis.com/"

    /**
     * Separate Retrofit instance for the Calendar API.
     * Reuses the same [OkHttpClient] (with Bearer-token interceptor + 401 authenticator)
     * provided by [NetworkModule], but with a different base URL.
     *
     * Uses a custom [EventDateTimeAdapter] so that [EventDateTime]'s mutually-exclusive
     * `date` / `dateTime` fields are serialized correctly: only the non-null field is written,
     * ensuring the Google Calendar API receives exactly one of the two (never both).
     */
    @Provides
    @Singleton
    @Named("calendar")
    fun provideCalendarRetrofit(client: OkHttpClient, gson: Gson): Retrofit {
        val calendarGson = gson.newBuilder()
            .registerTypeAdapter(EventDateTime::class.java, EventDateTimeAdapter())
            .create()
        return Retrofit.Builder()
            .baseUrl(CALENDAR_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(calendarGson))
            .build()
    }

    @Provides
    @Singleton
    fun provideCalendarApi(@Named("calendar") retrofit: Retrofit): CalendarApi =
        retrofit.create(CalendarApi::class.java)

    @Provides
    @Singleton
    fun provideCalendarRepository(impl: CalendarRepositoryImpl): CalendarRepository = impl
}
