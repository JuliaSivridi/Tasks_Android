# Stler Tasks Android — Technical Specification

**Version:** 2.1 (May 2026)  
**Repository:** github.com/JuliaSivridi/Tasks_Android  
**Stack:** Kotlin · Jetpack Compose · Room · Hilt · WorkManager · Glance · Google Sheets API v4 · Google Calendar API v3  
**Min SDK:** 26 (Android 8.0) · **Target SDK:** 36

---

## 1. Overview

Stler Tasks is a personal task manager for Android. It is a native rewrite of a PWA with the same Google Sheets backend, so both apps share one `db_tasks` spreadsheet per user. There is no dedicated backend server — all persistent task data lives in Google Sheets, accessed via the Sheets API v4. Room serves as a local cache that makes the app fully functional offline.

In addition to tasks, the app integrates with **Google Calendar API v3**: events from selected calendars are fetched, cached in Room, and displayed alongside tasks in the Upcoming screen, Calendar screen, and all four widgets.

**Key design goals:**
- Google Sheets as the single source of truth for tasks — no proprietary cloud service
- Full offline support via Room + sync queue
- Reactive UI — all screens observe Room Flows; data updates propagate automatically
- Clean MVVM + Repository architecture with Hilt DI throughout
- Glance widgets that react to Room changes without explicit refresh calls
- Google Calendar events surfaced inline with tasks, unified by date

**Locale override:** `MainActivity.attachBaseContext()` forces `en-GB` locale for the entire activity. This locks UI strings to English and sets Monday as the first day of week (used by the Material3 DatePicker via `Locale.getDefault()`).

---

## 2. Tech Stack

| Layer | Library | Version | Notes |
|---|---|---|---|
| Language | Kotlin | — | JVM toolchain 11 |
| UI | Jetpack Compose + Material 3 | BOM (platform) | Compose-only, no XML layouts |
| DI | Hilt | — | KSP processor; `@HiltAndroidApp`, `@HiltViewModel`, `@HiltWorker` |
| Local DB | Room | — | version 8; explicit migrations 4→5→6→7→8 |
| Networking | Retrofit + OkHttp | — | Bearer token interceptor + 401 Authenticator |
| Serialization | Gson | — | Retrofit converter + SyncQueue payload serialization |
| Background | WorkManager (Hilt) | — | Periodic sync (30 min) + one-off manual sync |
| Widgets | Glance (`glance-appwidget`, `glance-material3`) | — | 4 widget types |
| Auth | CredentialManager + Identity API | — | Google Sign-In + scope authorization |
| Preferences | DataStore (`datastore-preferences`) | — | Token, user info, spreadsheet ID, calendar IDs |
| Image loading | Coil (`coil-compose`) | — | User avatar in TopAppBar |
| Drag & drop | `reorderable` (sh.calvin) | — | FolderScreen drag-to-reorder |
| Coroutines | `kotlinx.coroutines-android` + `-play-services` | — | |
| Navigation | Navigation Compose | — | Single NavHost inside MainScreen |
| Lifecycle | Lifecycle ViewModel / Runtime | — | `WhileSubscribed(5000)` sharing strategy |

**Build config:** `applicationId = "com.stler.tasks"`, `versionCode = 21`, `versionName = "2.1"`, `minSdk = 26`, `targetSdk = 36`. KSP with `room.schemaLocation = "$projectDir/schemas"`. Signing via environment variables `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (only wired if `KEYSTORE_PATH` is non-blank, so debug builds are unaffected).

---

## 3. Architecture

**Pattern:** MVVM + Repository

```
┌──────────────────────────────────────────────────────────────┐
│                         UI Layer                             │
│  Composables ← ViewModel (StateFlow) ← Repository           │
└──────────────────────────────────────────────────────────────┘
         │                       │
         │ suspend fun            │ Flow<List<T>>
         ▼                       ▼
┌─────────────────────┐  ┌──────────────────────────────────┐
│  TaskRepositoryImpl │  │  CalendarRepositoryImpl          │
│  (write path)       │  │  (Calendar API + Room cache)     │
└──────────┬──────────┘  └──────────────────────────────────┘
           │
    ┌──────┴──────────────────────────────┐
    │               Room DAOs              │
    │  TaskDao / FolderDao / LabelDao /    │
    │  SyncQueueDao / CalendarEventDao     │
    └──────┬──────────────────────────────┘
           │                          ▲
    SyncQueue                         │
           │                     SyncWorker (WorkManager)
           │                          │
           ▼                          │
    Google Sheets API v4         Google Calendar API v3
    (SheetsApi via Retrofit)     (CalendarApi via Retrofit)
