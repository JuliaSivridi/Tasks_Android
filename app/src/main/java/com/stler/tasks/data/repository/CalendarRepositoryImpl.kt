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

        // Fetch calendar metadata (name + color) for the given IDs
        val calendarMeta = mutableMapOf<String, Pair<String, String>>() // id → (name, color)
        runCatching {
            calendarApi.listCalendars().items.forEach { entry ->
                calendarMeta[entry.id] = entry.summary to entry.backgroundColor
            }
        }.onFailure { e ->
            Log.w(TAG, "Could not fetch calendar list for metadata: ${e.message}")
        }

        for (calendarId in calendarIds) {
            runCatching {
                val (calName, calColor) = calendarMeta[calendarId] ?: (calendarId to "#4285f4")
                val response = calendarApi.listEvents(calendarId, timeMin, timeMax)
                val entities = response.items.mapNotNull { dto ->
                    CalendarMapper.dtoToEntity(dto, calendarId, calName, calColor)
                }
                calendarEventDao.deleteByCalendar(calendarId)
                if (entities.isNotEmpty()) {
                    calendarEventDao.upsertAll(entities)
                }
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
        val meta = runCatching {
            calendarApi.listCalendars().items.find { it.id == calendarId }
        }.getOrNull()
        val entity = CalendarMapper.dtoToEntity(
            dto          = dto,
            calendarId   = calendarId,
            calendarName = meta?.summary ?: calendarId,
            calendarColor = meta?.backgroundColor ?: "#4285f4",
        ) ?: throw IllegalStateException("Failed to map created event")
        entity.toDomain()
    }

    override suspend fun saveSelectedCalendarIds(ids: Set<String>) {
        authPreferences.saveSelectedCalendarIds(ids)
    }

    companion object {
        private const val TAG = "CalendarRepository"
    }
}
