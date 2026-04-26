package com.stler.tasks.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.stler.tasks.data.repository.CalendarRepository
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val calendarRepository: CalendarRepository,
) : BaseViewModel() {

    private val calendarId: String = savedStateHandle.get<String>("calendarId") ?: ""

    private val from: LocalDate = LocalDate.now()
    private val to:   LocalDate = LocalDate.now().plusDays(60)

    val groupedEvents: StateFlow<Map<LocalDate, List<CalendarEvent>>> =
        calendarRepository.getEventsForCalendars(setOf(calendarId), from, to)
            .map { events -> groupByDate(events) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** True until the first emission, then false. */
    val isLoading: StateFlow<Boolean> = groupedEvents
        .map { false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun groupByDate(events: List<CalendarEvent>): Map<LocalDate, List<CalendarEvent>> {
        val today = LocalDate.now()
        return events
            .filter { runCatching { LocalDate.parse(it.startDate) }.isSuccess }
            .groupBy { event ->
                val date = LocalDate.parse(event.startDate)
                if (date < today) LocalDate.MIN else date
            }
            .toSortedMap()
    }
}