```

### Write path (task mutation)

1. ViewModel calls `repository.createTask(task)` / `updateTask` / `deleteTask` / etc.
2. Repository writes to Room via DAO (`taskDao.upsert(entity)`).
3. Repository enqueues a `SyncQueueEntity` (INSERT / UPDATE / DELETE) via `syncQueueDao.enqueue()`.
4. Repository calls `widgetRefresher.refreshAll()` (debounced 400 ms fire-and-forget).
5. Room Flow emits → all observing ViewModels recompose → UI updates immediately (offline-first).
6. On next sync trigger, `SyncWorker.push()` drains the queue to Sheets API.

### Read path

1. Screen collects `StateFlow` from ViewModel (backed by `stateIn(WhileSubscribed(5000))`).
2. ViewModel observes Room DAO Flow (e.g. `taskDao.observeAllPending()`).
3. `SyncWorker.pull()` calls `taskRepository.fetchAllAndSave()` → `sheetsApi.batchGet()` → `db.withTransaction { taskDao.upsertAll(...) }`.
4. Room emits new data → ViewModels recompose → UI updates.

### Error handling

- All repository operations are wrapped in `runCatching` or expose `Result<T>` to callers.
- ViewModels extend `BaseViewModel` which exposes `uiError: SharedFlow<String>` via `safeLaunch { }`.
- `SyncWorker` returns `Result.retry()` on exception for up to 4 attempts (5th returns `Result.failure()`).
- `SyncQueueDao.deleteExhausted(maxRetries = 5)` removes items that have failed 5+ times.
- On 401 from OkHttp, the `Authenticator` calls `tokenProvider.refreshToken()` once; returns null if refresh fails.

---

## 4. Package / Folder Structure

```
com.stler.tasks/
├── auth/                    Google auth repository, DataStore preferences, AuthData model
├── data/
│   ├── local/
│   │   ├── dao/             TaskDao, FolderDao, LabelDao, SyncQueueDao, CalendarEventDao
│   │   ├── entity/          Room entities + toDomain() / toEntity() mappers
│   │   └── TaskDatabase.kt  RoomDatabase v8, migration objects
│   ├── remote/
│   │   ├── SheetsApi.kt     Retrofit interface (batchGet, append, batchUpdate, clear)
│   │   ├── SheetsMapper.kt  Row ↔ Entity conversions; date serial handling
│   │   ├── CalendarApi.kt   Retrofit interface (Calendar API v3)
│   │   ├── CalendarMapper.kt RFC3339 parsing, DTO → entity
│   │   ├── TokenProvider.kt Interface implemented by GoogleAuthRepository
│   │   └── dto/             Gson DTOs for Sheets, Calendar, Drive responses
│   └── repository/
│       ├── TaskRepository.kt          Interface
│       ├── TaskRepositoryImpl.kt      Mutations + pull
│       ├── CalendarRepository.kt      Interface
│       └── CalendarRepositoryImpl.kt  CRUD + fetch/cache
├── di/
│   ├── DatabaseModule.kt    Room, all DAOs
│   └── NetworkModule.kt     OkHttpClient, Retrofit, SheetsApi, Gson
├── domain/model/            Task, Folder, Label, CalendarEvent, CalendarItem, ListItem, SyncState
├── sync/
│   ├── SyncState.kt         Sealed class: Idle / Syncing / Pending(count)
│   ├── SyncManager.kt       WorkManager scheduling + syncState Flow
│   └── SyncWorker.kt        Push + pull + calendar sync
├── ui/
│   ├── auth/                AuthScreen, AuthViewModel, AuthUiState
│   ├── main/                MainScreen (NavHost + drawer), MainViewModel, TasksTopAppBar, SidebarMenu
│   ├── upcoming/            UpcomingScreen + UpcomingViewModel
│   ├── alltasks/            AllTasksScreen + AllTasksViewModel + FilterBar
│   ├── completed/           CompletedScreen + CompletedViewModel
│   ├── folder/              FolderScreen (drag-reorder) + FolderViewModel
│   ├── label/               LabelScreen + LabelViewModel
│   ├── priority/            PriorityScreen + PriorityViewModel
│   ├── calendar/            CalendarScreen + CalendarViewModel + CalendarEventItem
│   ├── task/                TaskFormSheet, TaskFormViewModel, TaskItem, TaskColors, pickers
│   ├── settings/            SettingsScreen + SettingsViewModel
│   ├── help/                HelpScreen (static content)
│   ├── feedback/            FeedbackScreen + FeedbackViewModel
│   ├── navigation/          Screen.kt (route constants)
│   ├── theme/               Color.kt, Theme.kt, Type.kt
│   └── util/                EmptyState, ShimmerTaskList, ErrorSnackbarEffect, LocalSnackbarHostState
├── widget/
│   ├── UpcomingWidget.kt    GlanceAppWidget — 7-day timeline
│   ├── FolderWidget.kt      GlanceAppWidget — single folder, hierarchical
│   ├── TaskListWidget.kt    GlanceAppWidget — flat list with filters
│   ├── CalendarWidget.kt    GlanceAppWidget — calendar events
│   ├── WidgetRefresher.kt   Debounced updateAll() coordinator
│   ├── WidgetPrefs.kt       SharedPreferences per widget instance
│   ├── WidgetColors.kt      ColorProvider constants for Glance
│   └── WidgetTaskRow.kt     Shared row composable for task rows in widgets
├── AppContainer.kt          Manual DI container for non-Hilt singletons (retained)
├── MainActivity.kt          Locale override, auth gate, deep link handler
└── TasksApplication.kt      Application; initializes SyncManager + WidgetRefresher
```

---

## 5. Data Model

### Task

| Field | Type | Default | Description |
|---|---|---|---|
| id | String | — | Primary key, e.g. `tsk_a1b2c3d4` |
| parentId | String | `""` | Parent task ID; `""` = root task |
| folderId | String | `"fld-inbox"` | Owning folder ID |
| title | String | — | Required task description |
| status | TaskStatus | `PENDING` | `PENDING` or `COMPLETED` |
| priority | Priority | `NORMAL` | `URGENT`, `IMPORTANT`, or `NORMAL` |
| deadlineDate | String | `""` | ISO date `"YYYY-MM-DD"` or `""` |
| deadlineTime | String | `""` | `"HH:MM"` or `""` |
| isRecurring | Boolean | `false` | Whether the task recurs |
| recurType | RecurType | `NONE` | `NONE`, `DAYS`, `WEEKS`, `MONTHS`, `YEARS` |
| recurValue | Int | `1` | Recurrence interval |
| labels | List\<String\> | `[]` | List of label IDs (domain); comma-separated string in entity/Sheets |
| sortOrder | Int | `0` | Position within sibling list (multiples of 10 after reorder) |
| createdAt | String | `""` | ISO 8601 instant (`Instant.now().toString()`) |
| updatedAt | String | `""` | ISO 8601 instant |
| completedAt | String | `""` | ISO 8601 instant or `""` |
| isExpanded | Boolean | `false` | Whether subtree is expanded in FolderScreen |

**Invariants:**
- `isRoot = parentId.isEmpty()` (computed property on domain model)
- Completing a recurring task advances `deadlineDate` by `recurValue × recurType` instead of marking it `COMPLETED`
- Completing a non-recurring task recursively completes all non-deleted descendants
- Deleting a task sets `status = "deleted"` and recursively sets descendants to `"deleted"` (soft delete)
- `deleted` tasks are filtered from all DAO queries (queries check `status = 'pending'` or `status = 'completed'`)

### Folder

| Field | Type | Default | Description |
|---|---|---|---|
| id | String | — | Primary key, e.g. `fld-inbox`, `fld_a1b2c3d4` |
| name | String | — | Display name |
| color | String | — | Hex color string, e.g. `"#f97316"` |
| sortOrder | Int | `0` | Display order in sidebar |

**Invariants:**
- `isInbox = id == "fld-inbox"` — the Inbox folder always appears first in `FolderDao.observeAll()` (SQL `CASE id WHEN 'fld-inbox' THEN 0 ELSE 1 END`)
- Deleting a folder moves all non-deleted tasks to `fld-inbox` in a single batch

### Label

| Field | Type | Default | Description |
|---|---|---|---|
| id | String | — | Primary key, e.g. `lbl_a1b2c3d4` |
| name | String | — | Display name |
| color | String | — | Hex color string |
| sortOrder | Int | `0` | Column D in labels sheet |

### CalendarEvent

| Field | Type | Default | Description |
|---|---|---|---|
| id | String | — | Google Calendar event ID |
| calendarId | String | — | Google Calendar ID (e.g. `"primary"` or email) |
| calendarName | String | — | Human-readable calendar name |
| calendarColor | String | — | Hex color from Calendar API |
| title | String | — | Event summary; `"(No title)"` if blank |
| startDate | String | — | `"YYYY-MM-DD"` |
| startTime | String | — | `"HH:MM"` or `""` for all-day |
| endDate | String | — | `"YYYY-MM-DD"` |
| endTime | String | — | `"HH:MM"` or `""` for all-day |
| isAllDay | Boolean | — | True when `start.dateTime` is null in API response |
| recurringEventId | String | `""` | Base event ID of recurring series; `""` if one-off |
| isEditable | Boolean | `true` | False for shared read-only calendars (access role not `owner`/`writer`) |

**Computed:** `isRecurring = recurringEventId.isNotBlank()`

### SyncQueueEntity

| Field | Type | Default | Description |
|---|---|---|---|
| id | Long | auto-generate | Primary key |
| entityType | String | — | `"task"`, `"folder"`, `"label"`, or `"settings"` |
| operation | String | — | `"INSERT"`, `"UPDATE"`, or `"DELETE"` |
| entityId | String | — | ID of the affected entity |
| payloadJson | String | — | Gson-serialized entity; empty for DELETE |
| retryCount | Int | `0` | Incremented on failure; items with `retryCount >= 5` are deleted |

---

## 6. Database Schema

**Database name:** `tasks.db`  
**Room version:** 8  

### Table: `tasks`

| Column | SQLite Type | Notes |
|---|---|---|
| id | TEXT NOT NULL PRIMARY KEY | |
| parentId | TEXT NOT NULL | Default `''`; indexed |
| folderId | TEXT NOT NULL | Default `'fld-inbox'`; indexed |
| title | TEXT NOT NULL | |
| status | TEXT NOT NULL | `'pending'` / `'completed'`; indexed |
| priority | TEXT NOT NULL | `'urgent'` / `'important'` / `'normal'` |
| deadlineDate | TEXT NOT NULL | `'YYYY-MM-DD'` or `''`; indexed |
| deadlineTime | TEXT NOT NULL | `'HH:MM'` or `''` |
| isRecurring | INTEGER NOT NULL | 0/1 |
| recurType | TEXT NOT NULL | `'days'`/`'weeks'`/`'months'`/`'years'`/`''` |
| recurValue | INTEGER NOT NULL | Default 1 |
| labels | TEXT NOT NULL | Comma-separated label IDs or `''` |
| sortOrder | INTEGER NOT NULL | |
| createdAt | TEXT NOT NULL | ISO instant |
| updatedAt | TEXT NOT NULL | ISO instant |
| completedAt | TEXT NOT NULL | ISO instant or `''` |
| isExpanded | INTEGER NOT NULL | 0/1 |

**Indices:** `parentId`, `folderId`, `status`, `deadlineDate`

### Table: `folders`

| Column | SQLite Type | Notes |
|---|---|---|
| id | TEXT NOT NULL PRIMARY KEY | |
| name | TEXT NOT NULL | |
| color | TEXT NOT NULL | hex |
| sortOrder | INTEGER NOT NULL | Default 0 |

### Table: `labels`

| Column | SQLite Type | Notes |
|---|---|---|
| id | TEXT NOT NULL PRIMARY KEY | |
| name | TEXT NOT NULL | |
| color | TEXT NOT NULL | hex |
| sortOrder | INTEGER NOT NULL | Default 0 |

### Table: `sync_queue`

| Column | SQLite Type | Notes |
|---|---|---|
| id | INTEGER PRIMARY KEY AUTOINCREMENT | |
| entityType | TEXT NOT NULL | |
| operation | TEXT NOT NULL | |
| entityId | TEXT NOT NULL | |
| payloadJson | TEXT NOT NULL | |
| retryCount | INTEGER NOT NULL | Default 0 |

### Table: `calendar_events`

| Column | SQLite Type | Notes |
|---|---|---|
| id | TEXT NOT NULL PRIMARY KEY | |
| calendarId | TEXT NOT NULL | indexed |
| calendarName | TEXT NOT NULL | |
| calendarColor | TEXT NOT NULL | |
| title | TEXT NOT NULL | |
| startDate | TEXT NOT NULL | |
| startTime | TEXT NOT NULL | |
| endDate | TEXT NOT NULL | |
| endTime | TEXT NOT NULL | |
| isAllDay | INTEGER NOT NULL | |
| recurringEventId | TEXT NOT NULL | Default `''`; indexed |
| isEditable | INTEGER NOT NULL | Default 1 |

**Indices:** `calendarId`, `recurringEventId`

### Migration History

| From→To | Change |
|---|---|
| 4→5 | Added `calendar_events` table |
| 5→6 | `ALTER TABLE calendar_events ADD COLUMN recurringEventId TEXT NOT NULL DEFAULT ''` |
| 6→7 | Added indices on `calendarId` and `recurringEventId` |
| 7→8 | `ALTER TABLE calendar_events ADD COLUMN isEditable INTEGER NOT NULL DEFAULT 1` |

`fallbackToDestructiveMigration(dropAllTables = true)` is set as a safety net for unexpected version gaps.

---

## 7. Google Sheets Schema

All reads use `valueRenderOption = UNFORMATTED_VALUE`. All writes use `valueInputOption = RAW`. Rows with a blank `id` column (col A) are skipped by `SheetsMapper.rowToTask()` / `rowToFolder()` / `rowToLabel()`.

### Sheet: `tasks` (columns A–Q)

| Col | Header | Format / Values |
|---|---|---|
| A | id | String, e.g. `tsk_a1b2c3d4` |
| B | parent_id | String or `""` |
| C | folder_id | String, e.g. `fld-inbox` |
| D | title | String |
| E | status | `"pending"` or `"completed"` |
| F | priority | `"urgent"`, `"important"`, or `"normal"` |
| G | deadline_date | `"YYYY-MM-DD"` string (RAW write); may be read back as Sheets serial number (double) when written via USER_ENTERED elsewhere — `SheetsMapper.dateStr()` converts serial to ISO format |
| H | deadline_time | `"HH:MM"` or `""` |
| I | is_recurring | `"TRUE"` or `"FALSE"` |
| J | recur_type | `"days"`, `"weeks"`, `"months"`, `"years"`, or `""` |
| K | recur_value | Integer string, e.g. `"1"` |
| L | labels | Comma-separated label IDs or `""` |
| M | sort_order | Integer string |
| N | created_at | ISO 8601 instant string |
| O | updated_at | ISO 8601 instant string |
| P | completed_at | ISO 8601 instant string or `""` |
| Q | is_expanded | `"TRUE"` or `"FALSE"` |

### Sheet: `folders` (columns A–D)

| Col | Header | Format |
|---|---|---|
| A | id | String |
| B | name | String |
| C | color | Hex string e.g. `"#f97316"` |
| D | sort_order | Integer string |

### Sheet: `labels` (columns A–D)

| Col | Header | Format |
|---|---|---|
| A | id | String |
| B | name | String |
| C | color | Hex string |
| D | sort_order | Integer string |

### Sheet: `settings` (index 3)

Present in spreadsheet structure (created at onboarding); not currently read/written by the Android app.

### Sheet: `meta` (index 4)

Present in spreadsheet structure (created at onboarding); not currently read/written by the Android app.

**Row-number lookup for UPDATE/DELETE:** `SheetsMapper.findRowNumber(rows, id)` iterates rows, skips the header (index 0), and returns the 1-based row number when `row[0] == id`. Returns null if not found (operation is skipped).

---

## 8. Authentication & First-Launch Setup

### Auth Flow (numbered steps)

1. **`AuthViewModel` init** — reads `GoogleAuthRepository.isSignedIn` (one-shot `first()`). If already signed in, emits `AuthUiState.SignedIn`; otherwise emits `AuthUiState.SignedOut`.
2. **User taps "Sign in with Google"** → `AuthViewModel.startSignIn(context)` → `GoogleAuthRepository.signIn(context)`.
3. **Step 1 — Google ID Token:** `CredentialManager.getCredential()` with `GetGoogleIdOption` (all accounts, not just pre-authorized). Returns `GoogleIdTokenCredential` containing `id` (email), `displayName`, `profilePictureUri`.
4. **Step 2 — Scope authorization:** `Identity.getAuthorizationClient(context).authorize(...)` requesting scopes: `https://www.googleapis.com/auth/spreadsheets`, `https://www.googleapis.com/auth/drive.metadata.readonly`, `https://www.googleapis.com/auth/calendar`.
5. **If `hasResolution()` is true** — user has not yet approved the scopes. User info is saved to DataStore (without token/spreadsheetId). `AuthUiState.NeedsAuthorization(pendingIntent)` is emitted. `AuthScreen` launches the intent via `ActivityResultContracts.StartIntentSenderForResult`. On `RESULT_OK`, `AuthViewModel.finalizeAuth(intent)` is called.
6. **If no resolution needed** (scopes already approved) — `accessToken` is extracted directly. Proceeds to step 7.
7. **`completeSignIn(token, credential)` / `finalizeAuth(intent)`** — calls `findOrCreateSpreadsheetWithName(token)` → Drive API search. If `db_tasks` found, returns its ID. If not found, calls `createSpreadsheet(token)`.
8. **`createSpreadsheet(token)`** — via raw OkHttp (avoids circular Hilt dependency):
   - POST to `https://sheets.googleapis.com/v4/spreadsheets` with body creating 5 sheets: `tasks` (index 0), `folders` (index 1), `labels` (index 2), `settings` (index 3), `meta` (index 4).
   - POST to `.../values:batchUpdate` writing: header row for tasks (A1:Q1), header row for folders, header row for labels, plus onboarding seed data (see below).
   - Upserts seed data to Room so the app is immediately usable without waiting for sync.
