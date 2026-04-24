# Stler Tasks Android — Technical Specification

**Version:** 1.3 (April 2026)  
**Repository:** github.com/JuliaSivridi/Tasks_Android  
**Stack:** Kotlin · Jetpack Compose · Room · Hilt · WorkManager · Glance · Google Sheets API v4  
**Min SDK:** 26 (Android 8.0) · **Target SDK:** 36

---

## 1. Overview

Stler Tasks is a personal task manager for Android. It is a native rewrite of a PWA with the same Google Sheets backend, so both apps share one `db_tasks` spreadsheet per user. There is no dedicated backend server — all persistent data lives in Google Sheets, accessed via the Sheets API v4. Room serves as a local cache that makes the app fully functional offline.

**Key design goals:**
- Google Sheets as the single source of truth — no proprietary cloud service
- Full offline support via Room + sync queue
- Reactive UI — all screens observe Room Flows; data updates propagate automatically
- Clean MVVM + Repository architecture with Hilt DI throughout
- Glance widgets that react to Room changes without explicit refresh calls

---

## 2. Tech Stack

| Layer | Library | Version | Notes |
|---|---|---|---|
| Language | Kotlin | 2.2.10 | |
| UI | Jetpack Compose + Material 3 | BOM 2025.x | Compose-only, no XML layouts |
| DI | Hilt | 2.59.2 | KSP processor |
| Local DB | Room | 2.x | version 4, fallbackToDestructiveMigration |
| Networking | Retrofit + OkHttp | 2.x / 4.x | Bearer token interceptor + 401 Authenticator |
| Serialization | Gson | 2.x | For Retrofit + SyncQueue payloads |
| Background | WorkManager (Hilt) | 2.x | Periodic sync (30 min) + one-off |
| Widgets | Glance | 1.x | All 3 widget types |
| Auth | CredentialManager + Identity API | latest | Google Sign-In + scope authorization |
| Preferences | DataStore | 1.x | Token, user info, spreadsheet ID |
| Image loading | Coil | 2.x | Avatar in TopAppBar |
| Drag & drop | reorderable (Calvin) | latest | FolderScreen drag-to-reorder |
| Coroutines | kotlinx.coroutines | 1.x | Android + Play Services variants |

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
```

**Write path:** Every mutation → Room write (instant, triggers UI recomposition) + SyncQueue INSERT. SyncWorker drains the queue on next sync.

**Read path:** Room Flow → ViewModel StateFlow → Composable. Sheets data is only read during sync pull (`fetchAllAndSave`), which upserts into Room.

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
│   │   ├── dao/                  TaskDao, FolderDao, LabelDao, SyncQueueDao
│   │   ├── entity/               TaskEntity, FolderEntity, LabelEntity, SyncQueueEntity
│   │   └── TaskDatabase.kt       Room DB (version 4)
│   ├── remote/
│   │   ├── dto/                  BatchValuesResponse, ValuesBody, DriveFilesResponse, …
│   │   ├── NetworkModule.kt      Hilt: OkHttpClient (interceptor + authenticator) + Retrofit
│   │   ├── SheetsApi.kt          batchGet / append / batchUpdate / clear
│   │   ├── SheetsMapper.kt       row ↔ entity bidirectional conversion
│   │   └── TokenProvider.kt      interface for token retrieval
│   └── repository/
│       ├── TaskRepository.kt     interface
│       └── TaskRepositoryImpl.kt full implementation (CRUD, Flows, fetchAllAndSave)
│
├── di/
│   ├── AuthModule.kt             provides GoogleAuthRepository as TokenProvider
│   ├── DatabaseModule.kt         provides Room DB + all DAOs
│   └── RepositoryModule.kt       binds TaskRepositoryImpl
│
├── domain/model/
│   ├── Task.kt                   domain model
│   ├── Folder.kt
│   ├── Label.kt
│   ├── Priority.kt               enum: URGENT, IMPORTANT, NORMAL
│   ├── RecurType.kt              enum: DAILY, WEEKLY, MONTHLY
│   └── TaskStatus.kt             enum: PENDING, COMPLETED
│
├── sync/
│   ├── NetworkObserver.kt        callbackFlow wrapping ConnectivityManager
│   ├── SyncManager.kt            schedules periodic + one-off WorkManager requests
│   ├── SyncState.kt              sealed class: Idle / Syncing / Pending(count)
│   └── SyncWorker.kt             @HiltWorker: push queue → pull all
│
├── ui/
│   ├── alltasks/                 AllTasksScreen + AllTasksViewModel
│   ├── completed/                CompletedScreen + CompletedViewModel
│   ├── folder/                   FolderScreen + FolderViewModel + DisplayNode
│   ├── label/                    LabelScreen + LabelViewModel
│   ├── main/                     MainScreen, MainViewModel, SidebarMenu,
│   │                             SidebarPreferences, SidebarState, TasksTopAppBar
│   ├── navigation/               Screen.kt (route constants + helper functions)
│   ├── priority/                 PriorityScreen + PriorityViewModel
│   ├── task/                     TaskItem, TaskCheckbox, TaskFormSheet,
│   │                             DeadlinePickerDialog, PriorityPickerSheet,
│   │                             LabelPickerSheet, TaskColors, TaskMobileMenu
│   ├── theme/                    Color.kt, Theme.kt, Typography.kt
│   ├── upcoming/                 UpcomingScreen + UpcomingViewModel
│   └── util/                     BaseViewModel, EmptyState, ShimmerTaskList,
│                                 ErrorSnackbarEffect
│
├── util/
│   └── ColorExtensions.kt        String.toComposeColor(), etc.
│
└── widget/
    ├── CompleteTaskAction.kt     GlanceActionCallback: complete task + queue + widget refresh
    ├── FolderWidget.kt           hierarchical folder task list
    ├── FolderWidgetReceiver.kt
    ├── TaskListWidget.kt         flat filtered list (folder / label / priority)
    ├── TaskListWidgetReceiver.kt
    ├── ToggleExpandAction.kt     GlanceActionCallback: toggle subtask visibility
    ├── UpcomingWidget.kt         tasks with deadline ≤ 7 days, grouped by date
    ├── UpcomingWidgetReceiver.kt
    ├── WidgetColors.kt           named ColorProvider constants
    ├── WidgetConfigActivity.kt   configuration screen for all 3 widget types
    ├── WidgetEntryPoint.kt       Hilt entry point for widget actions
    ├── WidgetHeader.kt           shared widget header (title + "+" button)
    └── WidgetTaskRow.kt          shared task row (checkbox + title + deadline/labels)
```

