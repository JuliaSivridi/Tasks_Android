# Google Calendar Integration — Architecture Spec

**Created:** 2026-04-26
**Branch:** `feature/google-calendar`
**Base:** `main` (Settings screen already present)

---

## 0. Pre-requisite (manual, outside code)
- Google Cloud Console → enable **Google Calendar API** for the project
- Add scope `https://www.googleapis.com/auth/calendar` to the OAuth consent screen
- Add the scope to `GoogleAuthRepository.buildAuthRequest()` alongside Sheets + Drive scopes

---

## 1. Data Layer

### 1.1 Domain Models

**`domain/model/CalendarItem.kt`**
```kotlin
data class CalendarItem(
    val id: String,          // Google Calendar ID (e.g. "primary")
    val summary: String,     // calendar display name
    val color: String,       // hex (backgroundColor from API, e.g. "#4285f4")
    val isSelected: Boolean, // user preference (from CalendarPreferences)
)
```

**`domain/model/CalendarEvent.kt`**
```kotlin
data class CalendarEvent(
    val id: String,
    val calendarId: String,
    val calendarName: String,
    val calendarColor: String,  // hex
    val title: String,
    val startDate: String,      // "YYYY-MM-DD"
    val startTime: String,      // "HH:MM" or "" for all-day
    val endDate: String,
    val endTime: String,
    val isAllDay: Boolean,
)
```

**`domain/model/ListItem.kt`** (unified list item for Upcoming / AllTasks)
```kotlin
sealed class ListItem {
    data class TaskItem(val task: Task) : ListItem()
    data class EventItem(val event: CalendarEvent) : ListItem()
}
```

### 1.2 Room

**`data/local/entity/CalendarEventEntity.kt`**
```kotlin
@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val calendarId: String,
    val calendarName: String,
    val calendarColor: String,
    val title: String,
    val startDate: String,
    val startTime: String,
    val endDate: String,
    val endTime: String,
    val isAllDay: Boolean,
)
```

**`data/local/dao/CalendarEventDao.kt`**
```kotlin
@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events WHERE calendarId IN (:calendarIds)")
    fun observeByCalendars(calendarIds: List<String>): Flow<List<CalendarEventEntity>>

    @Upsert
    suspend fun upsertAll(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE calendarId = :calendarId")
    suspend fun deleteByCalendar(calendarId: String)

    @Query("DELETE FROM calendar_events")
    suspend fun deleteAll()
}
```

**`data/local/TaskDatabase.kt`** — version bump 4 → 5; add `CalendarEventEntity` to entities list; add `MIGRATION_4_5`:
```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE calendar_events (
                id TEXT NOT NULL PRIMARY KEY,
                calendarId TEXT NOT NULL,
                calendarName TEXT NOT NULL,
                calendarColor TEXT NOT NULL,
                title TEXT NOT NULL,
                startDate TEXT NOT NULL,
                startTime TEXT NOT NULL,
                endDate TEXT NOT NULL,
                endTime TEXT NOT NULL,
                isAllDay INTEGER NOT NULL
            )
        """)
    }
}
```
Use `addMigrations(MIGRATION_4_5)` in `databaseBuilder` (NOT `fallbackToDestructiveMigration`) to preserve existing tasks/folders/labels.

**`auth/AuthPreferences.kt`** — add to existing `auth_prefs` DataStore:
```kotlin
private val SELECTED_CALENDAR_IDS = stringSetPreferencesKey("selected_calendar_ids")

val selectedCalendarIds: Flow<Set<String>> =
    context.authDataStore.data.map { it[SELECTED_CALENDAR_IDS] ?: emptySet() }

suspend fun saveSelectedCalendarIds(ids: Set<String>) {
    context.authDataStore.edit { it[SELECTED_CALENDAR_IDS] = ids }
}
```
Also extend `clearAll()` to clear `SELECTED_CALENDAR_IDS` on sign-out.

### 1.3 Remote

