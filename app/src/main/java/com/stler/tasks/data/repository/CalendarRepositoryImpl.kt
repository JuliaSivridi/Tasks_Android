package com.stler.tasks.data.repository

import android.util.Log
import com.stler.tasks.auth.AuthPreferences
import com.stler.tasks.data.local.dao.CalendarEventDao
import com.stler.tasks.data.remote.CalendarApi
import com.stler.tasks.data.remote.CalendarMapper
import com.stler.tasks.data.remote.dto.CalendarEventRequest
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.domain.model.CalendarItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val calendarApi: CalendarApi,
    private val calendarEventDao: CalendarEventDao,
    private val authPreferences: AuthPreferences,
) : CalendarRepository {

    // ── In-memory calendar metadata cache ────────────────────────────────────
    // Avoids an extra listCalendars() call on every createEvent / updateEvent / moveEvent.
    // TTL: 5 minutes. Populated also by fetchEventsAndSave() which already fetches the list.
    private var calendarMetaCache: Map<String, Pair<String, String>> = emptyMap()
    private var calendarMetaCacheTime: Long = 0L

    override fun getAllEvents(): Flow<List<CalendarEvent>> =
        calendarEventDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun moveEvent(
        fromCalendarId: String,
        toCalendarId  : String,
        eventId       : String,
    ): Result<CalendarEvent> = runCatching {
        val dto  = calendarApi.moveEvent(fromCalendarId, eventId, toCalendarId)
        val meta = calendarMeta(toCalendarId)
        val entity = CalendarMapper.dtoToEntity(dto, toCalendarId, meta.first, meta.second)
        // Atomically remove all Room entries for this event/series from the old calendar
        // and insert the moved entity — @Transaction prevents a window with zero events.
        calendarEventDao.deleteSeriesAndReplace(eventId, entity)
        entity?.toDomain() ?: throw IllegalStateException("Failed to map moved event")
    }

    override fun getEventsForCalendars(
        calendarIds: Set<String>,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<CalendarEvent>> {
        if (calendarIds.isEmpty()) return flowOf(emptyList())
        return calendarEventDao.observeByCalendars(calendarIds.toList())
            .map { entities ->
                entities
                    .filter { entity ->
                        runCatching {
                            val d = LocalDate.parse(entity.startDate)
                            d >= from && d <= to
                        }.getOrDefault(false)
                    }
                    .map { it.toDomain() }
            }
    }

    override fun getSelectedCalendarIds(): Flow<Set<String>> =
        authPreferences.selectedCalendarIds

    override suspend fun fetchCalendarsAndSave(): List<CalendarItem> {
        val selectedIds = authPreferences.selectedCalendarIds.first()
        return runCatching {
            calendarApi.listCalendars().items.map { entry ->
                CalendarItem(
                    id         = entry.id,
                    summary    = entry.summary,
                    color      = entry.backgroundColor.ifBlank { "#4285f4" },
                    isSelected = entry.id in selectedIds,
                    accessRole = entry.accessRole,
                )
            }
        }.onFailure { e ->
            Log.e(TAG, "fetchCalendarsAndSave failed: ${e.message}", e)
        }.getOrDefault(emptyList())
    }

    override suspend fun fetchEventsAndSave(
        calendarIds: Set<String>,
        from: LocalDate,
        to: LocalDate,
    ) {
        val timeMin = CalendarMapper.toRfc3339(from)
        val timeMax = CalendarMapper.toRfc3339(to.plusDays(1))

        // Fetch calendar metadata (name + color).
        // Auth errors (401) propagate to the caller (SyncWorker) so the worker retries.
        // Per-calendar fetch errors are best-effort (logged, not re-thrown).
        val calendarMetaMap = mutableMapOf<String, Pair<String, String>>()
        calendarApi.listCalendars().items.forEach { entry ->
            calendarMetaMap[entry.id] = entry.summary to entry.backgroundColor
        }
        // Refresh the metadata cache while we have fresh data
        calendarMetaCache = calendarMetaMap.toMap()
        calendarMetaCacheTime = System.currentTimeMillis()

        for (calendarId in calendarIds) {
            runCatching {
                val (calName, calColor) = calendarMetaMap[calendarId] ?: (calendarId to "#4285f4")
                val response = calendarApi.listEvents(calendarId, timeMin, timeMax)
                val entities = response.items.mapNotNull { dto ->
                    CalendarMapper.dtoToEntity(dto, calendarId, calName, calColor)
                }
                // Atomically replace: delete old rows AFTER fetch succeeds to avoid
                // losing data if the network call fails mid-loop.
                calendarEventDao.deleteAndReplace(calendarId, entities)
                Log.d(TAG, "Fetched ${entities.size} events for calendar '$calName'")
            }.onFailure { e ->
                Log.e(TAG, "fetchEventsAndSave failed for $calendarId: ${e.message}", e)
            }
        }
    }

    override suspend fun createEvent(
        calendarId: String,
        event: CalendarEventRequest,
    ): Result<CalendarEvent> = runCatching {
        val dto = calendarApi.createEvent(calendarId, event)
        val meta = calendarMeta(calendarId)
        val entity = CalendarMapper.dtoToEntity(
            dto           = dto,
            calendarId    = calendarId,
            calendarName  = meta.first,
            calendarColor = meta.second,
        ) ?: throw IllegalStateException("Failed to map created event")
        // Upsert first instance immediately for instant UI feedback
        calendarEventDao.upsertAll(listOf(entity))
        // Re-fetch the full calendar to pick up all recurring instances
        val isRecurring = event.recurrence?.isNotEmpty() == true
        if (isRecurring) {
            runCatching {
                fetchEventsAndSave(setOf(calendarId), DEFAULT_FROM, DEFAULT_TO)
            }.onFailure { e -> Log.w(TAG, "Post-create refresh failed: ${e.message}") }
        }
        entity.toDomain()
    }

    override suspend fun updateEvent(
        calendarId: String,
        eventId   : String,
        event     : CalendarEventRequest,
    ): Result<CalendarEvent> = runCatching {
        val dto  = calendarApi.updateEvent(calendarId, eventId, event)
        val meta = calendarMeta(calendarId)
        val entity = CalendarMapper.dtoToEntity(
            dto           = dto,
            calendarId    = calendarId,
            calendarName  = meta.first,
            calendarColor = meta.second,
        ) ?: throw IllegalStateException("Failed to map updated event")
        calendarEventDao.upsertAll(listOf(entity))
        // Re-fetch to get all updated recurring instances
        runCatching {
            fetchEventsAndSave(setOf(calendarId), DEFAULT_FROM, DEFAULT_TO)
        }.onFailure { e -> Log.w(TAG, "Post-update refresh failed: ${e.message}") }
        entity.toDomain()
    }

    override suspend fun deleteEvent(
        calendarId: String,
        eventId   : String,
    ): Result<Unit> = runCatching {
        val response = calendarApi.deleteEvent(calendarId, eventId)
        if (!response.isSuccessful && response.code() != 410) {  // 410 = already deleted
            throw retrofit2.HttpException(response)
        }
        calendarEventDao.deleteById(eventId)
    }

    override suspend fun deleteEventSeries(
        calendarId: String,
        seriesId  : String,
    ): Result<Unit> = runCatching {
        val response = calendarApi.deleteEvent(calendarId, seriesId)
        if (!response.isSuccessful && response.code() != 410) {
            throw retrofit2.HttpException(response)
        }
        calendarEventDao.deleteBySeriesId(seriesId)
    }

    override suspend fun getBaseEvent(
        calendarId: String,
        seriesId  : String,
    ): Result<CalendarEventWithRRule> = runCatching {
        val dto  = calendarApi.getEvent(calendarId, seriesId)
        val meta = calendarMeta(calendarId)
        val entity = CalendarMapper.dtoToEntity(dto, calendarId, meta.first, meta.second)
            ?: throw IllegalStateException("Failed to map base event")
        val rrule = dto.recurrence?.firstOrNull { it.startsWith("RRULE:") } ?: ""
        // End time from the base event's end.dateTime
        val endTime = dto.end.dateTime
            ?.takeIf { it.length >= 16 }
            ?.substring(11, 16) ?: ""
        CalendarEventWithRRule(
            event   = entity.toDomain(),
            rrule   = rrule,
            endTime = endTime,
        )
    }

    override suspend fun saveSelectedCalendarIds(ids: Set<String>) {
        authPreferences.saveSelectedCalendarIds(ids)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns display name + color for [calendarId].
     * Uses the in-memory cache (TTL 5 min); falls back to a fresh listCalendars() call
     * only when the cache is stale or empty — avoids one extra API round-trip per
     * event mutation (create / update / move).
     */
    private suspend fun calendarMeta(calendarId: String): Pair<String, String> {
        val now = System.currentTimeMillis()
        if (now - calendarMetaCacheTime < CACHE_TTL_MS) {
            calendarMetaCache[calendarId]?.let { return it }
        }
        // Cache miss or stale — fetch and refresh
        return runCatching {
            val items = calendarApi.listCalendars().items
            calendarMetaCache = items.associate { it.id to (it.summary to it.backgroundColor) }
            calendarMetaCacheTime = System.currentTimeMillis()
            calendarMetaCache[calendarId] ?: (calendarId to "#4285f4")
        }.getOrDefault(calendarId to "#4285f4")
    }

    companion object {
        private const val TAG = "CalendarRepository"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L  // 5 minutes
        /** Default date range for post-create/update re-fetches (mirrors the ViewModels' range). */
        private val DEFAULT_FROM: LocalDate get() = LocalDate.now()
        private val DEFAULT_TO  : LocalDate get() = LocalDate.now().plusDays(366)
    }
}
