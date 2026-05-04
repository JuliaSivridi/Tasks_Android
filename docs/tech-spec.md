# Stler Tasks Android — Technical Specification

**Version:** 2.1 (May 2026)  
**Repository:** github.com/JuliaSivridi/Tasks_Android  
**Stack:** Kotlin · Jetpack Compose · Room · Hilt · WorkManager · Glance · Google Sheets API v4 · Google Calendar API v3  
**Min SDK:** 26 (Android 8.0) · **Target SDK:** 36

---

## 1. Overview

Stler Tasks is a personal task manager for Android. It is a native rewrite of a PWA with the same Google Sheets backend, so both apps share one `db_tasks` spreadsheet per user. There is no dedicated backend server — all persistent task data lives in Google Sheets, accessed via the Sheets API v4. Room serves as a local cache that makes the app fully functional offline.

In addition to tasks, the app integrates with **Google Calendar API v3**: events from selected calendars are fetched, cached in Room, and displayed alongside tasks in the Upcoming screen, Calendar screen, and all three widgets.

**Key design goals:**
- Google Sheets as the single source of truth for tasks — no proprietary cloud service
- Full offline support via Room + sync queue
- Reactive UI — all screens observe Room Flows; data updates propagate automatically
- Clean MVVM + Repository architecture with Hilt DI throughout
- Glance widgets that react to Room changes without explicit refresh calls
- Google Calendar events surfaced inline with tasks, unified by date

---

## 2. Tech Stack

| Layer | Library | Version | Notes |
|---|---|---|---|
| Language | Kotlin | 2.2.10 | |
| UI | Jetpack Compose + Material 3 | BOM 2026.02.01 | Compose-only, no XML layouts |
| DI | Hilt | 2.59.2 | KSP processor |
| Local DB | Room | 2.7.1 | version 7, explicit migrations |
| Networking | Retrofit + OkHttp | 2.11.0 / 4.12.0 | Bearer token interceptor + 401 Authenticator |
| Serialization | Gson | 2.11.0 | For Retrofit + SyncQueue payloads |
| Background | WorkManager (Hilt) | 2.10.1 | Periodic sync (30 min) + one-off |
| Widgets | Glance | 1.1.0 | 4 widget types |
| Auth | CredentialManager + Identity API | 21.3.0 / 1.1.1 | Google Sign-In + scope authorization |
| Preferences | DataStore | 1.1.2 | Token, user info, spreadsheet ID |
| Image loading | Coil | 2.7.0 | Avatar in TopAppBar |
| Drag & drop | reorderable (Calvin) | 2.4.3 | FolderScreen drag-to-reorder |
| Coroutines | kotlinx.coroutines | 1.10.2 | Android + Play Services variants |
| Navigation | Navigation Compose | 2.9.0 | |
| Lifecycle | Lifecycle ViewModel/Runtime | 2.9.0 | |

**Build:** AGP 9.1.1 · KSP 2.2.10-2.0.2 · JVM toolchain 11

---

## 3. Architecture

**Pattern:** MVVM + Repository + Unidirectional Data Flow

```
UI (Composable)
    ↕ StateFlow / collectAsStateWithLifecycle
ViewModel (Hilt, ViewModelScope)
    ↕ suspend / Flow
Repository (Singleton)
    ↕ Room DAOs (Flow)     ↕ SyncQueue (write path)
Local DB (Room)            SyncWorker (background push + pull)
                               ↕ Retrofit / OkHttp
                           Google Sheets API v4
                           Google Calendar API v3
```

**Write path (tasks):** Every mutation → Room write (instant, triggers UI recomposition) + SyncQueue INSERT. SyncWorker drains the queue on next sync.

**Write path (calendar events):** Mutations (create / update / delete) go directly to the Calendar API via `CalendarRepository`. On success, Room is updated in place (no SyncQueue — Calendar events are not routed through Google Sheets).

**Read path:** Room Flow → ViewModel StateFlow → Composable. Sheets data is only read during sync pull (`fetchAllAndSave`); Calendar events are fetched by `SyncWorker` after the Sheets pull step.

**Error handling:** `BaseViewModel.safeLaunch` wraps all `viewModelScope.launch` calls, catches exceptions, and emits to a `Channel<String>` (`uiError` Flow). `ErrorSnackbarEffect` in each screen subscribes and shows a Snackbar via `LocalSnackbarHostState`.

---

## 4. Package Structure

```
com.stler.tasks/
├── auth/
│   ├── AuthData.kt               data class (email, name, avatar, spreadsheetId)
│   ├── AuthPreferences.kt        DataStore wrapper (token, expiry, user info, spreadsheetId)
│   ├── AuthScreen.kt             Sign-in UI (CredentialManager button + error display)
│   ├── AuthViewModel.kt          signIn / finalizeAuth / sign-out state
│   └── GoogleAuthRepository.kt   full auth flow + find-or-create spreadsheet
│
├── data/
│   ├── local/
│   │   ├── dao/                  TaskDao, FolderDao, LabelDao, SyncQueueDao,
│   │   │                         CalendarEventDao
│   │   ├── entity/               TaskEntity, FolderEntity, LabelEntity,
│   │   │                         SyncQueueEntity, CalendarEventEntity
│   │   └── TaskDatabase.kt       Room DB (version 7, 5 tables)
│   ├── remote/
│   │   ├── dto/                  BatchValuesResponse, ValuesBody, DriveFilesResponse,
│   │   │                         CalendarDtos (CalendarListResponse, CalendarEventDto,
│   │   │                         EventDateTime, CalendarEventRequest)
│   │   ├── CalendarApi.kt        Calendar API v3 endpoints (list, events, create, update,
│   │   │                         delete, move)
│   │   ├── CalendarMapper.kt     CalendarEventDto ↔ CalendarEventEntity conversion
│   │   ├── NetworkModule.kt      Hilt: OkHttpClient (interceptor + authenticator) + Retrofit
│   │   ├── SheetsApi.kt          batchGet / append / batchUpdate / clear
│   │   ├── SheetsMapper.kt       row ↔ entity bidirectional conversion
│   │   └── TokenProvider.kt      interface for token retrieval
│   └── repository/
│       ├── CalendarRepository.kt     interface
│       ├── CalendarRepositoryImpl.kt fetchCalendarsAndSave, getEventsForCalendars,
│       │                             createEvent, updateEvent, deleteEvent, moveEvent,
│       │                             getBaseEvent, getSelectedCalendarIds
│       ├── TaskRepository.kt         interface
│       └── TaskRepositoryImpl.kt     full implementation (CRUD, Flows, fetchAllAndSave)
│
├── di/
│   ├── AuthModule.kt             provides GoogleAuthRepository as TokenProvider
│   ├── CalendarModule.kt         provides Calendar-specific Retrofit (custom EventDateTime
│   │                             TypeAdapter), CalendarApi, CalendarRepository
│   ├── DatabaseModule.kt         provides Room DB + all DAOs
│   └── RepositoryModule.kt       binds TaskRepositoryImpl
│
├── domain/model/
│   ├── CalendarEvent.kt          domain model (id, calendarId, calendarName, calendarColor,
│   │                             title, startDate, startTime, endTime, isAllDay, recurringEventId,
│   │                             seriesId, computed isRecurring)
│   ├── CalendarItem.kt           calendar list entry (id, summary, color, isSelected, accessRole)
│   ├── Folder.kt
│   ├── Label.kt
│   ├── ListItem.kt               sealed class for mixed task/event lists
│   ├── Priority.kt               enum: URGENT, IMPORTANT, NORMAL
│   ├── RecurType.kt              enum: DAILY, WEEKLY, MONTHLY
│   ├── Task.kt                   domain model
│   └── TaskStatus.kt             enum: PENDING, COMPLETED
│
├── sync/
│   ├── NetworkObserver.kt        callbackFlow wrapping ConnectivityManager
│   ├── SyncManager.kt            schedules periodic + one-off WorkManager requests
│   ├── SyncState.kt              sealed class: Idle / Syncing / Pending(count)
│   └── SyncWorker.kt             @HiltWorker: push queue → pull Sheets → pull Calendar
│
├── ui/
│   ├── alltasks/                 AllTasksScreen + AllTasksViewModel
│   ├── calendar/                 CalendarScreen, CalendarViewModel, CalendarEventItem
│   ├── completed/                CompletedScreen + CompletedViewModel
│   ├── folder/                   FolderScreen + FolderViewModel + DisplayNode
│   ├── label/                    LabelScreen + LabelViewModel
│   ├── main/                     MainScreen, MainViewModel, SidebarMenu,
│   │                             SidebarPreferences, SidebarState, TasksTopAppBar
│   ├── navigation/               Screen.kt (route constants + helper functions)
│   ├── priority/                 PriorityScreen + PriorityViewModel
│   ├── task/                     TaskItem, TaskCheckbox, TaskFormSheet, TaskFormViewModel,
│   │                             DeadlinePickerDialog, EndTimePickerDialog,
│   │                             CustomRecurrenceSheet, PriorityPickerSheet,
│   │                             LabelPickerSheet, TaskColors, TaskMobileMenu,
│   │                             FormMode (TASK / EVENT)
│   ├── theme/                    Color.kt, Theme.kt, Typography.kt
│   ├── upcoming/                 UpcomingScreen + UpcomingViewModel
│   └── util/                     BaseViewModel, EmptyState, ShimmerTaskList,
│                                 ErrorSnackbarEffect
│
├── util/
│   └── ColorExtensions.kt        String.toComposeColor(), etc.
│
└── widget/
    ├── CalendarWidget.kt         upcoming tasks + events mixed timeline (4th widget type)
    ├── CompleteTaskAction.kt     GlanceActionCallback: complete task + queue + widget refresh
    ├── FolderWidget.kt           hierarchical folder task list
    ├── TaskListWidget.kt         flat filtered list (folder / label / priority)
    ├── ToggleExpandAction.kt     GlanceActionCallback: toggle subtask visibility
    ├── UpcomingWidget.kt         tasks + calendar events ≤ 7 days, grouped by date
    ├── WidgetColors.kt           named ColorProvider constants
    ├── WidgetConfigActivity.kt   configuration screen for all widget types
    ├── WidgetEntryPoint.kt       Hilt entry point for widget actions
    ├── WidgetEventRow.kt         calendar event row (icon + title + time + calendar name)
    ├── WidgetHeader.kt           shared widget header (title + "+" button)
    ├── WidgetPrefs.kt            Glance state keys
    ├── WidgetRefresher.kt        updateAll() helper for all widget types
    └── WidgetTaskRow.kt          shared task row (checkbox + title + deadline/labels)
```