9. **`authPreferences.saveAll()`** — persists `accessToken`, `tokenExpiry` (now + 1 hour), `spreadsheetId`, `spreadsheetName`, `userEmail`, `userName`, `userAvatarUrl`.
10. `AuthUiState.SignedIn` is emitted → `MainActivity` shows `MainScreen`.

### Onboarding Seed Data

Created in both Sheets and Room when a new `db_tasks` spreadsheet is created:

**Folders:**
- `fld-inbox` / Inbox / `#6b7280` / order 0
- `fld-work` / Work / `#4A90D9` / order 1
- `fld-personal` / Personal / `#7ED321` / order 2

**Labels:**
- `lbl-review` / Review / `#F5A623` / order 1
- `lbl-idea` / Idea / `#9B59B6` / order 2

**Tasks (5):**
- `tsk-seed-1` in Work — "Make important call" — priority: important
- `tsk-seed-2` in Work — "Review project draft" — priority: normal — label: `lbl-review`
- `tsk-seed-3` in Personal — "Buy groceries" — priority: urgent
- `tsk-seed-4` in Personal — "Schedule dentist appointment" — priority: normal — deadline: now + 5 days
- `tsk-seed-5` in Inbox — "Plan the week" — priority: normal — label: `lbl-idea`

### Token Refresh

