# Stler Tasks — Android App Technical Specification

_Last updated: 2026-04-18_

---

## 1. Project Overview

Stler Tasks is a native Android task manager that replaces a Progressive Web App (PWA).  
It uses the same Google Sheets spreadsheet as backend storage so data is shared seamlessly between the PWA and the Android app.

**Tech stack**

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9 |
| UI | Jetpack Compose (Material 3) |
| Navigation | Navigation Compose |
| DI | Hilt |
| Local DB | Room |
| Background sync | WorkManager |
| Home-screen widgets | Jetpack Glance (1.1) |
| Remote storage | Google Sheets API v4 via Retrofit |
| Auth | Google Sign-In / Credential Manager + OAuth2 |
| Build | Gradle (Kotlin DSL), `compileSdk 35` |

---

## 2. Package Structure

```
com.stler.tasks
├── auth/               Google auth token + preferences management
├── data/
│   ├── local/
│   │   ├── dao/        Room DAOs (TaskDao, FolderDao, LabelDao, SyncQueueDao)
│   │   ├── entity/     Room entities (TaskEntity, FolderEntity, LabelEntity, SyncQueueEntity)
│   │   └── TaskDatabase.kt
│   ├── remote/
│   │   ├── SheetsApi.kt        Retrofit interface (append / batchGet / batchUpdate / clear)
│   │   ├── SheetsMapper.kt     Entity ↔ Sheets-row conversion
│   │   ├── TokenProvider.kt    Authenticating OkHttp interceptor
│   │   └── dto/                Sheets API request/response DTOs
│   └── repository/
│       ├── TaskRepository.kt   Interface
│       └── TaskRepositoryImpl.kt
├── di/                 Hilt modules (DatabaseModule, NetworkModule, RepositoryModule, AuthModule)
├── domain/model/       Task, Folder, Label, Priority, RecurType, TaskStatus
├── sync/               SyncWorker, SyncManager, SyncState, NetworkObserver
├── ui/
│   ├── auth/           AuthScreen, AuthViewModel
│   ├── alltasks/       AllTasksScreen, AllTasksViewModel
│   ├── completed/      CompletedScreen, CompletedViewModel
│   ├── folder/         FolderScreen, FolderViewModel
│   ├── label/          LabelScreen, LabelViewModel
│   ├── main/           MainScreen, MainViewModel, SidebarMenu, TasksTopAppBar, FolderLabelDialogs
│   ├── navigation/     Screen (route constants)
│   ├── priority/       PriorityScreen, PriorityViewModel
│   ├── task/           TaskItem, TaskFormSheet, TaskFormViewModel, DeadlinePickerDialog,
│   │                   TaskCheckbox, TaskColors, PriorityPickerSheet, LabelPickerSheet
│   ├── theme/          Color, Type, Theme
│   └── upcoming/       UpcomingScreen, UpcomingViewModel
├── util/               ColorUtils (hex → Compose Color)
├── widget/
│   ├── CompleteTaskAction.kt   ActionCallbacks (Complete, ToggleExpand, OpenTask, OpenCreate, OpenScreen)
│   ├── FolderWidget.kt
│   ├── TaskListWidget.kt
│   ├── UpcomingWidget.kt
│   ├── WidgetConfigActivity.kt
│   ├── WidgetEntryPoint.kt     Hilt entry point for widgets
│   ├── WidgetHeader.kt         Shared widget header composable
│   ├── WidgetPrefs.kt          SharedPreferences for widget configuration
│   ├── WidgetRefresher.kt      Hilt singleton — refreshes all widgets from the repo layer
│   └── WidgetTaskRow.kt        Shared task row composable for all widgets
├── AppContainer.kt
├── MainActivity.kt
└── TasksApplication.kt
```

---

## 3. Domain Model

### Task
```kotlin
data class Task(
    val id: String,
    val parentId: String = "",          // empty = root task
    val folderId: String = "fld-inbox",
    val title: String,
    val status: TaskStatus,             // PENDING | COMPLETED
    val priority: Priority,             // URGENT | IMPORTANT | NORMAL
    val deadlineDate: String = "",      // "YYYY-MM-DD" or ""
    val deadlineTime: String = "",      // "HH:MM" or ""
    val isRecurring: Boolean = false,
    val recurType: RecurType,           // NONE | DAYS | WEEKS | MONTHS
    val recurValue: Int = 1,
    val labels: List<String> = emptyList(), // label IDs
    val sortOrder: Int = 0,
    val createdAt: String = "",         // ISO-8601
    val updatedAt: String = "",
    val completedAt: String = "",
    val isExpanded: Boolean = false,    // subtask tree expansion (local only)
)
```