---

## 5. Data Model

### 5.1 Domain Models

#### Task
| Field | Type | Description |
|---|---|---|
| id | String | `"tsk_xxxxxxxx"` (8 hex chars) |
| parentId | String | Parent task ID, `""` for root tasks |
| folderId | String | Folder ID, defaults to `"fld-inbox"` |
| title | String | Task title |
| status | TaskStatus | `PENDING` or `COMPLETED` |
| priority | Priority | `URGENT`, `IMPORTANT`, `NORMAL` |
| deadlineDate | String | ISO date `"YYYY-MM-DD"` or `""` |
| deadlineTime | String | `"HH:MM"` or `""` |
| isRecurring | Boolean | Recurring task flag |
| recurType | RecurType | `DAILY`, `WEEKLY`, `MONTHLY` |
| recurValue | Int | Recurrence interval (e.g. 2 = every 2 days) |
| labels | List\<String\> | List of label IDs |
| sortOrder | Int | Position within parent (sort_order = index × 10) |
| createdAt | String | ISO instant |
| updatedAt | String | ISO instant |
| completedAt | String | ISO instant or `""` |
| isExpanded | Boolean | Whether subtasks are shown (not synced to `updatedAt`) |
| isRoot | Boolean (computed) | `parentId.isBlank()` |

**Recurring task completion:** When `isRecurring` is true, completing the task does NOT set `status = COMPLETED`. Instead, `deadlineDate` advances by `recurValue × recurType` and `completedAt` is cleared. The task remains in `PENDING` state with a new deadline.

**Subtask visibility:** `isExpanded` is synced to Sheets but toggling it does NOT update `updatedAt` — prevents triggering unnecessary sync events.

#### Folder
| Field | Type | Description |
|---|---|---|
| id | String | `"fld-inbox"` for Inbox, `"fld-xxxxxxxx"` for others |
| name | String | Display name |
| color | String | Hex color `"#rrggbb"` |
| sortOrder | Int | Position in sidebar |
| isInbox | Boolean (computed) | `id == "fld-inbox"` |

#### Label
| Field | Type | Description |
|---|---|---|
| id | String | `"lbl_xxxxxxxx"` |
| name | String | Display name |
| color | String | Hex color `"#rrggbb"` |
| sortOrder | Int | Position in sidebar |

#### CalendarEvent
| Field | Type | Description |
|---|---|---|
| id | String | Google Calendar event ID |
| calendarId | String | Google Calendar ID (e.g. `"primary"`) |
| calendarName | String | Human-readable calendar name |
| calendarColor | String | Calendar hex color (e.g. `"#039be5"`) |
| title | String | Event summary |
| startDate | String | `"YYYY-MM-DD"` |
| startTime | String | `"HH:MM"` or `""` for all-day |
| endTime | String | `"HH:MM"` or `""` |
| isAllDay | Boolean | True when only `start.date` is set (no `dateTime`) |
| recurringEventId | String? | Series base event ID (non-null for recurring instances) |
| seriesId | String? | Alias for recurringEventId |
| isRecurring | Boolean (computed) | `recurringEventId != null` |

#### CalendarItem
| Field | Type | Description |
|---|---|---|
| id | String | Calendar ID |
| summary | String | Display name |
| color | String | Hex color |
| isSelected | Boolean | Whether user has selected this calendar for display |
| accessRole | String | `"owner"`, `"writer"`, `"reader"`, `"freeBusyReader"` |

### 5.2 Room Database (version 7)

5 tables: `tasks`, `folders`, `labels`, `sync_queue`, `calendar_events`.

Migration path: v4 → v5 (added `calendar_events`), v5 → v6 (added `series_id` column), v6 → v7 (added `calendar_color` column). Schema JSON files stored in `app/schemas/`.

#### Table: `calendar_events`
| Column | Type | Notes |
|---|---|---|
| id | TEXT PK | Google event ID |
| calendar_id | TEXT | |
| calendar_name | TEXT | |
| calendar_color | TEXT | |
| title | TEXT | |
| start_date | TEXT | `"YYYY-MM-DD"` |
| start_time | TEXT | `"HH:MM"` or `""` |
| end_time | TEXT | `"HH:MM"` or `""` |
| is_all_day | INTEGER | 0/1 |
| recurring_event_id | TEXT | nullable |
| series_id | TEXT | nullable |

### 5.3 SyncQueue Entry
| Field | Type | Description |
|---|---|---|
| id | Long (autoincrement) | |
| entityType | String | `"task"`, `"folder"`, or `"label"` |
| entityId | String | ID of the affected entity |
| operation | String | `"INSERT"`, `"UPDATE"`, `"DELETE"` |
| payloadJson | String | Full entity serialized as JSON (Gson) |
| retryCount | Int | Incremented on failure; items removed after 5 failures |

### 5.4 Google Sheets Schema

**Spreadsheet name:** `db_tasks`  
**Read mode:** `UNFORMATTED_VALUE` (dates come back as serial numbers — converted by `dateStr()`)  
**Write mode:** `RAW` (all values written as strings)

#### Sheet: `tasks` (columns A–Q)
| Col | Name | Type in Sheets |
|---|---|---|
| A | id | String |
| B | parent_id | String |
| C | folder_id | String |
| D | title | String |
| E | status | String (`"pending"` / `"completed"`) |
| F | priority | String (`"urgent"` / `"important"` / `"normal"`) |
| G | deadline_date | String `"YYYY-MM-DD"` or Sheets serial date (Number) |
| H | deadline_time | String `"HH:MM"` or `""` |
| I | is_recurring | String `"TRUE"` / `"FALSE"` |
| J | recur_type | String (`"daily"` / `"weekly"` / `"monthly"`) |
| K | recur_value | String (Int) |
| L | labels | String (comma-separated label IDs) |
| M | sort_order | String (Int) |
| N | created_at | String (ISO instant) |
| O | updated_at | String (ISO instant) |
| P | completed_at | String (ISO instant) |
| Q | is_expanded | String `"TRUE"` / `"FALSE"` |