`isExpiredSoon(expiry)` returns true if `Instant.now()` is within 300 seconds of the stored expiry. Refresh is done via `Identity.getAuthorizationClient(context).authorize(buildAuthRequest())` in `refreshToken()`. If `hasResolution()` is true (interactive re-auth needed), returns null. On success, saves new token with new 1-hour expiry. The OkHttp `Authenticator` calls `refreshToken()` once on 401.

### Sign-Out

`GoogleAuthRepository.signOut()`: clears all DataStore prefs, then deletes all rows from `tasks`, `folders`, `labels`, and `sync_queue` tables. Calendar events are NOT explicitly cleared (they are pure cache and contain no user-generated data).

---

## 9. Synchronization

### SyncState

```kotlin
sealed class SyncState {
    data object Idle : SyncState()         // cloud-check icon in TopAppBar
    data object Syncing : SyncState()      // spinning icon
    data class Pending(val count: Int) : SyncState()  // cloud-upload + badge
}
```

`SyncManager.syncState` combines:
- `WorkManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK_NAME)` — running state
- `WorkManager.getWorkInfosForUniqueWorkFlow(MANUAL_WORK_NAME)` — running state
- `syncQueueDao.observePendingCount()` — pending item count

### Periodic Sync

`SyncManager.initialize()` (called from `TasksApplication.onCreate()`) enqueues a `PeriodicWorkRequest` with 30-minute interval, `ExistingPeriodicWorkPolicy.KEEP`, and `NetworkType.CONNECTED` constraint. Name: `"StlerTasksPeriodicSync"`.

### Manual Sync

`SyncManager.triggerSync()` enqueues a `OneTimeWorkRequest` with `ExistingWorkPolicy.REPLACE` and `NetworkType.CONNECTED`. Name: `"StlerTasksManualSync"`. Also triggered automatically from `MainViewModel.init { syncManager.triggerSync() }`.

### SyncWorker Execution Order

1. Read `spreadsheetId` from DataStore. If blank, call `authRepository.findAndSaveSpreadsheetId()` (Drive search).
2. If still blank, return `Result.success()` (abort silently).
3. **Push phase** — drain `SyncQueue`:
   - Load all queue items in order.
   - Per item: `executeOperation(item, spreadsheetId, rowsCache)`.
   - On success: `syncQueueDao.deleteById(item.id)`.
   - On failure: `syncQueueDao.incrementRetry(item.id)`.
   - After all items: `syncQueueDao.deleteExhausted(maxRetries = 5)`.
   - Row cache: sheet rows are fetched once per sheet name per push run (lazy, keyed by sheet name).