### Folder
```kotlin
data class Folder(val id: String, val name: String, val color: String, val sortOrder: Int)
```

### Label
```kotlin
data class Label(val id: String, val name: String, val color: String)
```

---

## 4. Data Flow

```
Google Sheets
     │ pull (batchGet)           push (append / batchUpdate / clear)
     ▼                           ▲
SyncWorker (WorkManager)   SyncQueueDao (Room)
     │                           ▲
     ▼                     TaskRepositoryImpl
    Room  ──────────────►  (write → enqueue to SyncQueue → notify WidgetRefresher)
     │
     ▼
 ViewModels (StateFlow via Room DAOs)
     │
     ▼
 Compose UI / Glance Widgets
```

Every write operation:
1. Updates Room immediately (optimistic).
2. Appends a `SyncQueueEntity` (INSERT / UPDATE / DELETE) for the background worker.
3. Calls `WidgetRefresher.refreshAll()` so widgets re-render without waiting for the next periodic sync.

Reads are always served from Room; the remote is write-through only.

---

## 5. Sync Architecture

### SyncWorker
- `CoroutineWorker` annotated with `@HiltWorker`.
- **Push phase** — drains `SyncQueue` ordered by creation time:
  - `INSERT` → `sheetsApi.append()`
  - `UPDATE` → find row number → `sheetsApi.batchUpdate()`
  - `DELETE` → find row number → `sheetsApi.clear()`
  - Rows are cached per sheet name to avoid redundant network calls.
- **Pull phase** — `taskRepository.fetchAllAndSave(spreadsheetId)` downloads all three sheets and upserts Room.

### SyncManager
- Schedules a **30-minute periodic** `SyncWorker` at app startup (`ExistingPeriodicWorkPolicy.KEEP`).
- Exposes `triggerSync()` for manual one-off sync (TopAppBar button).
- Exposes `syncState: Flow<SyncState>` combining WorkManager state + pending queue count:
  - `Idle` — nothing pending, no worker running.
  - `Pending(count)` — items waiting in the queue.
  - `Syncing` — worker currently running.

### Offline support
All writes succeed immediately in Room and queue to `SyncQueue`. When connectivity is restored, the next WorkManager run drains the queue. Items that fail are retried up to 4 times; exhausted items are purged.

---

## 6. Authentication

1. `GoogleAuthRepository` — drives the Credential Manager sign-in flow and persists the auth token.
2. `TokenProvider` — OkHttp `Interceptor` that injects `Authorization: Bearer <token>` on every request; auto-refreshes the token if expired.
3. `AuthPreferences` (DataStore) — stores `accessToken`, `refreshToken`, `spreadsheetId`, `userEmail`, `userName`, `userAvatarUrl`.
4. `SyncWorker` — on first run, if `spreadsheetId` is blank, calls `GoogleAuthRepository.findAndSaveSpreadsheetId()` via Drive API to locate the user's spreadsheet by name.

---

## 7. Navigation

Entry point: `MainActivity` → `AuthScreen` (if not signed in) → `MainScreen`.

`MainScreen` hosts:
- `ModalNavigationDrawer` (sidebar) containing `SidebarMenu`
- `Scaffold` with `TasksTopAppBar` and a `FloatingActionButton`
- `NavHost` with the following destinations:

| Route | Screen | ViewModel |
|---|---|---|
| `upcoming` | UpcomingScreen | UpcomingViewModel |
| `all_tasks` | AllTasksScreen | AllTasksViewModel |
| `completed` | CompletedScreen | CompletedViewModel |
| `folder/{folderId}` | FolderScreen | FolderViewModel |
| `label/{labelId}` | LabelScreen | LabelViewModel |
| `priority/{priority}` | PriorityScreen | PriorityViewModel |

Start destination: `upcoming`.