#### Sheet: `folders` (columns A–D)
| Col | Name |
|---|---|
| A | id |
| B | name |
| C | color |
| D | sort_order |

#### Sheet: `labels` (columns A–D)
| Col | Name |
|---|---|
| A | id |
| B | name |
| C | color |
| D | sort_order |

**Row 1 of every sheet** is a header row (skipped by `SheetsMapper.findRowNumber`). Entity rows start at row 2.  
**Soft delete** = clearing the row (all cells emptied). `rowToTask/Folder/Label` returns `null` for rows with no ID — these are skipped during pull.

---

## 6. Authentication & First-Launch Setup

### 6.1 OAuth Scopes
- `https://www.googleapis.com/auth/spreadsheets` — full Sheets read/write + create
- `https://www.googleapis.com/auth/drive.metadata.readonly` — search Drive for existing spreadsheet
- `https://www.googleapis.com/auth/calendar` — read/write Google Calendar events

### 6.2 Sign-In Flow (GoogleAuthRepository.signIn)
1. **CredentialManager** → shows Google account picker → returns `GoogleIdTokenCredential` with user email, display name, avatar URL
2. **Identity.getAuthorizationClient** → requests Sheets + Drive + Calendar scopes → may return a `PendingIntent` if user hasn't approved scopes yet
3. If `hasResolution()` → return `SignInStep.NeedsAuthorization(pendingIntent)` → `AuthScreen` launches the intent → `finalizeAuth()` called on result
4. **findOrCreateSpreadsheet(token)** → Drive API search for `name='db_tasks'` → if not found, create new spreadsheet (see §6.3)
5. **Save to DataStore:** accessToken, tokenExpiry (+1 h), spreadsheetId, userEmail, userName, userAvatarUrl

### 6.3 First-Launch Spreadsheet Creation
When no `db_tasks` spreadsheet is found in the user's Drive, `createSpreadsheet()`:
1. POST `https://sheets.googleapis.com/v4/spreadsheets` → creates spreadsheet with 3 named sheets (tasks, folders, labels)
2. POST `.../values:batchUpdate` → writes header row to each sheet + seeds Inbox folder row in `folders`
3. Upserts Inbox `FolderEntity(id="fld-inbox", name="Inbox", color="#6b7280", sortOrder=0)` to Room so the app is immediately usable before first sync

### 6.4 Token Refresh
`refreshToken()` calls `Identity.getAuthorizationClient.authorize()` silently (no Activity needed). Token is considered stale if it expires within 5 minutes. The `NetworkModule` 401 Authenticator calls `refreshToken()` automatically on HTTP 401.

### 6.5 Sign-Out
Clears all DataStore keys + deletes all Room tables (tasks, folders, labels, sync_queue, calendar_events).

---

## 7. Synchronization

### 7.1 SyncWorker (push → pull Sheets → pull Calendar)
Triggered by WorkManager. Execution order:
1. **Push** — drain `SyncQueue` ordered by insertion:
   - `INSERT` → `SheetsApi.append(range = "sheet!A:lastCol")`
   - `UPDATE` → fetch current rows (cached per sheet per push) → find row number by entity ID → `SheetsApi.batchUpdate`
   - `DELETE` → same row-number lookup → `SheetsApi.clear`
   - On success: `syncQueueDao.deleteById(item.id)`
   - On failure: `syncQueueDao.incrementRetry(item.id)`
   - After all items: `syncQueueDao.deleteExhausted()` (removes items with retryCount ≥ 5)
2. **Delay** — if there were pending items: `delay(1_000 ms)` to let Sheets process writes before reading
3. **Pull Sheets** — `taskRepository.fetchAllAndSave(spreadsheetId)`:
   - `SheetsApi.batchGet(["tasks", "folders", "labels"])`
   - All three upserted inside a single Room transaction (`db.withTransaction`)
4. **Pull Calendar** — `calendarRepository.fetchEventsAndSave()`:
   - Fetches selected calendar IDs from DataStore
   - For each selected calendar: `CalendarApi.listEvents(calendarId, from=today, to=today+30d)`
   - Upserts all events to `calendar_events` table; deletes stale events from same calendars
5. Retry: up to 4 attempts (`WorkManager` exponential backoff); returns `Result.failure()` after 5th

### 7.2 Calendar Event Write Path

Calendar event mutations bypass the SyncQueue entirely and go directly to the Calendar API. All methods are in `CalendarRepositoryImpl`.

**createEvent(calendarId, request)**
1. POST to Calendar API → receives dto
2. Fetch `calendarMeta` (5-min in-memory cache; refreshed from `listCalendars()` if stale) to resolve calendar name + color
3. Map dto → entity via `CalendarMapper.dtoToEntity`
4. Upsert entity to Room immediately (instant UI feedback before any re-fetch)
5. If `request.recurrence` is non-empty: re-fetch full calendar (today..today+366) to pick up all generated recurring instances
6. Return the mapped domain event

**updateEvent(calendarId, eventId, request)**
1. PUT to Calendar API (full replace — avoids residual `start.date` left by PATCH merge on recurring timed events)
2. Map response → entity → upsert to Room
3. Always re-fetch the full calendar to update all recurring instances
4. Return mapped domain event

**deleteEvent(calendarId, eventId)**
1. HTTP DELETE; a 410 (Gone — already deleted) is accepted as success
2. Remove from Room by `eventId` (`calendarEventDao.deleteById`)

**deleteEventSeries(calendarId, seriesId)**
1. HTTP DELETE on `seriesId` (the base/master event ID)
2. Remove from Room all entries sharing that `seriesId` (`calendarEventDao.deleteBySeriesId`)

**moveEvent(fromCalendarId, toCalendarId, eventId)**
1. `calendarApi.moveEvent(fromCalendarId, eventId, toCalendarId)` (POST to `.../move`)
2. Map response → new entity with `toCalendarId` metadata
3. `calendarEventDao.deleteSeriesAndReplace(eventId, entity)` — atomic Room transaction (delete old series entries + insert new)

**getBaseEvent(calendarId, seriesId)**
1. `calendarApi.getEvent(calendarId, seriesId)` — single event fetch (the series master)
2. Extract RRULE: `dto.recurrence?.firstOrNull { it.startsWith("RRULE:") } ?: ""`
3. Extract endTime: `dto.end.dateTime?.substring(11, 16)` (characters 11–15, i.e. `"HH:MM"`); blank if absent
4. Returns `CalendarEventWithRRule(event, rrule, endTime)`

**calendarMeta cache:** 5-minute TTL in-memory cache mapping `calendarId → (calendarName, calendarColor)`. Used after create/update/move to label Room entities correctly without extra API calls on every write.

**EventDateTime serialization** (custom Gson `TypeAdapter` registered in `CalendarModule`):
- All-day event: `EventDateTime(date = "YYYY-MM-DD")` — `dateTime` absent from JSON
- Timed event: `EventDateTime(dateTime = "YYYY-MM-DDTHH:MM:SS+HH:MM", timeZone = "IANA/Zone")` — `date` absent from JSON
- Null fields are silently omitted (`serializeNulls=false` default) — intentional; sending `"date": null` causes HTTP 400 for recurring timed events in Google Calendar API v3
- `timeZone` is REQUIRED for recurring timed events (Google Calendar API v3 requirement)

**`buildEventDateTime` (in `TaskFormViewModel`):**
```kotlin
if (time.isBlank())
    EventDateTime(date = date)
else {
    val zone = ZoneId.systemDefault()
    val zdt  = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)
    EventDateTime(dateTime = zdt.format(ISO_OFFSET_DATE_TIME), timeZone = zone.id)
}
```