---

## 5. Data Model

### 5.1 Domain Models

#### Task
| Field | Type | Description |
|---|---|---|
| id | String | UUID (e.g. `tsk-xxxxxxxx`) |
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
| id | String | `"lbl-xxxxxxxx"` |
| name | String | Display name |
| color | String | Hex color `"#rrggbb"` |
| sortOrder | Int | Position in sidebar |

### 5.2 SyncQueue Entry
| Field | Type | Description |
|---|---|---|
| id | Long (autoincrement) | |
| entityType | String | `"task"`, `"folder"`, or `"label"` |
| entityId | String | ID of the affected entity |
| operation | String | `"INSERT"`, `"UPDATE"`, `"DELETE"` |
| payloadJson | String | Full entity serialized as JSON (Gson) |
| retryCount | Int | Incremented on failure; items removed after 5 failures |

### 5.3 Google Sheets Schema

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

### 6.2 Sign-In Flow (GoogleAuthRepository.signIn)
1. **CredentialManager** → shows Google account picker → returns `GoogleIdTokenCredential` with user email, display name, avatar URL
2. **Identity.getAuthorizationClient** → requests Sheets + Drive scopes → may return a `PendingIntent` if user hasn't approved scopes yet
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
Clears all DataStore keys + deletes all Room tables (tasks, folders, labels, sync_queue).

---

## 7. Synchronization

### 7.1 SyncWorker (push → pull)
Triggered by WorkManager. Execution order:
1. **Push** — drain `SyncQueue` ordered by insertion:
   - `INSERT` → `SheetsApi.append(range = "sheet!A:lastCol")`
   - `UPDATE` → fetch current rows (cached per sheet per push) → find row number by entity ID → `SheetsApi.batchUpdate`
   - `DELETE` → same row-number lookup → `SheetsApi.clear`
   - On success: `syncQueueDao.deleteById(item.id)`
   - On failure: `syncQueueDao.incrementRetry(item.id)`
   - After all items: `syncQueueDao.deleteExhausted()` (removes items with retryCount ≥ 5)