4. If push had pending changes, `delay(1_000L)` to avoid reading stale cached Sheets data.
5. **Pull phase** — `taskRepository.fetchAllAndSave(spreadsheetId)`:
   - `sheetsApi.batchGet(spreadsheetId, listOf("tasks", "folders", "labels"))`.
   - Collect IDs of items still in the sync queue (pending unsent changes → these rows are skipped to prevent local edits being overwritten).
   - `db.withTransaction { taskDao.upsertAll(...); folderDao.upsertAll(...); labelDao.upsertAll(...) }`.
6. **Calendar sync** — if selected calendar IDs are non-empty:
   - `calendarRepository.fetchEventsAndSave(selectedIds, from = now - 1 day, to = now + 366 days)`.
   - Internally: `calendarApi.listCalendars()` (populates metadata cache), then per-calendar `listEvents(timeMin, timeMax)`.
   - Per calendar: `calendarEventDao.deleteAndReplace(calendarId, entities)` (atomic @Transaction).
7. Return `Result.success()`.

On exception: `Result.retry()` for `runAttemptCount < 4`, else `Result.failure()`.

### SheetsApi Endpoints

| Operation | HTTP | Path | Notes |
|---|---|---|---|
| Pull (read all) | GET | `.../values:batchGet?ranges=tasks,folders,labels` | `valueRenderOption=UNFORMATTED_VALUE` |
| INSERT | POST | `.../values/{sheet}!A:{lastCol}:append` | `valueInputOption=RAW`, `insertDataOption=INSERT_ROWS` |
| UPDATE | POST | `.../values:batchUpdate` | Updates specific row range e.g. `tasks!A5:Q5` |
| DELETE (soft) | POST | `.../values/{range}:clear` | Empties the row; mapper skips rows with blank `id` |

### CalendarApi Endpoints

| Operation | HTTP | Path |
|---|---|---|
| List calendars | GET | `calendar/v3/users/me/calendarList` |
| List events | GET | `calendar/v3/calendars/{calendarId}/events?timeMin=...&timeMax=...&singleEvents=true&orderBy=startTime` |
| Create event | POST | `calendar/v3/calendars/{calendarId}/events` |
| Update event | PUT | `calendar/v3/calendars/{calendarId}/events/{eventId}` |
| Get event | GET | `calendar/v3/calendars/{calendarId}/events/{eventId}` |
| Move event | POST | `calendar/v3/calendars/{calendarId}/events/{eventId}/move?destination={targetCalendarId}` |
| Delete event | DELETE | `calendar/v3/calendars/{calendarId}/events/{eventId}` (204 or 410 = success) |

---

## 10. UI Screens

### AuthScreen

**ViewModel:** `AuthViewModel`  
**State:** `uiState: StateFlow<AuthUiState>` — sealed: `Loading`, `SignedOut`, `NeedsAuthorization(pendingIntent)`, `SignedIn(userName, userAvatarUrl, spreadsheetId)`, `Error(message)`  
**Layout:** Centered `Column` — app logo + title row, subtitle text, conditional content per state: `CircularProgressIndicator` (Loading/NeedsAuthorization), "Sign in with Google" button (SignedOut), error text + "Try again" button (Error).  
**Actions:** `startSignIn(context)`, `finalizeAuth(intent)`, `clearError()`  
**Note:** No ViewModel parameter needed by the screen — it takes an injected `AuthViewModel` passed from `MainActivity`.

---

### MainScreen (shell)

**ViewModel:** `MainViewModel`, `TaskFormViewModel`  
**StateFlows:** `folders`, `labels`, `syncState`, `authData`, `sidebarState`, `selectedCalendars`  
**Layout:** `ModalNavigationDrawer` + `Scaffold` (TopAppBar, FAB, SnackbarHost) + `NavHost`.  
**Start destination:** `Screen.UPCOMING`  
**Overlay screens** (replace entire content rather than navigating): Settings, Help, Feedback — toggled via `showSettings`, `showHelp`, `showFeedback` local state.  
**Task form:** `TaskFormSheet` shown as overlay when `showForm = true`. Supports create, edit, add-subtask, edit-calendar-event, edit-event-schedule-only modes.  
**Deep links:** Handled in `LaunchedEffect(initialDeepLinkUri)`. Supported schemes:
- `stlertasks://task/{taskId}` — opens task edit form
- `stlertasks://event/{calendarId}/{eventId}` — opens event edit form
- `stlertasks://create?folderId=` — opens create form
- `stlertasks://upcoming` — navigates to Upcoming (no-op if already there)
- `stlertasks://all_tasks` — navigates to All Tasks
- `stlertasks://folder/{folderId}` — navigates to folder screen

---

### UpcomingScreen

**ViewModel:** `UpcomingViewModel`  
**Data source:** `repository.observeAllPendingTasksWithDeadline()` + `calendarRepository.getEventsForCalendars(selectedIds, now, now+366)`  
**StateFlows:** `allGroupedTasks: Map<LocalDate, List<ListItem>>`, `isLoading`, `weekDays`, `weekOffset`, `labels`, `folders`, `priorityFilter`, `labelFilter`, `folderFilter`, `calendarFilter`, `calendarsInEvents`  
**Sort order within a day:** timed items before all-day, then by time string.  
**Filter matrix:** calendar-only filter → tasks hidden; task-only filter → events hidden; both active → both filtered; none → all shown.  
**Overdue group:** Items with `deadlineDate < today` are grouped under key `LocalDate.MIN` (displayed as "Overdue" header in red).  
**Week strip:** Mon–Sun strip driven by `_weekOffset`. Scroll syncs to strip via `snapshotFlow` + debounce 120ms. Day pills show a dot (orange = today, primary = has tasks). Navigation via chevron buttons or direct tap.  
**Empty state:** "No upcoming tasks" centered text.  
**Actions:** complete task, delete task, delete event, delete event series, update deadline/priority/labels inline.

---

### AllTasksScreen

**ViewModel:** `AllTasksViewModel`  
**Data source:** `repository.observeAllPendingTasks()` + `calendarRepository.getEventsForCalendars(selectedIds, now, now+366)`  
**StateFlows:** `filteredItems: List<ListItem>`, `isLoading`, `labels`, `folders`, `priorityFilter`, `labelFilter`, `folderFilter`, `calendarFilter`, `calendarsInEvents`  
**Sort order:** priority (URGENT→IMPORTANT→NORMAL, events count as NORMAL) → deadline date → timed-before-allday → time. Undated tasks appended after all dated items, sorted by priority only.  
**Scroll-to-today:** On first load, scrolls to the first item whose date is today or future (debounced 200ms).  
**Filter bar:** `FilterBar` composable (priority, labels, folders, calendars chips).  
**Empty state:** `EmptyState` with `FormatListBulleted` icon.

---

### CompletedScreen