**`buildEndDateTime` (in `TaskFormViewModel`):**
```kotlin
if (startTime.isBlank()) {
    // All-day: end = next calendar day
    EventDateTime(date = LocalDate.parse(startDate).plusDays(1).toString())
} else {
    val startLocal = LocalTime.parse(startTime)
    val endT = when {
        endTime.isBlank()                                   -> startLocal.plusHours(1).format("HH:mm")
        !LocalTime.parse(endTime).isAfter(startLocal)       -> startLocal.plusHours(1).format("HH:mm")
        // clamp: if user moved start past old end, end defaults to start+1h
        else                                                 -> endTime
    }
    val zone = ZoneId.systemDefault()
    val zdt  = ZonedDateTime.of(LocalDate.parse(startDate), LocalTime.parse(endT), zone)
    EventDateTime(dateTime = zdt.format(ISO_OFFSET_DATE_TIME), timeZone = zone.id)
}
```
Special case: end time is clamped to start+1 h whenever the stored end time is ≤ start time (e.g. after the user moves the start time past the old end time).

### 7.3 SyncManager
- **Periodic:** `PeriodicWorkRequest` every 30 min, constraint `CONNECTED`
- **One-off:** `triggerSync()` enqueues an immediate `OneTimeWorkRequest` (CONNECTED), replaces existing with `KEEP` policy
- **Initialized** in `TasksApplication.onCreate()`

### 7.4 SyncState
`StateFlow<SyncState>` combining WorkManager `WorkInfo` + `SyncQueueDao.countAll()`:
- `Idle` — no running worker, queue empty
- `Syncing` — worker is RUNNING
- `Pending(count)` — queue has items, no worker running

Displayed in the sidebar footer (count badge + spinning icon when Syncing).

### 7.5 NetworkObserver
`callbackFlow` wrapping `ConnectivityManager.registerNetworkCallback`. Emits `true`/`false`, `distinctUntilChanged`. Used in `MainViewModel` to trigger sync on reconnect.

---

## 8. UI Screens

### 8.1 Upcoming
**ViewModel:** `UpcomingViewModel`  
**Data:** Root `PENDING` tasks + calendar events, all within 7 days; grouped by `deadlineDate`/`startDate`  
**Features:**
- Horizontal date strip (7 day-pills: date number + weekday letter + orange dot if tasks exist)
- Left/right navigation arrows to shift the visible week
- "Today" button (primary border when on the current week)
- Filter pills: priority chips + label chips + folder chip + calendar chip
- Tasks and calendar events unified into a date-grouped timeline (`"16 Apr · Thursday · Today"`)
- **Overdue section** — all past-due tasks/events collapsed into one group above the date strip (date key = `LocalDate.MIN`)
- `showDateInDeadline = false` on `TaskItem` (date shown in section header, only time shown in row 2)
- `isLoading` StateFlow → shimmer skeleton on first load

**Filter matrix** — four independent filter sets: `priorityFilter`, `labelFilter`, `folderFilter`, `calendarFilter`:

```
taskFiltersActive = priorityFilter.isNotEmpty() || labelFilter.isNotEmpty() || folderFilter.isNotEmpty()
calFiltersActive  = calendarFilter.isNotEmpty()

shown tasks  = if (calFiltersActive && !taskFiltersActive) → none
               else → tasks filtered by priority ∩ label ∩ folder

shown events = if (calFiltersActive)  → events filtered by calendarId ∈ calendarFilter
               if (taskFiltersActive) → none
               else                   → all events
```

**Sort order within each date group:**
1. Overdue group: secondary sort by `startDate`/`deadlineDate` string ascending
2. Timed items (`startTime != ""`) sort before all-day items
3. Within timed: ascending by time string

**`calendarsInEvents`:** derived `StateFlow<List<CalendarItem>>` — distinct calendars present in the currently loaded events. Used to populate the calendar filter chip dropdown in UpcomingScreen.

### 8.2 All Tasks
**ViewModel:** `AllTasksViewModel`  
**Data:** Root `PENDING` tasks; sort: priority → deadline → createdAt  
**Features:**
- Filter pills (priority / label / folder)
- EmptyState when no tasks match
- Shimmer on first load

### 8.3 Completed
**ViewModel:** `CompletedViewModel`  
**Data:** `COMPLETED` tasks; sort: completedAt desc  
**Features:**
- TaskItem with `enableSwipe = false`, strikethrough title, Restore + Delete buttons
- EmptyState

### 8.4 Folder
**ViewModel:** `FolderViewModel`  
**Data:** All pending tasks in a folder, including recursive subtrees  
**Features:**
- Hierarchical display: `DisplayNode(task, depth, childCount, completedChildCount)`
- `isExpanded` controls subtask visibility — toggling persists to Room + Sheets
- Drag handle (DragHandle icon, far left) — `reorderable` library
- Drag-to-reorder: `onMove` updates optimistic local list; on drop (`isDragging → false`) calls `reorderSiblings` → single batch Room write
- `onIndent` / `onOutdent` in TaskItem "..." menu for reparenting
- Swipe enabled (drag handle and swipe gesture use separate touch areas)

### 8.5 Label
**ViewModel:** `LabelViewModel`  
**Data:** Root `PENDING` tasks with the selected label ID; sort: priority → deadline → createdAt  
**Features:**
- Priority filter chip
- EmptyState

### 8.6 Priority
**ViewModel:** `PriorityViewModel`  
**Data:** Root `PENDING` tasks for the selected priority  
**Features:**
- Tabs: Urgent / Important / Normal
- Sort: deadline → createdAt
- EmptyState per tab

### 8.7 Calendar
**ViewModel:** `CalendarViewModel`  
**Route:** `calendar/{calendarId}` — отдельный экран на каждый календарь; `calendarId` берётся из `SavedStateHandle`  
**Data:** события одного конкретного календаря из Room, от `today` до `today + 366 дней`  
**Features:**
- `LazyColumn` событий, сгруппированных по дате (аналогично UpcomingScreen)
- Заголовок-дата перед каждой группой (`CalendarDayHeader`): `"d MMM ‧ Weekday"` + `"‧ Today"` / `"‧ Tomorrow"` для ближайших дат
- Все просроченные события (startDate < today) объединяются в группу `"Overdue"` (ключ `LocalDate.MIN`)
- Пока данные не загружены — `ShimmerTaskList`; если событий нет — `EmptyState`
- `CalendarEventItem` на каждое событие (см. §8.8)
- FAB отсутствует в самом `CalendarScreen`; глобальный FAB MainScreen здесь тоже отображается и открывает создание **задачи** (`TaskFormSheet` в TASK mode), а не события

### 8.8 CalendarEventItem

Shared-компонент для отображения одного события — используется и в `CalendarScreen`, и в других местах.

**Строка 1:**
```
[40dp placeholder] [40dp box: CalendarMonth icon, tinted calColor, 24dp] [title weight=1] [Schedule btn] [MoreHoriz btn]
```
- Иконка `CalendarMonth` (24dp) тонирована `event.calendarColor` — идентифицирует принадлежность к календарю
- Title: кликабелен если передан `onEdit`; 2 строки max
- **Schedule** (`Icons.Outlined.Schedule`, 18dp, цвет `deadlineColor`): быстрое редактирование расписания (`onEditSchedule`)
- **MoreHoriz** (18dp): открывает `ModalBottomSheet` с пунктами Edit и Delete

**Строка 2** (отступ 54dp):
```
[time label?]  [CalendarMonth icon 14dp, tinted]  [calendar name]
```
- `timeLabel`: для timed-события — `"HH:MM — HH:MM"` или `"HH:MM"`; для all-day + `showDate=true` — `"d MMM"` / `"Today"` / `"Tomorrow"`; для all-day + `showDate=false` — пусто
- Иконка `CalendarMonth` 14dp тонирована `calColor` перед названием календаря (не точка, не badge — именно иконка)

**Удаление:**
- Нерекуррентное: `AlertDialog` "Delete event?" → `onDelete()`
- Рекуррентное: `AlertDialog` с тремя кнопками — "Delete this event only" / "Delete all events in series" (error color) / "Cancel"