**`data/remote/dto/CalendarDtos.kt`** — all Calendar API DTOs in one file:
```kotlin
data class CalendarListResponse(val items: List<CalendarListEntry> = emptyList())
data class CalendarListEntry(val id: String, val summary: String, val backgroundColor: String = "")

data class CalendarEventsResponse(val items: List<CalendarEventDto> = emptyList())
data class CalendarEventDto(
    val id: String,
    val summary: String? = null,
    val start: EventDateTime,
    val end: EventDateTime,
)
data class EventDateTime(
    val dateTime: String? = null,  // RFC3339 for timed events, e.g. "2026-04-26T14:00:00+03:00"
    val date: String? = null,      // "YYYY-MM-DD" for all-day events
)

// Request body for event creation
data class CalendarEventRequest(
    val summary: String,
    val start: EventDateTime,
    val end: EventDateTime,
)
```

**`data/remote/api/CalendarApi.kt`** (Retrofit interface, base URL `https://www.googleapis.com/`):
```kotlin
interface CalendarApi {
    @GET("calendar/v3/users/me/calendarList")
    suspend fun listCalendars(): CalendarListResponse

    @GET("calendar/v3/calendars/{calendarId}/events")
    suspend fun listEvents(
        @Path("calendarId") calendarId: String,
        @Query("timeMin") timeMin: String,          // RFC3339
        @Query("timeMax") timeMax: String,          // RFC3339
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("orderBy") orderBy: String = "startTime",
    ): CalendarEventsResponse

    @POST("calendar/v3/calendars/{calendarId}/events")
    suspend fun createEvent(
        @Path("calendarId") calendarId: String,
        @Body event: CalendarEventRequest,
    ): CalendarEventDto
}
```

**`data/remote/mapper/CalendarMapper.kt`**

Mapping responsibilities:
- `CalendarListEntry → CalendarItem`: set `isSelected` from the saved `Set<String>` of selected IDs
- `CalendarEventDto → CalendarEventEntity`: parse RFC3339 `dateTime` (extract date `YYYY-MM-DD`, time `HH:MM`) or plain `date` for all-day; `isAllDay = dateTime == null`; `summary ?: "(No title)"`
- `CalendarEventEntity → CalendarEvent`: trivial field copy

RFC3339 parse helper (example):
```kotlin
fun parseRfc3339Date(dateTime: String): String = dateTime.substring(0, 10)   // "YYYY-MM-DD"
fun parseRfc3339Time(dateTime: String): String = dateTime.substring(11, 16)  // "HH:MM"
```

### 1.4 Repository

**`data/repository/CalendarRepository.kt`** (interface):
```kotlin
interface CalendarRepository {
    /** Room-backed Flow; filtered by date range in-memory after emission. */
    fun getEventsForCalendars(calendarIds: Set<String>, from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>>

    /** DataStore-backed Flow of selected calendar IDs. */
    fun getSelectedCalendarIds(): Flow<Set<String>>

    /**
     * Fetches calendar list from API; returns mapped CalendarItem list (with isSelected).
     * Does NOT persist calendars to Room (transient — calendars change rarely).
     */
    suspend fun fetchCalendarsAndSave(): List<CalendarItem>

    /**
     * Fetches events from API for each calendarId, upserts into Room.
     * Clears stale events for each calendar before inserting fresh data.
     */
    suspend fun fetchEventsAndSave(calendarIds: Set<String>, from: LocalDate, to: LocalDate)

    /** Creates a Calendar event via API. */
    suspend fun createEvent(calendarId: String, event: CalendarEventRequest): Result<CalendarEvent>

    /** Persists selected calendar ID set to DataStore. */
    suspend fun saveSelectedCalendarIds(ids: Set<String>)
}
```

**`data/repository/CalendarRepositoryImpl.kt`** — inject `CalendarApi`, `CalendarEventDao`, `AuthPreferences`:

