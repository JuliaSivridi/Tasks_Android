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
Calendar event mutations (create / update / delete) bypass the SyncQueue entirely and go directly to the Calendar API:
- **Create:** `CalendarRepository.createEvent(calendarId, CalendarEventRequest)` → `PUT` to `events` endpoint → on success, fetches and upserts new event to Room
- **Update:** `CalendarRepository.updateEvent(calendarId, eventId, CalendarEventRequest)` → `PUT` (replaces entire event, avoids residual fields from PATCH merge)
- **Delete:** `CalendarRepository.deleteEvent(calendarId, eventId)` → `DELETE` → removes from Room
- **Move calendar:** `CalendarRepository.moveEvent(fromCalendarId, toCalendarId, eventId)` → `POST .../move` endpoint

**EventDateTime serialization:** A custom Gson `TypeAdapter` (`EventDateTimeAdapter`) is registered in `CalendarModule`. It omits null fields by default (Gson `serializeNulls=false`), ensuring only `date` or only `dateTime` is sent — never both. The `timeZone` field (IANA name, e.g. `"Europe/Helsinki"`) is included for timed events and required by the Calendar API for recurring timed events.

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
- Filter pills: priority chips + label chips + folder chip
- Tasks and calendar events unified into a date-grouped timeline (`"16 Apr · Thursday · Today"`)
- **Overdue section** — all past-due tasks/events collapsed into one group above the date strip
- `showDateInDeadline = false` on `TaskItem` (date shown in section header, only time shown in row 2)
- `isLoading` StateFlow → shimmer skeleton on first load

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

Single bottom sheet for task creation, task editing, and calendar event creation/editing. Mode is set by `TaskFormViewModel.formMode` (a `mutableStateOf<FormMode>`).

### 10.1 Form Modes

**TASK mode** (default): Title → Deadline (date + optional time) → Repeat → Folder → Labels → Priority  
**EVENT mode**: Title → Date → Start time / End time → Repeat → Calendar picker

Mode toggle: `SingleChoiceSegmentedButtonRow` with Task / Event segments, shown at the top of the sheet.

### 10.2 TASK mode
**Fields (in order):** Title → Deadline → Repeat → Folder → Labels → Priority

**Smart parsing in title field:** `@FolderName` sets folder (stripped from title); `#LabelName` adds label (stripped from title). Only applied in TASK mode.

**Repeat row:** Checkbox + "Every [N] [days/weeks/months]" — hidden until a deadline date is selected.

**Folder selector:** Scrollable popup, single select; pre-filled from current screen context.

**Labels:** Opens `LabelPickerSheet` bottom sheet on tap; selected labels shown as chips below the row.

**Edit extras:** Delete task button (destructive, confirm dialog).

### 10.3 EVENT mode
**Fields (in order):** Title → Date chip → Start time chip — End time chip → Repeat → Custom RRULE → Calendar dropdown

**End time picker:** `EndTimePickerDialog` (Material3 TimePicker wrapper). Clear button removes the end time.

**Custom recurrence:** `CustomRecurrenceSheet` bottom sheet. Builds a full RRULE string:
- Frequency: Daily / Weekly / Monthly / Yearly
- Interval: 1–99
- Weekly: BYDAY (multi-select Mon–Sun)
- Monthly: by day-of-month / by ordinal weekday / by last weekday
- Ends: Never / On date / After N occurrences

**Calendar dropdown:** `ExposedDropdownMenuBox` listing only calendars the user has write access to (accessRole `writer` or `owner`). Calendars loaded once via `loadCalendars()` guard.

**Submit:** Calls `TaskFormViewModel.createEvent()` or `updateEvent()`. On success, `_eventCreated` SharedFlow emits → sheet closes.

**Editing recurring events:** Moving to EVENT edit opens `loadBaseEvent()` to fetch the series base event data (title, start, recurrence) and pre-fills the form with those values.

### 10.4 TaskFormViewModel
Extends `BaseViewModel`. Injected: `TaskRepository`, `CalendarRepository`.

| State | Type | Description |
|---|---|---|
| formMode | FormMode | TASK / EVENT |
| endTime | String | Event end time `"HH:MM"` or `""` |
| selectedCalendarId | String | Target calendar for event creation |
| baseEventLoading | Boolean | True while fetching series base event |
| selectedCalendars | StateFlow\<List\<CalendarItem\>\> | Writable calendars for dropdown |
| calendarsLoading | StateFlow\<Boolean\> | Loading spinner for dropdown |
| eventCreated | SharedFlow\<String\> | Emitted on successful event create/update |

**Recurrence state** (persists between form openings): `crsByDay`, `crsMonthlyIdx`, `crsEnds`, `crsEndDate`, `crsAfterCountStr`.

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