**Параметры:**
| Параметр | Описание |
|---|---|
| `event` | `CalendarEvent` |
| `showDate` | Показывать дату в строке 2 (false когда дата уже в заголовке группы) |
| `onEdit` | Открыть форму редактирования (полная); если null — кнопки не показываются |
| `onEditSchedule` | Открыть форму редактирования только расписания (Schedule-кнопка) |
| `onDelete` | Удалить событие |
| `onDeleteSeries` | Удалить всю серию (для рекуррентных) |

### 8.9 MainScreen — глобальный FAB и Settings

**FAB** — единственная кнопка `FloatingActionButton` в `Scaffold` `MainScreen`. Отображается на **всех** экранах. Открывает `TaskFormSheet` в TASK mode:
- На экране Folder — создаёт задачу в текущей папке (`sidebarFolderContext = currentFolderId`)
- На всех остальных экранах — создаёт задачу в Inbox

Кнопка создания событий в FAB **не** встроена — события создаются через кнопку "+" в шапке виджета или в EVENT mode внутри `TaskFormSheet`.

**Settings** — открывается не через навигационный маршрут, а как overlay: `showSettings = true` в `MainScreen` заменяет основной контент на `SettingsScreen`. Кнопка вызова — в `TasksTopAppBar`.

### 8.10 Settings Screen

**ViewModel:** `SettingsViewModel`. Открывается поверх основного контента (не через NavHost), закрывается кнопкой Back в TopAppBar.

Два раздела:

**SPREADSHEET**
- Показывает имя и ID активной таблицы Google Sheets
- Кнопка "Change" разворачивает `AnimatedVisibility`-список всех Google Sheets из Drive пользователя (загружает `listUserSheets()` при первом открытии)
- Активная таблица отмечена иконкой Check; клик по другой — переключение: сохраняет новый ID в DataStore, очищает Room (tasks / folders / labels / sync\_queue), запускает sync

**CALENDARS**
- Список всех Google Calendars пользователя (загружается при открытии экрана через `loadCalendars()`)
- Каждый календарь: иконка `CalendarMonth` тонированная цветом + название + `Checkbox`
- Клик по строке или Checkbox → `toggleCalendar()`:
  - Обновляет `selectedCalendarIds` в DataStore
  - Оптимистично обновляет UI без повторного запроса к API
- Кнопка Refresh в заголовке секции — повторный запрос к Calendar API
- **Только отмеченные (`isSelected = true`) календари синхронизируются** в SyncWorker и отображаются в Upcoming / Calendar screens / виджетах
- Календари с `isSelected = false` в App остаются в списке (user может включить позже), но события из них не запрашиваются

---

## 9. TaskItem Component

The single shared task row composable used on all screens.

### 9.1 Parameters
| Parameter | Default | Description |
|---|---|---|
| task | — | Task domain model |
| labels | — | All labels (for name/color lookup) |
| depth | 0 | Indent level (×20dp) |
| hasChildren | false | Show expand/collapse chevron |
| completedChildCount | 0 | For subtask progress display |
| totalChildCount | 0 | Total direct children |
| showFolder | false | Show folder name in row 2 |
| showLabels | true | Show label chips in row 2 |
| showDateInDeadline | true | Show date in deadline label (false in Upcoming) |
| folderName / folderColor | null | Folder display info |
| onCheckedChange | — | Toggle complete/incomplete |
| onExpand | — | Toggle isExpanded |
| onDeadlineChange | — | Update deadline (date, time, recurring params) |
| onPriorityChange | — | Update priority |
| onLabelChange | — | Update label list |
| onAddSubtask | — | Open create form with parentId preset |
| onEdit | — | Open edit form |
| onDelete | — | Delete task (after confirm dialog) |
| onIndent | null | FolderScreen only: reparent under task above |
| onOutdent | null | FolderScreen only: move up one level |
| enableSwipe | true | False in CompletedScreen |

### 9.2 Layout

**Row 1:**
```
[expand 40dp] [checkbox 40dp] [title weight=1] [deadline btn] [more btn]
```
- Expand box: 40dp touch target, 24dp chevron icon (ChevronRight/ExpandMore); empty box if no children
- Checkbox: `TaskCheckbox` — custom Canvas-drawn, priority-colored border/fill, 40dp touch target, 18dp visual; white checkmark when checked; TalkBack contentDescription
- Title: tappable (`.clickable { onEdit() }`), strikethrough + 70% alpha when completed
- When completed: Restore + Delete buttons instead of deadline/more

**Row 2 (conditional, when any metadata exists):**
```
[recurring icon?] [deadline label?] [label badges?] [folder?] [subtask count?]
```
Indented by `depth × 20 + 54dp`

### 9.3 Swipe Gestures
`SwipeToDismissBox` wrapped in `key(task.id, task.deadlineDate)`:
- **Swipe right (StartToEnd):** Green background + checkmark icon → 500ms delay → `onCheckedChange(true)`. The `key()` ensures the dismiss state rebuilds when the deadline changes (critical for recurring tasks)
- **Swipe left (EndToStart):** Blue background + schedule icon → snaps back → opens `DeadlinePickerDialog`
- `confirmValueChange` handles the snap-back for left swipe
- `enableSwipe = false` passes through for CompletedScreen

### 9.4 Dialogs / Sheets
- `DeadlinePickerDialog` — date picker chip + time chip + repeat checkbox + Postpone button
- `PriorityPickerSheet` — bottom sheet with 3 priority options
- `LabelPickerSheet` — multi-select with create-new option
- `TaskMobileMenu` — bottom sheet: Priority / Labels / Add subtask / Indent / Outdent / Edit / Delete
- Delete confirm `AlertDialog`

---

## 10. TaskFormSheet (Create / Edit Tasks and Events)

Single bottom sheet for task creation, task editing, and calendar event creation/editing. Mode is controlled by `TaskFormViewModel.formMode: FormMode` (TASK or EVENT).

### 10.1 Parameters

```kotlin
fun TaskFormSheet(
    task            : Task? = null,          // non-null → edit task mode
    calendarEvent   : CalendarEvent? = null, // non-null → edit event mode
    scheduleOnly    : Boolean = false,       // true → show only date/time/repeat, hide title + calendar picker
    initialFolderId : String = "fld-inbox",
    initialParentId : String = "",
    labels          : List<Label>,
    folders         : List<Folder>,
    onConfirm       : (TaskFormResult) -> Unit,
    onDismiss       : () -> Unit,
    viewModel       : TaskFormViewModel = hiltViewModel(),
)
```

Derived flags: `isEditing = task != null`, `isEditingEvent = calendarEvent != null`.

### 10.2 Form State Initialization

| Field | Initial value |
|---|---|
| `title` | `task?.title ?: calendarEvent?.title ?: ""` |
| `priority` | `task?.priority ?: Priority.NORMAL` |
| `selectedLabelIds` | `task?.labels ?: emptyList()` |
| `deadlineDate` | `task?.deadlineDate ?: calendarEvent?.startDate ?: ""` |
| `deadlineTime` | `task?.deadlineTime ?: calendarEvent?.startTime ?: ""` |
| `isRecurring` | `task?.isRecurring ?: false` (never pre-filled from event) |
| `recurType` | `task?.recurType ?: RecurType.DAYS` |
| `recurValue` | `task?.recurValue?.toString() ?: "1"` |
| `folderId` | `task?.folderId ?: initialFolderId` |

### 10.3 LaunchedEffect(Unit) — Form Open Behavior

On every form open:
1. If `!scheduleOnly` → request focus on the title field
2. Reset ViewModel recurrence state: `crsByDay = ∅`, `crsMonthlyIdx = 0`, `crsEnds = NEVER`, `crsEndDate = null`, `crsAfterCountStr = "13"`