Task creation / editing is handled by a **bottom sheet** (`TaskFormSheet`) overlaid on any screen — it is controlled by state in `MainScreen`, not by navigation.

### Deeplink scheme: `stlertasks://`

| URI | Action |
|---|---|
| `stlertasks://upcoming` | Bring Upcoming to the front without replacing its ViewModel |
| `stlertasks://all_tasks` | Navigate to All Tasks |
| `stlertasks://folder/{id}` | Navigate to specific folder |
| `stlertasks://task/{id}` | Open task edit sheet |
| `stlertasks://create[?folderId=…]` | Open create-task sheet, optionally pre-selecting a folder |

---

## 8. Screens

### UpcomingScreen
- **Week strip** — 7-day pill row with `←` / `→` navigation and a "today" calendar button.
- **Filter chips** — Priority (Urgent / Important / Normal) + all labels.
- **Task list** — grouped by deadline date; all past dates (< today) collapse into a single **"Overdue"** section shown first in error-red. Timed tasks appear before untimed tasks within each group.
- Row 2 of each task shows time only (date is already in the section header).
- Scroll position drives week strip: `snapshotFlow` on `LazyListState` → debounced → `viewModel.onVisibleDateChanged()`.

### AllTasksScreen
- Flat list of all pending tasks across all folders and depths.
- Priority + label filter chips.

### FolderScreen
- Tasks in a specific folder, nested (tree of root + subtasks).
- Drag-to-reorder via `sh.calvin.reorderable` library.
- Folder chip hidden (`showFolder = false`) — the folder is implicit from the screen context.

### LabelScreen
- Tasks tagged with the current label across all folders.
- Label chip hidden (`showLabels = false`) — the label is implicit from the screen context.
- Folder chip shown.

### PriorityScreen
- Tasks filtered by a single priority level.

### CompletedScreen
- Completed tasks with restore / delete actions.

### TaskFormSheet (`ModalBottomSheet`)
- Create or edit a task.
- **Smart title parsing**: `@FolderName` pre-selects folder, `#LabelName` adds label, `!1/!2/!3` sets URGENT/IMPORTANT/NORMAL priority.
- Deadline picker → `DeadlinePickerDialog` (date chip + time chip + recurring toggle + recur interval row).
- Folder picker, label multi-picker, priority picker.

### DeadlinePickerDialog
- Date chip → `DatePickerDialog` sub-dialog.
- Time chip (enabled only when date is set) → `TimePicker` sub-dialog.
- Recurring toggle → `RecurRow` (every N days/weeks/months).
- **× (Close icon)** — clears date, time and recurrence, saves immediately.
- **→ (ArrowForward icon)** — visible only for recurring tasks with a date set; advances deadline by the task's own recurrence interval (`recurValue` × `recurType`) and saves immediately (= Postpone).
- Cancel + Save buttons.

---

## 9. Task Item (`TaskItem.kt`)

A reusable composable used by every list screen.

**Parameters**

| Name | Default | Purpose |
|---|---|---|
| `depth` | 0 | Left-indent level (×20dp per level) |
| `hasChildren` | false | Shows expand/collapse chevron |
| `completedChildCount` | 0 | For subtask progress display |
| `totalChildCount` | 0 | For subtask progress display |
| `showFolder` | false | Show folder chip in row 2 |
| `showLabels` | true | Show label chips in row 2 (false in LabelScreen) |
| `showDateInDeadline` | true | Include date in deadline label (false in UpcomingScreen) |

**Layout**

- Row 1: `[chevron 28dp] [checkbox 32dp] [title] [deadline-clock] [⋯ menu]`
- Row 2 (when metadata present): recurring icon, deadline label, label chips, folder chip, subtask stats.

**Deadline colouring** (`deadlineStatus`):
- Overdue → `error`
- Today → `primary`
- Tomorrow → `tertiary`
- Future → `onSurfaceVariant`

---

## 10. Widget System

Three Glance `GlanceAppWidget` implementations:

### UpcomingWidget
- Reads `observeAllPendingTasks()`, filters to tasks with a deadline, sorts them.
- Overdue tasks (< today) grouped under a single **"Overdue"** header; future/today tasks have per-date headers formatted as `"d MMM · Today/Tomorrow · Weekday"`.
- Each row uses `WidgetTaskRow(showExpandSpace=false, timeOnly=true)`.