2. **Delay** — if there were pending items: `delay(1_000 ms)` to let Sheets process writes before reading
3. **Pull** — `taskRepository.fetchAllAndSave(spreadsheetId)`:
   - `SheetsApi.batchGet(["tasks", "folders", "labels"])`
   - All three upserted inside a single Room transaction (`db.withTransaction`)
4. Retry: up to 4 attempts (`WorkManager` exponential backoff); returns `Result.failure()` after 5th

### 7.2 SyncManager
- **Periodic:** `PeriodicWorkRequest` every 30 min, constraint `CONNECTED`
- **One-off:** `triggerSync()` enqueues an immediate `OneTimeWorkRequest` (CONNECTED), replaces existing with `KEEP` policy
- **Initialized** in `TasksApplication.onCreate()`

### 7.3 SyncState
`StateFlow<SyncState>` combining WorkManager `WorkInfo` + `SyncQueueDao.countAll()`:
- `Idle` — no running worker, queue empty
- `Syncing` — worker is RUNNING
- `Pending(count)` — queue has items, no worker running

Displayed in the sidebar footer (count badge + spinning icon when Syncing).

### 7.4 NetworkObserver
`callbackFlow` wrapping `ConnectivityManager.registerNetworkCallback`. Emits `true`/`false`, `distinctUntilChanged`. Used in `MainViewModel` to trigger sync on reconnect.

---

## 8. UI Screens

### 8.1 Upcoming
**ViewModel:** `UpcomingViewModel`  
**Data:** Root `PENDING` tasks with a deadline, grouped by `deadlineDate`  
**Features:**
- Horizontal date strip (7 day-pills: date number + weekday letter + orange dot if tasks exist)
- Left/right navigation arrows to shift the visible week
- "Today" button (primary border when on the current week)
- Filter pills: priority chips + label chips + folder chip
- Tasks grouped by date under day headers (`"16 Apr · Thursday · Today"`)
- **Overdue section** — all past-due tasks collapsed into one group above the date strip's first day
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
**Data:** All tasks in a folder (any status? — pending only + recursive subtrees)  
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
- Priority filter chip (no label or folder filter — already filtered by label)
- EmptyState

### 8.6 Priority
**ViewModel:** `PriorityViewModel`  
**Data:** Root `PENDING` tasks for the selected priority  
**Features:**
- Tabs: Urgent / Important / Normal
- Sort: deadline → createdAt
- EmptyState per tab

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

## 10. TaskFormSheet (Create / Edit)

Single bottom sheet for both create and edit mode (`task != null` = edit).

**Fields (in order):** Title → Labels → Priority → Deadline (date + optional time) → Repeat → Folder

**Smart parsing in title field:** `@FolderName` sets folder (stripped from title); `#LabelName` adds label (stripped from title)

**Repeat row:** Checkbox + "Every [N] [days/weeks/months]" — hidden until a deadline date is selected

**Folder selector:** Scrollable popup, single select; pre-filled from current screen context

**Edit extras:** Delete task button (destructive, confirm dialog)

**Deeplink create:** `stlertasks://create` opens create form. `stlertasks://task/{id}` opens edit form for the task.

---

## 11. Widgets

All widgets use Jetpack Glance. Data is fetched inside `provideContent` using `collectAsState()` from Room Flows, so widgets react to Room changes automatically (no explicit `updateAll()` call needed after Room writes).

### 11.1 Shared Components

**WidgetHeader:** Row with widget title (left) and "+" button (right). "+" launches `stlertasks://create`.

**WidgetTaskRow:** Shared task row composable:
- Optional chevron (28dp) — expand/collapse subtasks via `ToggleExpandAction`
- Checkbox (32dp touch, 20dp visual) — priority-colored border + surface fill → `CompleteTaskAction`
- Title (2-line max) → opens `stlertasks://task/{id}` deeplink
- Row 2: deadline · #labels · folder (each with its own color)
- `timeOnly: Boolean` parameter — when true shows only time (for UpcomingWidget where date is in section header)
- `showExpandSpace: Boolean` — when false omits the 28dp chevron spacer (TaskListWidget)