**When `isEditingEvent` (editing a calendar event):**
- `viewModel.formMode = FormMode.EVENT`
- `viewModel.endTime = calendarEvent.endTime`
- `viewModel.selectedCalendarId = calendarEvent.calendarId`
- `viewModel.loadCalendars()`
- If `calendarEvent.isRecurring`:
  - Call `viewModel.loadBaseEvent(calendarId, seriesId)` with a callback that receives `(_, _, _, _, parsed)`
  - **Only recurrence state is applied from the base event** — date/time/title remain as the specific instance's values
  - If `parsed != null`:
    - `isRecurring = true`
    - `recurType` = map `RRuleFreq → RecurType` (DAILY→DAYS, WEEKLY→WEEKS, MONTHLY→MONTHS, YEARLY→YEARS)
    - `recurValue = parsed.interval.toString()`
    - `viewModel.crsByDay = parsed.byDay`
    - `viewModel.crsEnds = parsed.ends`
    - `viewModel.crsEndDate = parsed.endDate`
    - `viewModel.crsAfterCountStr = parsed.afterCount.toString()`
    - If MONTHLY and `deadlineDate.isNotBlank()`: compute `monthlyOptions(deadlineDate)`, find the option matching `parsed.byDay` code, set `viewModel.crsMonthlyIdx`
  - Set `baseEventLoaded = true`

**When creating or editing a task:**
- `viewModel.endTime = ""`
- `viewModel.formMode = FormMode.TASK`

### 10.4 Mode Toggle

`SingleChoiceSegmentedButtonRow` (Task / Event segments) is shown **only when `!isEditing && !isEditingEvent`** (i.e., only when creating a brand-new item).
- Task segment → `viewModel.formMode = FormMode.TASK`
- Event segment → `viewModel.formMode = FormMode.EVENT; viewModel.loadCalendars()`

### 10.5 Deadline Section

**EVENT mode** — row: `[Date chip*] [Start time chip] — [End time chip]`

| Element | Blank state | Set state | Error state | On click |
|---|---|---|---|---|
| Date chip | "Date *" (gray) | formatted date in deadline color | "Date *" in error color when `startDateError=true` | `showDeadlinePicker=true; startDateError=false` |
| Start time chip | "HH:MM" (gray) | time in deadline color | — | `showTimePicker=true` |
| "—" separator | always visible | — | — | — |
| End time chip | "HH:MM" | "HH:MM" when set | "HH:MM*" when startTime set but endTime blank (hint) / "End time *" when `endTimeError=true` | `showEndTimePicker=true; endTimeError=false` |

**TASK mode** — row: `[Date chip] [Time chip — only shown when date is set]`
- Date chip: "No date" when blank; formatted date in deadline color when set
- Time chip: only rendered when `deadlineDate.isNotBlank()`

### 10.6 Repeat Row

Shown when `deadlineDate.isNotBlank() || formMode == EVENT`.

`RepeatRow`: Checkbox + label. When unchecked: label is "Repeat". When checked: "Repeat every [N] [day|week|month|year▾]".
- Interval: numeric `BasicTextField`, max 3 digits
- Frequency dropdown: day / week / month / year → maps to `RecurType.DAYS / WEEKS / MONTHS / YEARS`

### 10.7 EVENT Mode Recurrence Extras

Shown when `formMode == EVENT && isRecurring`.

**"Repeat on"** (WEEKLY only):
- 7 circle buttons Mon–Sun
- Selected day: `FilledTonalButton`; unselected: `TextButton`
- `LaunchedEffect(recurType)`: when switching to WEEKLY and `crsByDay` is empty, automatically seeds it from `deadlineDate.dayOfWeek`
- Tapping a day toggles it in/out of `viewModel.crsByDay`

**"Repeat by"** (MONTHLY only):
- `monthlyOptions(LocalDate.parse(deadlineDate))` computes 2 or 3 options:
  1. "Monthly on day N"
  2. "Monthly on the Nth Weekday"
  3. "Monthly on the last Weekday" (only when `dayOfMonth > 21` and the same weekday doesn't fit in the next month's occurrence)
- `ExposedDropdownMenuBox` showing those options; selection stored in `viewModel.crsMonthlyIdx`

**"Ends"** (all repeating events — WEEKLY, MONTHLY, DAILY, YEARLY):
- Three `RadioButton` options:
  - **Never** → `EndsType.NEVER`
  - **On** → `EndsType.ON_DATE`; shows a `TextButton` with the chosen date → opens `DatePickerDialog` (sets `viewModel.crsEndDate`)
  - **After** → `EndsType.AFTER_COUNT`; shows `BasicTextField` (1–3 digits) + "occurrences" label

### 10.8 TASK Mode Fields

Shown only when `formMode == TASK`.

**Folder:** `FilterChip` always in selected state. Background = `folder.color @ 18% alpha`. `onClick` → `showFolderPicker = true`.

**Labels:** Clickable row showing "Labels [N]▸". Tapping opens `LabelPickerSheet`. Selected labels are shown as `FilterChip`s with a × trailing icon for quick-remove.

**Priority:** Three `FilterChip`s (Urgent / Important / Normal). Each chip background = priority color @ 18% alpha. Selected chip shows a Check icon; unselected shows a Flag icon.

### 10.9 EVENT Mode Fields

Shown only when `formMode == EVENT`.

**Calendar picker** (hidden when `scheduleOnly = true`):
- Loading → `CircularProgressIndicator`
- Empty list → message "No calendars selected. Go to Settings → Calendars." (shown in error color when `calendarError = true`)
- Loaded → `OutlinedTextField` (readOnly) with `CalendarMonth` icon tinted + dropdown; selecting a calendar sets `viewModel.selectedCalendarId = cal.id; calendarError = false`

### 10.10 Buttons Row

**Left side (TASK mode only):**
- **"Clear"**: shown when `isEditing && (deadlineDate.isNotBlank() || deadlineTime.isNotBlank())`. Resets: `deadlineDate=""`, `deadlineTime=""`, `isRecurring=false`, `recurType=DAYS`, `recurValue="1"`
- **"Postpone"**: shown when `isEditing && isRecurring && deadlineDate.isNotBlank()`. Advances `deadlineDate` by `recurValue × recurType` using `LocalDate.plusDays/plusWeeks/plusMonths`

**Right side (always):**
- "Cancel" `TextButton` → `onDismiss()`
- "Save" / "Create" `TextButton` with Check icon:
  - TASK mode → `submitTask()`
  - EVENT mode, editing a recurring event → `showEditSeriesDialog = true`
  - EVENT mode, otherwise → `submitEvent()`
  - Label: "Save" when `isEditing || isEditingEvent`, "Create" otherwise

### 10.11 Edit Recurring Event Dialog

`AlertDialog` shown when the user taps Save while editing a recurring calendar event. Presents:
1. **"Edit this event only"** → `submitEventForInstance()`
2. **"Edit all events in series"** → `submitEvent()`
3. **"Cancel"** → dismisses dialog

### 10.12 submitTask()

```
trimmed = title.trim()
if (trimmed.isBlank()) → titleError = true; return

parsed = parseSmartTitle(trimmed, folders, labels, folderId, selectedLabelIds)

onConfirm(TaskFormResult(
    title        = parsed.title,
    folderId     = parsed.folderId,
    parentId     = task?.parentId ?: initialParentId,
    priority     = parsed.priority ?: priority,
    labelIds     = parsed.labelIds,
    deadlineDate = deadlineDate,
    deadlineTime = deadlineTime,
    isRecurring  = isRecurring,
    recurType    = recurType,
    recurValue   = recurValue.toIntOrNull() ?: 1,
))
```

### 10.13 submitEvent() — new event or edit whole series

```
if (!scheduleOnly && title.isBlank()) → titleError = true; return
if (deadlineDate.isBlank()) → startDateError = true; return
if (!scheduleOnly && calendars.isEmpty() && !isEditingEvent) → calendarError = true; return

Build RRULE (if isRecurring):
  freq      = recurType → RRuleFreq
  interval  = recurValue.toIntOrNull() ?: 1
  if MONTHLY: monthlyOpt = monthlyOptions(deadlineDate)[crsMonthlyIdx]
  rrule = buildRRule(freq, interval,
    byDay         = if WEEKLY then crsByDay else ∅,
    monthlyOption = if MONTHLY then monthlyOpt else null,
    ends          = crsEnds,
    endDate       = crsEndDate,
    afterCount    = crsAfterCountStr.toIntOrNull().coerceIn(1, 999) ?: 13)

if (isEditingEvent):
  // Target the series master event, not the individual instance
  targetId = if (calendarEvent.isRecurring) calendarEvent.recurringEventId else calendarEvent.id
  viewModel.updateEvent(selectedCalendarId, targetId, trimmed,
    deadlineDate, deadlineTime, viewModel.endTime, rrule,
    originalCalendarId = calendarEvent.calendarId)
else:
  viewModel.createEvent(selectedCalendarId, trimmed,
    deadlineDate, deadlineTime, viewModel.endTime, rrule)
```