**ViewModel:** `CompletedViewModel`  
**Data source:** `repository.observeCompletedTasks()` — ordered by `completedAt DESC` (falls back to `updatedAt`).  
**StateFlows:** `filteredTasks`, `isLoading`, `labels`, `folders`, `priorityFilter`, `labelFilter`, `folderFilter`  
**Filter bar:** Priority, label, folder chips (no calendar chip).  
**Actions:** restore task (unchecking checkbox), delete task.  
**Empty state:** `EmptyState` with `CheckCircle` icon.

---

### FolderScreen

**ViewModel:** `FolderViewModel` (gets `folderId` from `SavedStateHandle`)  
**Data source:** `repository.observePendingInFolder(folderId)` + `repository.observeCompletedChildCountsInFolder(folderId)`  
**StateFlows:** `displayList: List<TaskNode>`, `labels`  
**Layout:** `ReorderableLazyListState` (sh.calvin reorderable library). Drag handle on the left of each row.  
**Tree building:** `buildDisplayList()` — recursive depth-first traversal, roots sorted by `sortOrder`, children appended only if `task.isExpanded`. Each `TaskNode` carries `task`, `depth`, `childCount`, `completedChildCount`.  
**Drag-to-reorder:** `onMove` updates local optimistic list and stores pending `fromIdx`/`toIdx`. On `isDragging → false`, calls `reorderSiblings(parentId, fromIdx, toIdx)` (one batch DB write).  
**Indent/outdent:** Shown in task "…" menu — `reparentTask(taskId, newParentId)`.  
**Empty state:** "No tasks in this folder" centered text.

---

### LabelScreen

**ViewModel:** `LabelViewModel` (gets `labelId` from `SavedStateHandle`)  
**Data source:** `repository.observeAllPendingTasks()` filtered by `labelId in task.labels`  
**StateFlows:** `filteredTasks`, `isLoading`, `labels`, `folders`  
**Note:** Label chip is hidden from FilterBar (`showLabels = false` on TaskItem — label is implicit).  
**Empty state:** `EmptyState` with `Label` icon.

---

### PriorityScreen

**ViewModel:** `PriorityViewModel`  
**Data source:** `repository.observeAllPendingTasks()` filtered by `_selectedPriority`  
**StateFlows:** `filteredTasks`, `isLoading`, `labels`, `folders`, `selectedPriority`  
**Sort:** deadline date (nulls last) → timed-before-allday → time → createdAt  
**Navigation arg:** `priority` string (`"urgent"`, `"important"`, `"normal"`) — sets `_selectedPriority` via `LaunchedEffect`.  
**Empty state:** `EmptyState` with `Flag` icon.

---

### CalendarScreen

**ViewModel:** `CalendarViewModel` (gets `calendarId` from `SavedStateHandle`)  
**Data source:** `calendarRepository.getEventsForCalendars(setOf(calendarId), now, now+366)`  
**StateFlows:** `groupedEvents: Map<LocalDate, List<CalendarEvent>>`, `isLoading`  
**Layout:** `LazyColumn` with date headers. Overdue items grouped under `LocalDate.MIN`.  
**Actions:** delete event, delete event series (via `CalendarEventItem`).  
**Empty state:** `EmptyState` with `CalendarMonth` icon.

---

### TaskFormSheet

**ViewModel:** `TaskFormViewModel`  
**Mode:** Determined by what is passed — `task != null` = edit task; `calendarEvent != null` = edit/create event; neither = create task. `scheduleOnly = true` restricts the event form to date/time/repeat only.  
**Fields (task mode):** title (BasicTextField), priority (SegmentedButton), deadline (DatePicker + TimePicker + recurrence), folder (ExposedDropdownMenu), labels (LabelPickerSheet), parent (implicit via `initialParentId`).  
**Fields (event mode):** title, calendar (ExposedDropdownMenu), start date + time, end time, repeat (recurrence picker with RRULE builder).  
**Label sentinel:** New labels are encoded as `"__new__:colorHex:name"` — `TaskFormViewModel.resolveLabelSentinels()` creates the label in Room/Sheets and returns the real ID.  
**Output:** `TaskFormResult(title, folderId, parentId, priority, labelIds, deadlineDate, deadlineTime, isRecurring, recurType, recurValue)` — passed back via `onConfirm` lambda to `MainScreen.handleFormResult()`.

---

### SettingsScreen

**ViewModel:** `SettingsViewModel`  
**StateFlows:** `spreadsheetId`, `spreadsheetName`, `files: List<DriveFile>`, `loading`, `switching`, `calendars: List<CalendarItem>`, `calendarsLoading`  
**Sections:** SPREADSHEET (shows current spreadsheet name; expandable picker listing all Drive spreadsheets), CALENDARS (checkbox list of all Google Calendars; refresh button).  
**Switch spreadsheet:** saves new ID/name to DataStore, clears all local Room data (tasks/folders/labels/syncQueue), triggers sync.  
**Toggle calendar:** updates `selectedCalendarIds` in DataStore + optimistically updates UI list.

---

### HelpScreen

No ViewModel. Static scrollable content in cards: Getting started, Tasks, Views, Calendar & events, Sync & offline, Widgets.

---

### FeedbackScreen

**ViewModel:** `FeedbackViewModel`  
**State:** `sendResult: StateFlow<SendResult?>` — `null`, `Sending`, `Success`, `Error`  
**Transport:** HTTP POST to Google Apps Script URL with form-encoded body (`app=Tasks`, `email=...`, `message=...`). `instanceFollowRedirects = false` (Apps Script returns 302 on success; following it converts POST to GET and the script never executes).  
**Success:** clears message field, shows snackbar.

---

## 11. Widgets

All widgets extend `GlanceAppWidget`, use `PreferencesGlanceStateDefinition`, and obtain dependencies via `EntryPointAccessors` from `WidgetEntryPoint` (a Hilt entry point interface). `WidgetRefresher.refreshAll()` (debounced 400ms) calls `updateAll()` on all four widget classes after every task mutation. `refreshOnStartup()` (no debounce) is called from `Application.onCreate()` to re-register Glance sessions before WorkManager replays stale `SessionWorker` jobs.

### UpcomingWidget

**Data source:** `repo.observeAllPendingTasks()` + `calendarRepo.getEventsForCalendars(selectedIds, today, today+7)`  
**Configuration:** None (no config activity). Shows today + next 6 days (7-day window). Events cut off beyond `today+6`.  
**Update trigger:** `WidgetRefresher.refreshAll()` from repository; `refreshOnStartup()` on app start.  
**Layout:** `WidgetHeader("Upcoming", screenUri="stlertasks://upcoming")` + `LazyColumn` with `DateHeader` per date + `WidgetTaskRow` per task + `WidgetEventRow` per event. Overdue tasks grouped under "Overdue" header (red).  
**Pending-complete:** tap on checkbox sets `pendingCompleteKey` in Glance Preferences state (transient visual checkmark for up to 4 seconds while Room commits).