```kotlin
// getEventsForCalendars:
calendarEventDao.observeByCalendars(calendarIds.toList())
    .map { entities ->
        entities
            .map { it.toDomain() }
            .filter { event ->
                val d = LocalDate.parse(event.startDate)
                d >= from && d <= to
            }
    }

// fetchEventsAndSave — per calendarId:
calendarEventDao.deleteByCalendar(calendarId)
val response = calendarApi.listEvents(calendarId, timeMin, timeMax)
calendarEventDao.upsertAll(response.items.map { it.toEntity(calendarId, calendarName, calendarColor) })

// fetchCalendarsAndSave:
val selectedIds = authPreferences.selectedCalendarIds.first()
calendarApi.listCalendars().items.map { entry ->
    entry.toDomain(isSelected = entry.id in selectedIds)
}
```

---

## 2. Sync

**`sync/SyncWorker.kt`** — add Calendar fetch phase after the existing pull phase:

```
// After: taskRepository.fetchAllAndSave(spreadsheetId)

// Calendar sync phase
val selectedIds = authPreferences.selectedCalendarIds.first()
if (selectedIds.isNotEmpty()) {
    runCatching {
        val from = LocalDate.now().minusDays(1)
        val to   = LocalDate.now().plusDays(60)
        calendarRepository.fetchEventsAndSave(selectedIds, from, to)
        Log.i(TAG, "Calendar sync done for ${selectedIds.size} calendars")
    }.onFailure { e ->
        Log.e(TAG, "Calendar sync failed: ${e.message}", e)
        // Do NOT fail the worker — calendar sync is best-effort
    }
}
```

Inject `CalendarRepository` and `AuthPreferences` into `SyncWorker` via `@AssistedInject`.

---

## 3. Settings Screen — Calendar Selection

Extend **`SettingsScreen.kt`** with a new section below "SPREADSHEET":

```
Text("CALENDARS", labelSmall, onSurfaceVariant)          // section label

OutlinedCard(fillMaxWidth, padding horizontal 12dp):
  // Header row: title + refresh icon button
  Row:
    Text("Google Calendars", bodyMedium, weight=1f)
    IconButton(onClick = viewModel::loadCalendars):
      Icon(Icons.Outlined.Refresh)

  HorizontalDivider()

  // Body
  when:
    calendarsLoading → Row: CircularProgressIndicator(16dp) + Text("Loading…")
    calendars.isEmpty() → Text("No calendars found. Tap refresh to load.", padding 16dp)
    else → forEach calendar:
        Row(fillMaxWidth, verticalCenter, padding h=16dp v=12dp):
          Icon(Icons.Outlined.CalendarMonth, tint=Color(calendar.color), size=20dp)
          Spacer(12dp)
          Text(calendar.summary, bodyMedium, weight=1f)
          Checkbox(
              checked = calendar.isSelected,
              onCheckedChange = { viewModel.toggleCalendar(calendar.id, it) }
          )
```

Extend **`SettingsViewModel.kt`**:
```kotlin
private val _calendars      = MutableStateFlow<List<CalendarItem>>(emptyList())
private val _calendarsLoading = MutableStateFlow(false)

val calendars:        StateFlow<List<CalendarItem>> = _calendars.asStateFlow()
val calendarsLoading: StateFlow<Boolean>             = _calendarsLoading.asStateFlow()

fun loadCalendars() {
    viewModelScope.launch {
        _calendarsLoading.value = true
        try { _calendars.value = calendarRepository.fetchCalendarsAndSave() }
        catch (e: Exception) { Log.e(TAG, "loadCalendars", e) }
        finally { _calendarsLoading.value = false }
    }
}

fun toggleCalendar(id: String, selected: Boolean) {
    viewModelScope.launch {
        val current = authPreferences.selectedCalendarIds.first().toMutableSet()
        if (selected) current.add(id) else current.remove(id)
        calendarRepository.saveSelectedCalendarIds(current)
        // Refresh local list to update isSelected
        _calendars.update { list -> list.map { if (it.id == id) it.copy(isSelected = selected) else it } }
    }
}
```

---

## 4. Sidebar — Calendars Section

**`ui/main/SidebarPreferences.kt`** — add `calendarsOpen: Boolean = true` to `SidebarState` data class.