### 10.14 submitEventForInstance() — edit this instance only

```
eventId = calendarEvent.id   // the specific instance ID, NOT recurringEventId
viewModel.updateEvent(selectedCalendarId, eventId, trimmed,
  deadlineDate, deadlineTime, viewModel.endTime, rrule = null,
  originalCalendarId = calendarEvent.calendarId)
```
`rrule = null` removes recurrence from this instance, effectively detaching it from the series.

### 10.15 Smart Title Parsing (TASK mode only)

Applied in `parseSmartTitle()` before calling `onConfirm`:

| Token | Rule | Behavior |
|---|---|---|
| `@FolderName` | Case-insensitive folder name match | Sets `folderId`; token stripped from title |
| `#LabelName` | Case-insensitive label name match | Adds to `labelIds` if not already present; token stripped |
| `!1` | Literal | Sets `Priority.URGENT`; stripped |
| `!2` | Literal | Sets `Priority.IMPORTANT`; stripped |
| `!3` | Literal | Sets `Priority.NORMAL`; stripped |

Multiple spaces collapsed to single space. Unmatched tokens remain in the title.

### 10.16 Sub-Dialogs

| Dialog state | Trigger | Behavior |
|---|---|---|
| `showDeadlinePicker` | Date chip click | `DatePickerDialog` initialized from current `deadlineDate` as epoch millis (UTC). Monday-first locale enforced via `CompositionLocalProvider(LocalConfiguration)`. **Clear button sets both `deadlineDate=""` and `deadlineTime=""`** (clears time too). |
| `showTimePicker` | Start time chip click | `AlertDialog` with Material3 `TimePicker` (24 h). Default initial time 09:00 (or parsed from `deadlineTime`). On OK: sets `deadlineTime = "HH:MM"`; in EVENT mode, if new start time ≥ `endTime` → `viewModel.endTime = ""`. **Clear button sets `deadlineTime = ""`**. |
| `showEndTimePicker` | End time chip click | `EndTimePickerDialog(initialTime = viewModel.endTime, onConfirm = { viewModel.endTime = it }, onClear = { viewModel.endTime = "" }, onDismiss)` |
| `showFolderPicker` | Folder chip click (TASK only) | `FolderPickerDialog` — `AlertDialog` listing Inbox + all non-inbox folders as radio-style rows; selected folder row shows Check icon |
| `showLabelPicker` | Labels row click (TASK only) | `LabelPickerSheet` bottom sheet — multi-select with create-new option |
| `showEndsDatePicker` | "On" ends radio button (EVENT + recurring only) | `DatePickerDialog` → sets `viewModel.crsEndDate` |

### 10.17 TaskFormViewModel State

Extends `BaseViewModel`. Injected: `TaskRepository`, `CalendarRepository`.

| State | Type | Description |
|---|---|---|
| `formMode` | `FormMode` | TASK / EVENT |
| `endTime` | `String` | Event end time `"HH:MM"` or `""` |
| `selectedCalendarId` | `String` | Target calendar for event creation/update |
| `baseEventLoading` | `Boolean` | True while fetching series base event |
| `selectedCalendars` | `StateFlow<List<CalendarItem>>` | Writable calendars for dropdown |
| `calendarsLoading` | `StateFlow<Boolean>` | Loading spinner for calendar dropdown |
| `eventCreated` | `SharedFlow<String>` | Emitted on successful event create/update → sheet closes |
| `crsByDay` | `Set<DayOfWeek>` | Selected days for WEEKLY recurrence |
| `crsMonthlyIdx` | `Int` | Index into `monthlyOptions()` for MONTHLY recurrence |
| `crsEnds` | `EndsType` | NEVER / ON_DATE / AFTER_COUNT |
| `crsEndDate` | `LocalDate?` | End date for ON_DATE ends |
| `crsAfterCountStr` | `String` | Occurrence count string for AFTER_COUNT ends (default "13") |

---

## 11. Widgets

All widgets use Jetpack Glance. Data is fetched inside `provideContent` using `collectAsState()` from Room Flows, so widgets react to Room changes automatically. Widget locale is explicitly set to English for day names and month abbreviations (independent of device locale).

### 11.1 Shared Components

**WidgetHeader:** Row with widget title (left) and "+" button (right). "+" launches `stlertasks://create`.

**WidgetTaskRow:** Shared task row composable:
- Optional chevron (28dp) — expand/collapse subtasks via `ToggleExpandAction`
- Checkbox (36dp slot) — priority-colored border + surface fill → `CompleteTaskAction`
  - Pending-complete transient state: checkbox shows checkmark immediately via `PreferencesGlanceStateDefinition` key, before Room confirms
- Title (2-line max) → opens `stlertasks://task/{id}` deeplink
- Row 2: deadline · #labels · folder (each with its own color)
- `timeOnly: Boolean` — when true shows only time (Upcoming/Calendar widget where date is in section header)
- `showExpandSpace: Boolean` — when false omits the 28dp chevron spacer (TaskListWidget)

**WidgetEventRow:** Calendar event row:
- Same 36dp icon slot as checkbox — shows calendar icon tinted with calendar color
- Title (2-line max) → opens `stlertasks://event/{calendarId}/{id}` deeplink
- Row 2: time range · calendar name
- `timeOnly: Boolean` — when true hides date (already shown in section header)

**CompleteTaskAction:** Marks task complete in Room + enqueues UPDATE in SyncQueue + stores pending ID in Glance state + calls `WidgetRefresher.refreshAll()`.

**ToggleExpandAction:** Toggles `isExpanded` in Room (no sync queue entry).

### 11.2 Upcoming Widget
Root pending tasks + calendar events, `deadlineDate/startDate ≤ today + 7 days`, unified timeline sorted by date → timed-before-allday → time string. Grouped by date with `DateHeader` (e.g. `"16 Apr · Thursday · Today"`). Overdue section at top. `timeOnly = true`.

### 11.3 Folder Widget
Hierarchical: root tasks + expanded children (indented by `depth × 16dp`). Uses `isExpanded` from Room. `showExpandSpace = true`.

### 11.4 Task List Widget
Flat, root tasks only. Supports optional filter: folder / label / priority (configured per widget instance). `showExpandSpace = false`.

### 11.5 Calendar Widget
Mixed timeline of tasks + calendar events for the next 7 days (same logic as UpcomingWidget), rendered as a standalone fourth widget type. Configured in `WidgetConfigActivity` (auto-confirms, no config needed).

### 11.6 Widget Configuration (WidgetConfigActivity)
- Detects widget type via `AppWidgetManager.getAppWidgetInfo`
- **Upcoming / Calendar:** auto-confirms immediately (no config needed)
- **Folder:** RadioButton list of all folders
- **Task List:** 3 `ExposedDropdownMenuBox` filters (folder / label / priority)
- Saves config via `updateAppWidgetState(PreferencesGlanceStateDefinition)`

### 11.7 Widget Colors
All colors are named `ColorProvider` constants in `WidgetColors.kt`, backed by XML color resources in `res/values/colors.xml` and `res/values-night/colors.xml`.

| Constant | Light | Dark |
|---|---|---|
| WPrimary | #e07e38 | #e07e38 |
| WSurface | #FFFFFF | #1C1C1E |
| WOnSurface | #1C1C1E | #F2F2F7 |
| WOnSurfaceVariant | #8E8E93 | #8E8E93 |
| WDivider | #3C3C43 @18% | #3C3C43 @18% |
| WError | #F87171 | #F87171 |
| WPriorityUrgent | #F87171 | #F87171 |
| WPriorityImportant | #FB923C | #FB923C |
| WPriorityNormal | #9CA3AF | #9CA3AF |
| WDeadlineOverdue | #F87171 | #F87171 |
| WDeadlineToday | #16A34A | #16A34A |
| WDeadlineTomorrow | #FB923C | #FB923C |
| WDeadlineThisWeek | #A78BFA | #A78BFA |