### FolderWidget

**Data source:** `repo.observePendingInFolder(folderId)` + `repo.observeCompletedChildCountsInFolder(folderId)` + `repo.observeLabels()` + `repo.observeFolders()`  
**Configuration:** `WidgetConfigActivity` — user selects folder. Stored in `WidgetPrefs.getFolderId(context, appWidgetId)` (SharedPreferences file `widget_config`). `isFolderWidgetConfigured` also returns true for widgets with a legacy `folder_N` pref.  
**Layout:** `WidgetHeader(folderName, screenUri="stlertasks://folder/{folderId}")` + `LazyColumn` (capped at 20 items). Recursive tree: `addRecursive()` builds `FolderRow` list respecting `isExpanded`.  
**Deep-link on tap:** task row → `stlertasks://task/{taskId}`.

### TaskListWidget

**Data source:** `repo.observeAllPendingTasks()` + `calendarRepo.getEventsForCalendars(selectedIds, now-1, now+7)` (events suppressed when any task filter is active).  
**Configuration:** `WidgetConfigActivity` — multi-select filters: folders (`filterFolders_N`), labels (`filterLabels_N`), priorities (`filterPriorities_N`). Stored in `WidgetPrefs` (comma-separated sets). Legacy single-value prefs (`filterFolder_N`, etc.) are migrated on first read.  
**Row cap:** 50 when filters active; 20 otherwise (guards Binder 1 MB RemoteViews limit).  
**Header title:** built from active filters — `@FolderName` / `#LabelName` / `!1 !2` priority codes, joined by space. Falls back to `"Tasks"`.  
**Layout:** `WidgetHeader(title, screenUri="stlertasks://all_tasks")` + `LazyColumn`.

### CalendarWidget

**Data source:** `calendarRepo.getEventsForCalendars(widgetCalendarIds or selectedIds, now-1, now+30)`  
**Configuration:** `WidgetConfigActivity` — selects one or more calendars. Stored as `calendarIds_N` (comma-separated) and `calendarName_N` in `WidgetPrefs`. Falls back to globally selected calendars if empty.  
**Header deep-link:** single calendar → `stlertasks://calendar/{calendarId}`; multiple → `stlertasks://upcoming`.  
**Row cap:** 40.  
**Layout:** `WidgetHeader(calendarName)` + `LazyColumn` with date headers + `WidgetEventRow` rows. 30-day window.

---

## 12. Shared Components

### CalendarEventItem

**Location:** `ui/calendar/CalendarEventItem.kt`  
**Signature:**
```kotlin
fun CalendarEventItem(
    event          : CalendarEvent,
    showDate       : Boolean = true,
    onEdit         : (() -> Unit)? = null,      // full edit form
    onEditSchedule : (() -> Unit)? = null,      // schedule-only form
    onDelete       : (() -> Unit)? = null,
    onDeleteSeries : (() -> Unit)? = null,
    modifier       : Modifier = Modifier,
)
```
**Internal state:** `showMenu: Boolean`, `showDeleteConfirm: Boolean`  
**Layout:** `Row` — calendar icon (40dp slot matching TaskItem checkbox), `Column { title; FlowRow{ recurring icon, time label, calendar name icon+text } }`, optional action icons (Schedule + MoreHoriz).  
**Menu:** `ModalBottomSheet` with Edit + Delete entries. Delete → `AlertDialog`. Recurring event delete → 3-option dialog (this event / all in series / cancel).  
**Time label:** `formatEventTime()` — all-day events show date when `showDate=true`; timed events show `"HH:MM — HH:MM"` plus optional date prefix.  
**Deadline color:** uses `deadlineColor(deadlineStatus(startDate, startTime))` from `TaskColors.kt`.

### FilterBar

**Location:** `ui/alltasks/AllTasksScreen.kt`  
**Signature:**
```kotlin
fun FilterBar(
    labels          : List<Label>,
    folders         : List<Folder> = emptyList(),
    priorityFilter  : Set<Priority>,
    labelFilter     : Set<String>,
    folderFilter    : Set<String> = emptySet(),
    calendars       : List<CalendarItem> = emptyList(),
    calendarFilter  : Set<String> = emptySet(),
    onTogglePriority: (Priority) -> Unit,
    onToggleLabel   : (String) -> Unit,
    onToggleFolder  : (String) -> Unit = {},
    onToggleCalendar: (String) -> Unit = {},
    onClearAll      : () -> Unit = {},
    showLabelFilter : Boolean = true,
    showFolderFilter: Boolean = true,
)
```
**Internal state:** 4 dropdown-expanded booleans.  
**Layout:** `Row` — optional ✕ clear-all button, then icon-only `FilterChip` buttons (priority 🚩, labels 🏷, folders 📁, calendars 📅). Each chip shows a count badge when active. Each chip opens a `DropdownMenu` with multi-select items (checkmark on active).  
**Neutral chip colors:** overrides Material You warm tint — selected container = `#d8d8d8` (light) / `primaryContainer` (dark).

### TaskItem

**Location:** `ui/task/TaskItem.kt`  
Not documented in detail here as it is a large composable. Key parameters include `task`, `labels`, `depth`, `hasChildren`, `completedChildCount`, `totalChildCount`, `showFolder`, `showLabels`, `showExpandSlot`, `showDateInDeadline`, `enableSwipe`, callbacks for check, expand, deadline change, priority change, label change, add subtask, edit, delete, indent, outdent.

---

## 13. Navigation

### Route Constants (`Screen.kt`)

| Constant | Route String | Args |
|---|---|---|
| `Screen.UPCOMING` | `"upcoming"` | — |
| `Screen.ALL_TASKS` | `"all_tasks"` | — |
| `Screen.COMPLETED` | `"completed"` | — |
| `Screen.FOLDER` | `"folder/{folderId}"` | `folderId: String` |
| `Screen.LABEL` | `"label/{labelId}"` | `labelId: String` |
| `Screen.PRIORITY` | `"priority/{priority}"` | `priority: String` |
| `Screen.CALENDAR` | `"calendar/{calendarId}"` | `calendarId: String` (URL-encoded) |

Helper functions: `folderRoute(id)`, `labelRoute(id)`, `priorityRoute(priority)`, `calendarRoute(id)`.

### Navigation Behavior

- All navigation uses `popUpTo(graph.startDestinationId) + launchSingleTop = true` to prevent stack buildup when switching sections from the sidebar.
- The `NavHost` lives inside `MainScreen`. Navigation between screens happens by calling `navController.navigate(route)`.
- **Overlay screens** (Settings, Help, Feedback) are shown by toggling `showSettings/showHelp/showFeedback` local state in `MainScreen`. They replace the entire content via `return` and do not create NavBackStack entries.
- The `TaskFormSheet` (ModalBottomSheet) is shown as a local overlay without navigation.
- Deep links are handled in `MainActivity.onNewIntent()` and passed as `initialDeepLinkUri` to `MainScreen`.