**`ui/main/SidebarMenu.kt`** — add "Calendars" section after Labels, using identical collapsible-section pattern:
```kotlin
// Section header (same pattern as foldersOpen / labelsOpen)
SidebarSectionHeader(
    title        = "Calendars",
    isOpen       = sidebarState.calendarsOpen,
    onToggle     = { onToggleSection(SidebarSection.CALENDARS) },
    onAdd        = null,  // no add button — calendars come from Google
)

// Items — only selected calendars
AnimatedVisibility(visible = sidebarState.calendarsOpen) {
    Column {
        selectedCalendars.forEach { cal ->
            NavigationDrawerItem(
                icon   = { Icon(Icons.Outlined.CalendarMonth, tint = Color(cal.color), modifier = Modifier.size(16.dp), contentDescription = null) },
                label  = { Text(cal.summary) },
                selected = currentRoute == Screen.CALENDAR && currentCalendarId == cal.id,
                onClick  = { onNavigate(Screen.calendarRoute(cal.id)); drawerClose() },
            )
        }
    }
}
```

**`ui/navigation/Screen.kt`**:
```kotlin
const val CALENDAR = "calendar/{calendarId}"
fun calendarRoute(calendarId: String) = "calendar/$calendarId"
```

**`ui/main/MainScreen.kt`**:
- Extract `currentCalendarId` from back stack entry arguments (same pattern as `currentFolderId`)
- Add `composable(Screen.CALENDAR, arguments = listOf(navArgument("calendarId") { type = NavType.StringType }))` → `CalendarScreen(calendarId)`
- Pass `selectedCalendars` and `currentCalendarId` to `SidebarMenu`

**`ui/main/MainViewModel.kt`**:
```kotlin
// Combine selected IDs with a locally-cached calendar list
// Simple approach: expose selectedCalendarIds Flow + a cached list loaded on init
val selectedCalendars: StateFlow<List<CalendarItem>> = ...
```
On init: load calendars from API if IDs are non-empty; combine with selectedCalendarIds from DataStore.

---

## 5. CalendarScreen

**`ui/calendar/CalendarViewModel.kt`**
```kotlin
@HiltViewModel
class CalendarViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val calendarRepository: CalendarRepository,
) : BaseViewModel() {
    private val calendarId: String = savedStateHandle["calendarId"] ?: ""

    private val from = LocalDate.now().minusDays(1)
    private val to   = LocalDate.now().plusDays(60)

    val groupedEvents: StateFlow<Map<LocalDate, List<CalendarEvent>>> =
        calendarRepository.getEventsForCalendars(setOf(calendarId), from, to)
            .map { events -> groupByDate(events) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val isLoading: StateFlow<Boolean> = groupedEvents
        .map { false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}
```

Grouping logic (same as UpcomingViewModel):
- `LocalDate.MIN` bucket → rendered as "Overdue"
- Today, Tomorrow, This Week (Mon–Sun of current week), Later

**`ui/calendar/CalendarScreen.kt`**
- Collects `groupedEvents` and `isLoading`
- `isLoading` → `ShimmerTaskList`
- `groupedEvents.isEmpty()` → `EmptyState(icon=CalendarMonth, message="No events", subtitle="Events will appear after sync")`
- LazyColumn:
  - For each date key → `DateHeader(date)` (reuse existing composable from UpcomingScreen)
  - For each event → `CalendarEventItem(event)`

**`ui/calendar/CalendarEventItem.kt`** (standalone, reused in Upcoming/AllTasks):
```kotlin
@Composable
fun CalendarEventItem(event: CalendarEvent) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.bodyLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val color = deadlineStatusColor(event.startDate)   // from TaskColors.kt
                Text(formatEventDateTime(event), style = bodySmall, color = color)
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = Color(android.graphics.Color.parseColor(event.calendarColor)),
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    event.calendarName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

`formatEventDateTime`: if `isAllDay` → show date only; else show time on Upcoming (when date = today), show date + time elsewhere.

---

## 6. Upcoming and AllTasks — Events in List

### ListItem sealed class

**`domain/model/ListItem.kt`** (new file):
```kotlin
sealed class ListItem {
    data class TaskItem(val task: Task) : ListItem()
    data class EventItem(val event: CalendarEvent) : ListItem()
}
```

### UpcomingViewModel

New injected dependency: `CalendarRepository`.

```kotlin
private val selectedCalendarIds: Flow<Set<String>> = calendarRepository.getSelectedCalendarIds()