### FolderWidget
- Reads `observePendingInFolder(folderId)`.
- Shows recursive subtask tree at configurable folder (set at placement time via `WidgetConfigActivity`).
- Supports expand/collapse via `ToggleExpandAction`.

### TaskListWidget
- Reads `observeAllPendingTasks()`, applies optional folder/label/priority filters.
- Header title built from active filters: `@Folder #Label !1/!2/!3`, falls back to `"Tasks"`.

### WidgetHeader
- App icon (`ic_launcher_round`, 28dp) + title (18sp bold) + `+` button.
- Icon and title open the app to the widget's associated screen via `startDeepLink()`.
- `+` button opens the create-task sheet via `OpenCreateAction`.

### WidgetTaskRow
- Row 1: complete checkbox, task title.
- Row 2 (14sp): recurring icon, deadline label, label dots, folder name.
- Parameters: `showExpandSpace: Boolean` (omits empty left spacer for non-folder widgets), `timeOnly: Boolean` (omits date portion of deadline label).

### ActionCallbacks
All live in `CompleteTaskAction.kt`:

| Class | Key params | Effect |
|---|---|---|
| `CompleteTaskAction` | `taskId` | Marks task complete → `refreshAll()` |
| `ToggleExpandAction` | `taskId`, `expand` | Toggles `isExpanded` → refreshes FolderWidget |
| `OpenTaskAction` | `taskId` | Deep-links to `stlertasks://task/{id}` |
| `OpenCreateAction` | `folderId?` | Deep-links to `stlertasks://create[?folderId=…]` |
| `OpenScreenAction` | `screenUri` | Deep-links to any URI |

### Widget refresh
`WidgetRefresher` is a Hilt `@Singleton` called by `TaskRepositoryImpl` after every write, plus by `CompleteTaskAction` / `ToggleExpandAction`. It calls `GlanceAppWidget.updateAll(context)` on all three widget types inside `withContext(Dispatchers.Main)` (required by Glance's RemoteViews pipeline).

---

## 11. Recurring Tasks

`recurType`: `DAYS` | `WEEKS` | `MONTHS` | `NONE`.  
`recurValue`: positive integer (e.g. 3 for "every 3 days").

**Postpone** (→ button in `DeadlinePickerDialog`): advances `deadlineDate` by exactly one recurrence interval — `recurValue` days/weeks/months — not a hardcoded +1 day.

**Complete recurring task**: the `completeTask()` function in `TaskRepositoryImpl` advances the deadline by one interval and resets status to `PENDING` (same pattern as the PWA `getNextDueDate`).

---

## 12. Dependency Injection

| Module | Provides |
|---|---|
| `DatabaseModule` | `TaskDatabase`, all DAOs |
| `NetworkModule` | `OkHttpClient` (with `TokenProvider`), `Retrofit`, `SheetsApi`, `Gson` |
| `RepositoryModule` | `TaskRepository` → `TaskRepositoryImpl` |
| `AuthModule` | `GoogleAuthRepository`, `AuthPreferences` |

Widgets cannot receive Hilt injections directly; they use `EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)` to access `TaskRepository`.

---

## 13. Key Libraries

| Library | Usage |
|---|---|
| `androidx.glance:glance-appwidget:1.1.0` | Home-screen widgets |
| `androidx.navigation:navigation-compose` | In-app navigation |
| `androidx.hilt:hilt-navigation-compose` | ViewModel injection in composables |
| `androidx.work:work-runtime-ktx` | Background sync |
| `androidx.room:room-ktx` | Local database |
| `com.google.dagger:hilt-android` | DI framework |
| `com.squareup.retrofit2:retrofit` | HTTP client for Sheets API |
| `sh.calvin.reorderable` | Drag-to-reorder in FolderScreen |
| `androidx.datastore:datastore-preferences` | Auth preferences storage |
| `com.google.android.gms:play-services-auth` | Google Sign-In |

---

## 14. Configuration & Build

- **Min SDK**: 26 (Java 8 time API used without desugaring).
- **Target / Compile SDK**: 35.
- **Package**: `com.stler.tasks`.
- **Deeplink host**: `stlertasks` (custom scheme, no HTTP).
- `local.properties` contains `GOOGLE_CLIENT_ID` — not committed.