---

## 14. Theme & Colors

Theme is `TasksTheme` wrapping `MaterialTheme`. No dynamic color (Material You). Light and dark color schemes are fully custom.

### Primary and Accent Colors

| Constant | Hex (light) | Usage |
|---|---|---|
| `Primary` | `#e07e38` | Primary buttons, FAB, links, cover accent line |
| `PrimaryDark` | `#D98D52` | Primary in dark mode |
| `OnPrimary` | `#ffffff` | Text on primary buttons |

### Background / Surface

| Constant | Light | Dark |
|---|---|---|
| `Background` | `#ffffff` | `#1c1c1c` |
| `Surface` | `#ffffff` | `#363636` |
| `Popover` | `#ffffff` | `#242424` |

### Text

| Constant | Light | Dark |
|---|---|---|
| `Foreground` | `#18181f` | `#f2f2f2` |
| `MutedForeground` | `#6b6b6b` | `#949494` |

### Selection Highlights

| Constant | Hex | Usage |
|---|---|---|
| `SelectedHighlightLight` | `#d8d8d8` | Week-strip pill, filter chips, FAB, sidebar items (light) |
| `SelectedHighlightDark` | `#515151` | Same in dark mode |
| `OnChipSelected` | `#424242` | Text/icons on selected chips (light mode) |

### Priority Colors

| Constant | Hex | Usage |
|---|---|---|
| `PriorityUrgent` | `#f87171` | Red-400 |
| `PriorityImportant` | `#fb923c` | Orange-400 |
| `PriorityNormal` | `#9ca3af` | Gray-400 |

### Deadline Status Colors

| Constant | Hex | Usage |
|---|---|---|
| `DeadlineOverdue` | `#f87171` | Past due dates |
| `DeadlineToday` | `#16a34a` | Due today (green-600) |
| `DeadlineTomorrow` | `#fb923c` | Due tomorrow |
| `DeadlineThisWeek` | `#a78bfa` | Due this week (violet-400) |
| `Destructive` | `#e96060` | Error states, delete actions |

### Borders and Inputs

| Constant | Light | Dark |
|---|---|---|
| `Border` / `Input` | `#e0e0e0` | `#4a4a4a` / `#383838` |

### Widget Colors (XML resources)

Widget colors reference XML color resources in `res/values/colors.xml` and `res/values-night/colors.xml` (day/night variants). Glance `ColorProvider(R.color.widget_xxx)` is used rather than `ColorProvider(Color)` to get proper day/night behavior.

---

## 15. CI/CD & Build

### GitHub Actions Workflow (`release.yml`)

**Trigger:** push of any tag matching `v*`  
**Runner:** `ubuntu-latest`  
**Steps:**
1. `actions/checkout@v4.2.2`
2. `actions/setup-java@v4.7.0` — JDK 17 (Temurin distribution)
3. `chmod +x ./gradlew`
4. Decode keystore: `echo "$KEYSTORE_BASE64" | base64 --decode > keystore.jks`
5. `./gradlew assembleRelease` with env vars `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
6. Rename APK: `app-release.apk` → `stler-tasks.apk`
7. `softprops/action-gh-release@v2` — creates GitHub Release and attaches `stler-tasks.apk`

**Secrets required:** `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`  
**Permissions:** `contents: write` (to create the GitHub Release)

### Local / Debug Build

No signing config is created when `KEYSTORE_PATH` is blank — debug builds use Android's default debug keystore. `isMinifyEnabled = false` for release (no ProGuard obfuscation).

---

## 16. Key Algorithms

### Recursive Tree Build (FolderViewModel + FolderWidget)

```pseudocode
function buildDisplayList(tasks, completedCounts):
    byParent = group tasks by parentId
    result = []

    function addTask(task, depth):
        children = byParent[task.id] ?? []
        completedCount = completedCounts[task.id] ?? 0
        result.append(TaskNode(task, depth, children.size, completedCount))
        if task.isExpanded:
            for child in sort(children, by sortOrder):
                addTask(child, depth + 1)

    for root in sort(byParent[""], by sortOrder):
        addTask(root, 0)

    return result
```

### Task Reorder (FolderViewModel.reorderSiblings)

```pseudocode
function reorderSiblings(parentId, fromIndex, toIndex):
    siblings = allTasks
        .filter(parentId == parentId)
        .sortedBy(sortOrder)
    if fromIndex or toIndex out of range: return
    moved = siblings.removeAt(fromIndex)
    siblings.insertAt(toIndex, moved)
    now = Instant.now().toString()
    updated = siblings.mapIndexed { i, task ->
        task.copy(sortOrder = i * 10, updatedAt = now)
    }
    repository.updateTasks(updated)   // single batch write
```

### Sync Push Loop (SyncWorker.push)

```pseudocode
function push(spreadsheetId):
    queue = syncQueueDao.getAll()
    if queue.isEmpty: return

    rowsCache = {}

    for item in queue:
        try:
            executeOperation(item, spreadsheetId, rowsCache)
            syncQueueDao.deleteById(item.id)
        catch:
            syncQueueDao.incrementRetry(item.id)

    syncQueueDao.deleteExhausted(maxRetries = 5)

function executeOperation(item, spreadsheetId, rowsCache):
    sheetName = sheetOf(item.entityType)   // "tasks", "folders", "labels"
    if item.operation == "INSERT":
        range = sheetName + "!A:" + lastColOf(item.entityType)
        sheetsApi.append(spreadsheetId, range, body = entityRow(item))
    else:
        rows = rowsCache.getOrFetch(sheetName)
        rowNum = findRowNumber(rows, item.entityId)
        if rowNum == null: return   // entity not found in sheet (skip)
        range = sheetName + "!A" + rowNum + ":" + lastColOf(...) + rowNum
        if item.operation == "UPDATE":
            sheetsApi.batchUpdate(spreadsheetId, range, body = entityRow(item))
        if item.operation == "DELETE":
            sheetsApi.clear(spreadsheetId, range)
```

### Onboarding Seed Detection

```pseudocode
// At sign-in, after Drive search for "db_tasks":
function findOrCreateSpreadsheetWithName(accessToken):
    found = findSpreadsheetId(accessToken)   // Drive API search
    if found.isNotBlank():
        return (found, "db_tasks")
    // No spreadsheet found → first-time user
    created = createSpreadsheet(accessToken)
    return (created, "db_tasks")

function findSpreadsheetId(accessToken):
    query = "name='db_tasks' AND mimeType='application/vnd.google-apps.spreadsheet' AND trashed=false"
    response = Drive.files.list(q=query, fields="files(id,name)")
    return response.files.firstOrNull()?.id ?? ""
```