---

## 12. Theme & Colors

### 12.1 App Colors (Color.kt)

| Constant | Light | Dark | Usage |
|---|---|---|---|
| Primary | #e07e38 | #e07e38 | Brand orange — buttons, active elements |
| PriorityUrgent | #F87171 | #F87171 | Urgent priority |
| PriorityImportant | #FB923C | #FB923C | Important priority |
| PriorityNormal | #9CA3AF | #9CA3AF | Normal priority |
| DeadlineOverdue | #F87171 | #F87171 | Past-due deadline |
| DeadlineToday | #16A34A | #16A34A | Due today |
| DeadlineTomorrow | #FB923C | #FB923C | Due tomorrow |
| DeadlineThisWeek | #A78BFA | #A78BFA | Due this week |
| SelectedHighlightLight | #D8D8D8 | — | Selected chip/item background (light) |
| SelectedHighlightDark | — | #515151 | Selected chip/item background (dark) |

**Unified highlight system:** `primaryContainer` in `Theme.kt` maps to `SelectedHighlightLight` (light) and `SelectedHighlightDark` (dark). This single slot controls: TimePicker selected boxes, FAB container, day-pill selection in Upcoming, filter chip selection in AllTasks, and sidebar selected item highlight.

### 12.2 Deadline Status Logic (TaskColors.kt)
```
deadlineStatus(dateStr):
  blank → NONE
  today → TODAY
  tomorrow → TOMORROW
  within 7 days → THIS_WEEK
  past → OVERDUE
  else → FUTURE

deadlineLabel(date, time, includeDate):
  if includeDate: "Mon, 16 Apr" or "Mon, 16 Apr · 14:30"
  else: time only "14:30"
```

### 12.3 Typography
Material 3 default typography scale. No custom fonts.

---

## 13. Navigation & Deeplinks

### 13.1 In-App Routes (Screen.kt)
| Route | Description |
|---|---|
| `upcoming` | Upcoming screen |
| `all-tasks` | All Tasks screen |
| `completed` | Completed screen |
| `folder/{folderId}` | Folder task list |
| `label/{labelId}` | Label task list |
| `priority/{priority}` | Priority task list (`urgent`/`important`/`normal`) |
| `calendar/{calendarId}` | Calendar screen — список событий одного календаря |

Navigation is handled by Compose Navigation (`NavHost` in `MainScreen`). The sidebar `ModalNavigationDrawer` calls `onNavigate(route)` → `navController.navigate(route)`.

### 13.2 Deeplinks
| URI | Action |
|---|---|
| `stlertasks://task/{taskId}` | Open edit form for the task |
| `stlertasks://create` | Open create form (Inbox, default priority) |
| `stlertasks://event/{calendarId}/{eventId}` | Open event edit form |
| `stlertasks://upcoming` | Navigate to Upcoming screen |

Declared in `AndroidManifest.xml`. `MainActivity` receives the intent and passes the URI to `MainScreen`, where a `LaunchedEffect` routes to the correct action.

---

## 14. Loading States & Empty States

### Loading (ShimmerTaskList)
- `isLoading: StateFlow<Boolean>` in every ViewModel: `filteredTasks.map { false }.stateIn(..., initialValue = true)`
- `ShimmerTaskList(itemCount = 6)`: pulsing skeleton rows (alpha 0.25↔0.6, 900ms, LinearEasing, Reverse)
- Each skeleton row: 40dp expand spacer + 18dp checkbox square + title bar (varied width per index)

### Empty States (EmptyState)
- Centered column: 64dp icon (40% opacity) + message (titleMedium) + optional subtitle (bodyMedium, muted)
- Used in: AllTasksScreen, CompletedScreen, PriorityScreen, LabelScreen

---

## 15. CI/CD

**Workflow:** `.github/workflows/release.yml`  
**Trigger:** Push of a tag matching `v*` (e.g. `v1.1`)

**Steps:**
1. Checkout code
2. Set up JDK 11
3. Decode keystore from `KEYSTORE_BASE64` secret → write to temp file
4. Set env vars: `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
5. `./gradlew assembleRelease`
6. Upload signed APK as GitHub Release artifact

**Signing:** `app/build.gradle.kts` reads `KEYSTORE_PATH` env var; signing config is only created when the var is set (safe for local debug builds without a keystore).

---

## 16. First-Time Setup (New Developer)

1. Clone the repository
2. In Google Cloud Console:
   - Create (or reuse) a project
   - Enable **Google Sheets API**, **Google Drive API**, and **Google Calendar API**
   - Create **OAuth 2.0 Web Client ID** → copy to `app/src/main/res/values/strings.xml` as `google_web_client_id`
   - Create **OAuth 2.0 Android Client ID** (package `com.stler.tasks`, SHA-1 of debug keystore)
   - Set OAuth consent screen to **Production** (so any Google account can sign in)
   - Add scopes: `spreadsheets`, `drive.metadata.readonly`, `calendar`
3. Run on device/emulator from Android Studio
4. Sign in — the app will find or create the `db_tasks` spreadsheet automatically

**For release builds:** Create a keystore, base64-encode it, add GitHub secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Push a `v*` tag to trigger the build.

---

## 17. Key Algorithms

### Recurring Task Completion
```kotlin
if (task.isRecurring) {
    val newDate = when (task.recurType) {
        DAILY   -> LocalDate.parse(task.deadlineDate).plusDays(task.recurValue.toLong())
        WEEKLY  -> LocalDate.parse(task.deadlineDate).plusWeeks(task.recurValue.toLong())
        MONTHLY -> LocalDate.parse(task.deadlineDate).plusMonths(task.recurValue.toLong())
    }
    // update deadlineDate only; status stays PENDING; completedAt = ""
} else {
    // set status = COMPLETED, completedAt = now
    // recursively complete all descendants
}
```

### FolderScreen Tree Flattening
`FolderViewModel` builds a `List<DisplayNode>` from Room tasks:
1. Build a map `parentId → children` (only PENDING tasks)
2. DFS from root tasks (parentId == "") respecting `isExpanded`
3. Each node carries `depth`, `childCount`, `completedChildCount`
4. `localList` (optimistic reorder) is a mutable copy updated on drag; DB write happens only on drop

### Drag-to-Reorder (FolderScreen)
- `pendingReorder` object (not `mutableStateOf` — no recomposition on intermediate frames): stores `parentId`, `fromIdx`, `toIdx`
- `onMove` callback: updates `localList` + updates `pendingReorder`
- `LaunchedEffect(isDragging)`: when `isDragging → false`, calls `viewModel.reorderSiblings(parentId, from, to)` → single batch Room write + sync queue entry

### Upcoming / Widget Timeline Merge
Tasks and calendar events are merged into a unified `TimelineEntry` list:
```
TimelineEntry(date, hasTime, time, row: UpcomingRow)
```
Sorted by: `date` → `if (hasTime) 0 else 1` → `time` string.
Grouped by date with a `Header` row inserted before each group.

### EventDateTime Serialization
Calendar API requires exactly one of `date` or `dateTime` per event boundary (never both):
```
All-day:  EventDateTime(date = "YYYY-MM-DD")
Timed:    EventDateTime(dateTime = "2026-05-04T14:00:00+03:00", timeZone = "Europe/Helsinki")
```
The custom `EventDateTimeAdapter` serializes with `serializeNulls=false` so absent fields are omitted from the JSON body, satisfying both regular and recurring event requirements.

### RRULE Builder (CustomRecurrenceSheet)
```
RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR;COUNT=10
RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=2TU        (2nd Tuesday)
RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=-1SA       (last Saturday)
RRULE:FREQ=DAILY;INTERVAL=1;UNTIL=20261231T235959Z
```
Ordinal prefix: `ceil(dayOfMonth / 7.0).toInt()` for 1st–4th; `-1` when the day can fall in the last position (dayOfMonth > 21 and day would overflow next month).