// Events flow — switches source whenever selected IDs change
private val eventsFlow: Flow<List<CalendarEvent>> =
    selectedCalendarIds.flatMapLatest { ids ->
        if (ids.isEmpty()) flowOf(emptyList())
        else calendarRepository.getEventsForCalendars(ids, LocalDate.now().minusDays(1), LocalDate.now().plusDays(60))
    }

// allGroupedTasks now returns Map<LocalDate, List<ListItem>>
val allGroupedTasks: StateFlow<Map<LocalDate, List<ListItem>>> =
    combine(tasksWithDeadline, eventsFlow, _priorityFilter, _labelFilter, _folderFilter) {
        tasks, events, priorityFilter, labelFilter, folderFilter ->
        // filter tasks as before
        val filteredTasks = tasks.filter { ... }
        // merge: tasks → ListItem.TaskItem, events → ListItem.EventItem
        val allItems: List<ListItem> = filteredTasks.map { ListItem.TaskItem(it) } +
            events.map { ListItem.EventItem(it) }
        // group by date
        allItems.groupByDate()   // private extension using task.deadlineDate / event.startDate
    }.stateIn(...)
```

Sorting within each date bucket: tasks and events interleaved by time (tasks with no time go first within their priority order; all-day events go first).

### UpcomingScreen

```kotlin
// In the LazyColumn items block, replace:
//   TaskItem(task = item, ...)
// with:
when (val listItem = item) {
    is ListItem.TaskItem  -> TaskItem(task = listItem.task, ...)
    is ListItem.EventItem -> CalendarEventItem(event = listItem.event)
}
```

### AllTasksViewModel + AllTasksScreen

Same changes as Upcoming, but without the week strip and date grouping — events are included in the flat list, interleaved by date+time with tasks.

---

## 7. TaskFormSheet — Calendar Event Creation

> **Design review required before implementation (task 12.6.1).**

Proposed UX: when `deadlineDate` is set, show additional fields below the Deadline row:

| Field | Control | Default | Visibility |
|-------|---------|---------|------------|
| Duration | `ExposedDropdownMenuBox`: Not set / 15 min / 30 min / 1 h / 2 h / All day | Not set | Always when deadline is set |
| Add to Calendar | `Switch` | Off | Always when deadline is set |
| Calendar | `ExposedDropdownMenuBox` (selected calendars only) | first selected | Only when Switch = on |

On save: if switch = on → `calendarRepository.createEvent(calendarId, ...)` after task is saved; error → `uiError` snackbar.

---

## 8. DI

**`di/CalendarModule.kt`** (new Hilt module):
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object CalendarModule {

    /** Separate Retrofit instance for Calendar API (different base URL). */
    @Provides @Singleton @Named("calendar")
    fun provideCalendarRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(okHttpClient)   // reuse the shared OkHttpClient with Bearer interceptor
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides @Singleton
    fun provideCalendarApi(@Named("calendar") retrofit: Retrofit): CalendarApi =
        retrofit.create(CalendarApi::class.java)

    @Provides @Singleton
    fun provideCalendarRepository(impl: CalendarRepositoryImpl): CalendarRepository = impl
}
```

**`di/NetworkModule.kt`** — expose the shared `OkHttpClient` with `@Provides @Singleton` so `CalendarModule` can inject it. Currently the client is created inline; extract it to a named `@Provides` method.

---

## 9. Scope Exclusions

- **Widgets** — calendar events not shown in Upcoming/Folder/TaskList widgets (separate future scope)
- **Recurring events** — `singleEvents=true` in API call; Google expands recurrences server-side
- **Edit/delete calendar events** — read + create only; editing/deleting existing events is out of scope
- **CalendarScreen in Completed** — events have no "completed" state; Completed screen remains tasks-only
