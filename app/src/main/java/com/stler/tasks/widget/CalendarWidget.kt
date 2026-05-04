package com.stler.tasks.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import com.stler.tasks.domain.model.CalendarEvent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/** Row model for the CalendarWidget's date-grouped event list. */
private sealed class CalendarWidgetRow {
    data class Header(val text: String, val isOverdue: Boolean = false) : CalendarWidgetRow()
    data class Event(val event: CalendarEvent) : CalendarWidgetRow()
}

class CalendarWidget : GlanceAppWidget() {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

        // Per-widget calendar selection; falls back to globally selected if empty
        val widgetCalendarIds = WidgetPrefs.getCalendarWidgetIds(context, appWidgetId)
        val widgetName        = WidgetPrefs.getCalendarWidgetName(context, appWidgetId)
            .ifBlank { "Calendar" }

        val ep           = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val calendarRepo = ep.calendarRepository()

        val from = LocalDate.now().minusDays(1)
        val to   = LocalDate.now().plusDays(30)

        // If per-widget IDs are configured use them directly (no Flow needed);
        // otherwise switch on globally-selected calendar IDs.
        val eventsFlow: Flow<List<CalendarEvent>> =
            if (widgetCalendarIds.isNotEmpty()) {
                calendarRepo.getEventsForCalendars(widgetCalendarIds, from, to)
            } else {
                calendarRepo.getSelectedCalendarIds().flatMapLatest { ids ->
                    if (ids.isEmpty()) flowOf(emptyList())
                    else calendarRepo.getEventsForCalendars(ids, from, to)
                }
            }

        val title     = widgetName
        val headerUri = if (widgetCalendarIds.size == 1) {
            "stlertasks://calendar/${widgetCalendarIds.first()}"
        } else {
            "stlertasks://upcoming"
        }

        provideContent {
            val allEvents by eventsFlow.collectAsState(initial = emptyList())

            val today  = LocalDate.now()
            val cutoff = today.plusDays(29)   // 30-day window

            // Sort events by date then time
            data class SortableEvent(
                val date   : LocalDate,
                val hasTime: Boolean,
                val time   : String,
                val event  : CalendarEvent,
            )

            val sorted: List<SortableEvent> = buildList {
                allEvents.forEach { event ->
                    val date = runCatching { LocalDate.parse(event.startDate) }.getOrNull()
                        ?: return@forEach
                    if (date > cutoff) return@forEach
                    add(SortableEvent(
                        date    = date,
                        hasTime = event.startTime.isNotBlank(),
                        time    = event.startTime,
                        event   = event,
                    ))
                }
            }.sortedWith(compareBy({ it.date }, { if (it.hasTime) 0 else 1 }, { it.time }))

            // Build header-per-date row list
            val rows: List<CalendarWidgetRow> = buildList {
                val overdue  = sorted.filter { it.date < today }
                val upcoming = sorted.filter { it.date >= today }

                if (overdue.isNotEmpty()) {
                    add(CalendarWidgetRow.Header("Overdue", isOverdue = true))
                    overdue.forEach { add(CalendarWidgetRow.Event(it.event)) }
                }

                upcoming
                    .groupBy { it.date }
                    .forEach { (date, entries) ->
                        add(CalendarWidgetRow.Header(formatDateHeader(date, today)))
                        entries.forEach { add(CalendarWidgetRow.Event(it.event)) }
                    }
            }

            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(WSurface),
                ) {
                    WidgetHeader(title = title, screenUri = headerUri)
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(rows.take(40), itemId = { row ->
                            when (row) {
                                is CalendarWidgetRow.Header -> "h_${row.text}".hashCode().toLong()
                                is CalendarWidgetRow.Event  -> "e_${row.event.id}".hashCode().toLong()
                            }
                        }) { row ->
                            when (row) {
                                is CalendarWidgetRow.Header -> DateHeader(
                                    text      = row.text,
                                    isOverdue = row.isOverdue,
                                )
                                is CalendarWidgetRow.Event  -> WidgetEventRow(
                                    event           = row.event,
                                    showExpandSpace = false,
                                    timeOnly        = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()

    override fun onUpdate(
        context: android.content.Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val ready = appWidgetIds.filter { WidgetPrefs.isConfigured(context, it) }.toIntArray()
        if (ready.isNotEmpty()) super.onUpdate(context, appWidgetManager, ready)
    }
}