**CompleteTaskAction:** Marks task complete in Room + enqueues UPDATE in SyncQueue + calls `WidgetRefresher.refreshAll()`.

**ToggleExpandAction:** Toggles `isExpanded` in Room (no sync queue entry — expanded state doesn't trigger `updatedAt`).

### 11.2 Upcoming Widget
Root pending tasks, `deadlineDate ≤ today + 7 days`, grouped by date (including Overdue section). `timeOnly = true` on WidgetTaskRow.

### 11.3 Folder Widget
Hierarchical: root tasks + expanded children (indented by `depth × 16dp`). Uses `isExpanded` from Room. `showExpandSpace = true`.

### 11.4 Task List Widget
Flat, root tasks only. Supports optional filter: folder / label / priority (configured per widget instance). `showExpandSpace = false`.

### 11.5 Widget Configuration (WidgetConfigActivity)
- Detects widget type via `AppWidgetManager.getAppWidgetInfo`
- **Upcoming:** auto-confirms immediately (no config needed)
- **Folder:** RadioButton list of all folders
- **Task List:** 3 `ExposedDropdownMenuBox` filters (folder / label / priority)
- Saves config via `updateAppWidgetState(PreferencesGlanceStateDefinition)`

### 11.6 Widget Colors
All colors are named `ColorProvider` constants in `WidgetColors.kt`, backed by XML color resources in `res/values/colors.xml` and `res/values-night/colors.xml` (same values in both — explicit palette, not Material You dynamic color).

| Constant | Hex |
|---|---|
| WPrimary | #e07e38 |
| WSurface | light: #FFFFFF / dark: #1C1C1E |
| WOnSurface | light: #1C1C1E / dark: #F2F2F7 |
| WOnSurfaceVariant | #8E8E93 |
| WDivider | #3C3C43 @ 18% opacity |
| WPriorityUrgent | #F87171 |
| WPriorityImportant | #FB923C |
| WPriorityNormal | #9CA3AF |
| WDeadlineOverdue | #F87171 |
| WDeadlineToday | #16A34A |
| WDeadlineTomorrow | #FB923C |
| WDeadlineThisWeek | #A78BFA |

---

## 12. Theme & Colors

### 12.1 App Colors (Color.kt)
| Constant | Hex | Usage |
|---|---|---|
| Primary | #e07e38 | Brand orange — buttons, active elements |
| PriorityUrgent | #F87171 | Urgent priority |
| PriorityImportant | #FB923C | Important priority |
| PriorityNormal | #9CA3AF | Normal priority |
| DeadlineOverdue | #F87171 | Past-due deadline |
| DeadlineToday | #16A34A | Due today |
| DeadlineTomorrow | #FB923C | Due tomorrow |
| DeadlineThisWeek | #A78BFA | Due this week |
| NavSelected | #E4E4E4 | Sidebar selected item (light mode) |
| AccentDark | #3A3A3C | Sidebar selected item (dark mode) |

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

Navigation is handled by Compose Navigation (`NavHost` in `MainScreen`). The sidebar `ModalNavigationDrawer` calls `onNavigate(route)` → `navController.navigate(route)`.

### 13.2 Deeplinks
| URI | Action |
|---|---|
| `stlertasks://task/{taskId}` | Open edit form for the task |
| `stlertasks://create` | Open create form (Inbox, default priority) |

Declared in `AndroidManifest.xml`. `MainActivity` receives the intent and passes the URI to `MainScreen`, where a `LaunchedEffect` routes to the correct action. Guard: if already on the target route, does not re-navigate.

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
**Trigger:** Push of a tag matching `v*` (e.g. `v1.3`)

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
   - Enable **Google Sheets API** and **Google Drive API**
   - Create **OAuth 2.0 Web Client ID** → copy to `app/src/main/res/values/strings.xml` as `google_web_client_id`
   - Create **OAuth 2.0 Android Client ID** (package `com.stler.tasks`, SHA-1 of debug keystore)
   - Set OAuth consent screen to **Production** (so any Google account can sign in)
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
