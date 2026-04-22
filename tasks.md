# Stler Tasks Android — Task Decomposition

**Updated:** 2026-04-21
**Spec:** `docs/architecture-spec.md`
**Source reference:** PWA at `D:\Projects\Tasks` (scanned for full UI parity)

**Status keys:** `[ ]` not started · `[~]` in progress · `[x]` done

---

## Stage 1 — Project Initialization

- [x] 1.1 Create Android project (Kotlin, minSdk 26, targetSdk 34, Compose enabled)
- [x] 1.2 Configure `build.gradle` with all deps (AGP 9.1.1 + Kotlin 2.2.10; KSP 2.2.10-2.0.2; Hilt 2.59.2)
- [x] 1.3 Google Cloud Project setup (SHA-1 added, Client ID obtained)
- [x] 1.4 Configure Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, KSP processor)
- [x] 1.5 Set up package structure (data / domain / ui / widget / sync / di)

---

## Stage 2 — Data Layer

- [x] 2.1 Domain models: `Task.kt`, `Folder.kt`, `Label.kt` (plain data classes matching spec §3)
- [x] 2.2 Room:
  - `TaskEntity`, `FolderEntity`, `LabelEntity`, `SyncQueueEntity`
  - `TaskDao`, `FolderDao`, `LabelDao`, `SyncQueueDao`
  - `TaskDatabase.kt`
- [x] 2.3 Retrofit:
  - `SheetsApi.kt` — batchGet / append / batchUpdate / clear
  - `NetworkModule.kt` — OkHttp + Bearer interceptor + 401 Authenticator + logging
  - `TokenProvider` interface + `StubTokenProvider` (Stage 3 will replace)
- [x] 2.4 `SheetsMapper.kt` — bidirectional row ↔ entity (tasks 17 cols A–Q, folders 4 cols A–D, labels 3 cols A–C; dateStr() handles Sheets serial-date numbers)
- [x] 2.5 `TaskRepository` interface + `TaskRepositoryImpl`:
  - `fetchAllAndSave()` — batchGet → Room upsert
  - Full CRUD (Task, Folder, Label): Room write + SyncQueue enqueue
  - Recurring task completion: advance deadline, do NOT mark done
  - Flows for all UI screens
  - `DatabaseModule`, `RepositoryModule`, `AuthModule` Hilt modules

---

## Stage 3 — Authentication

- [x] 3.1 `AuthScreen.kt` — Sign in with Google button (CredentialManager); handles NeedsAuthorization via IntentSenderRequest launcher
- [x] 3.2 OAuth 2.0: CredentialManager → Google ID token; Identity.getAuthorizationClient → Sheets + Drive scope access token
- [x] 3.3 Drive API: OkHttp call to Drive v3 files.list → finds db_tasks spreadsheetId
- [x] 3.4 `AuthPreferences` (DataStore): accessToken, tokenExpiry, spreadsheetId, userEmail, userName, userAvatarUrl
- [x] 3.5 Token refresh: GoogleAuthRepository.refreshToken() via Identity.authorize() — silent, no Activity needed
- [x] 3.6 Sign-out: clearAll DataStore + deleteAll Room tables
- [x] 3.7 `MainActivity`: Loading → spinner; SignedIn → main placeholder (Stage 5); else → AuthScreen

---

## Stage 4 — Synchronization

- [x] 4.1 `SyncWorker.kt` (@HiltWorker): push SyncQueue → Sheets (INSERT/UPDATE/DELETE with row-cache); pull → fetchAllAndSave; retry max 4 times
- [x] 4.2 `SyncManager.initialize()` → PeriodicWorkRequest (30 min, CONNECTED); `triggerSync()` for manual one-off
- [x] 4.3 `NetworkObserver` — callbackFlow wrapping ConnectivityManager; distinctUntilChanged
- [x] 4.4 `SyncState` (Idle/Syncing/Pending) — Flow combining WorkManager state + SyncQueue count
- [x] 4.5 `is_expanded` sync: `toggleExpanded()` does NOT update `updatedAt` (implemented in TaskRepositoryImpl)
- [x] 4.x HiltWorkerFactory wired in TasksApplication (Configuration.Provider); WorkManagerInitializer disabled in manifest

---

## Stage 5 — App Shell

- [x] 5.1 `MainActivity.kt` — routes Loading/SignedIn/else; deeplinks deferred to Stage 8
- [x] 5.2 `MainScreen.kt` — ModalNavigationDrawer + Scaffold + NavHost (6 routes); dynamic TopAppBar title from folders/labels data
- [x] 5.3 `SidebarMenu.kt` — full sidebar per spec: + Add task · Upcoming/AllTasks/Completed · Priorities (collapsible) · Folders (collapsible, MoreVert → Edit/Delete) · Labels (collapsible, MoreVert → Edit/Delete) · sync footer
- [x] 5.x `TasksTopAppBar.kt` — spinning sync icon (BadgedBox for Pending), Coil avatar, user dropdown with Sign out
- [x] 5.x `SidebarPreferences.kt` — DataStore for section collapse state (priorities/folders/labels)
- [x] 5.x `MainViewModel.kt` — folders, labels, syncState, authData, sidebarState; deleteFolder/deleteLabel
- [x] 5.x Placeholder screens for Stage 7 (Upcoming, AllTasks, Completed, Folder, Label, Priority)

---

## Stage 6 — Task Item Component

- [x] 6.1 `TaskItem.kt` composable (reused across all screens):
  - Row 1: expand icon (if has children) | checkbox (priority-colored border/fill) | title | action buttons
  - Row 2 (conditional): RefreshCw (recurring) | deadline label (colored) | label badges (Tag icon + name) | folder name (if showFolder) | subtask count "X/Y" (ListChecks)
  - Indent: `depth × 20dp`
  - **Mobile**: Clock always visible + MoreHorizontal → bottom sheet with: Priority submenu, Labels submenu, Add subtask, Edit, Delete
- [x] 6.2 `DeadlinePickerDialog.kt` — date picker + optional time picker (triggered by Clock button)
- [x] 6.3 `PriorityPickerSheet.kt` — bottom sheet with 3 priority options + checkmark on current
- [x] 6.4 `LabelPickerSheet.kt` — multi-select label list with checkmarks; option to create new label
- [x] 6.x `TaskCheckbox.kt` — custom Canvas-drawn checkbox (priority-colored border/fill + white checkmark)
- [x] 6.x `TaskColors.kt` — priority/deadline color constants + deadlineStatus() + deadlineLabel()

---

## Stage 7 — Task Screens

- [x] 7.1 `UpcomingScreen.kt` + `UpcomingViewModel.kt`:
  - Horizontal date strip: 7 day-pills (number + weekday letter + dot indicator)
  - Navigation arrows (ChevronLeft/Right) to shift week ±1
  - "Today" button to the right of strip (primary border if on current week)
  - Filter pills: priority chips + label chips below strip
  - Root tasks with deadline, grouped by selected date
- [x] 7.2 `AllTasksScreen.kt` + `AllTasksViewModel.kt`:
  - Filter pills: priority chips + label chips
  - Root pending tasks, sort: priority → deadline → created_at
- [x] 7.3 `FolderScreen.kt` + `FolderViewModel.kt`:
  - Hierarchical: root tasks + subtasks (indented depth × 20dp)
  - is_expanded controls subtask visibility; synced to Room + Sheets
  - GripVertical drag handle on far left
  - Vertical drag: reorder within parent (sort_order = index × 10)
  - Horizontal drag >50dp: reparent task under target
- [x] 7.4 `LabelScreen.kt` + `LabelViewModel.kt`:
  - Root pending tasks with selected label; filter pills (priority only)
  - Sort: priority → deadline → created_at
- [x] 7.5 `PriorityScreen.kt` + `PriorityViewModel.kt`:
  - Tabs: Urgent / Important / Normal
  - Root pending tasks for selected priority; sort: deadline → created_at
- [x] 7.6 `CompletedScreen.kt` + `CompletedViewModel.kt`:
  - Completed tasks, sort by completed_at desc
  - TaskItem in completed mode: strikethrough title, Restore + Delete buttons

---

## Stage 8 — Task Create / Edit

- [x] 8.1 `TaskFormSheet.kt` (bottom sheet, handles create + edit):
  - Fields in order: Title → Labels → Priority → Deadline (date + optional time) → Repeat
  - Title: auto-focus; smart parsing: @FolderName sets folder, #LabelName adds label (both stripped from title)
  - Labels: multi-select chips; "New" button → inline color picker (8 presets) + name input → Enter creates
  - Priority: 3 equal-width buttons (Urgent/Important/Normal), default Normal
  - Deadline: date picker + optional time picker (reuse DeadlinePickerDialog)
  - Repeat: checkbox; if checked → "Every [N] [days/weeks/months]"
  - Folder: pre-filled by current screen context or Inbox; tap → scrollable popup, single select
  - Buttons: Cancel | Create
- [x] 8.2 Edit mode: same `TaskFormSheet` with `task != null`; FAB in MainScreen + onEdit/onAddSubtask wired to all screens
  - Same fields pre-filled
  - Button: Save
  - Extra: "Delete task" (destructive, confirm dialog)
  - No "Add subtask" field here (use Plus action button on task row)
- [x] 8.3 Folder management dialogs: Create (name + 8-color picker), Edit, Delete confirm → tasks to Inbox
- [x] 8.4 Label management dialogs: same as folders, no task migration
- [x] 8.5 Recurring task logic: on task complete, if is_recurring → advance deadline, do NOT mark completed

---

## Stage 9 — Widgets

- [x] 9.1 Glance `WidgetTaskRow.kt` (shared):
  - Checkbox left → CompleteTaskAction (optimistic + SyncQueue)
  - Title row → deeplink `stlertasks://task/{taskId}`
  - Priority color bar (4dp)
  - Deadline color label
- [x] 9.2 `WidgetHeader.kt` (shared): widget title + "+" button → `stlertasks://create`
- [x] 9.3 Widget "Upcoming Tasks":
  - `UpcomingWidget.kt` + `UpcomingWidgetReceiver.kt`
  - Root pending tasks, deadline ≤ 7 days, sorted by date
  - Deadline colors same as app
- [x] 9.4 Widget "Folder":
  - `FolderWidget.kt` + `FolderWidgetReceiver.kt`
  - Hierarchical: root + expanded children with indent (depth × 16dp)
  - is_expanded from Room (shared with app)
- [x] 9.5 Widget "Task List":
  - `TaskListWidget.kt` + `TaskListWidgetReceiver.kt`
  - Flat, root only, optional filter: folder / label / priority (from prefs)
- [x] 9.6 `WidgetConfigActivity.kt`:
  - Detects widget type via AppWidgetManager.getAppWidgetInfo
  - Upcoming: auto-confirms immediately
  - Folder: RadioButton folder list
  - Task List: 3 ExposedDropdownMenuBox filters (folder/label/priority)
  - Saves config via updateAppWidgetState (PreferencesGlanceStateDefinition)
- [x] 9.7 Register all 3 widgets in `AndroidManifest.xml` + `res/xml/widget_*_info.xml`
- [x] 9.8 Deeplink handling: `MainActivity` passes URI to `MainScreen`; LaunchedEffect routes `stlertasks://task/{id}` → openEdit, `stlertasks://create` → openCreate
- [x] 9.x `WidgetEntryPoint.kt` — Hilt EntryPoint for widgets; `CompleteTaskAction.kt` — ActionCallback

---

## Stage 9b — Bug Fixes & UX Improvements

_Discovered during testing after Stage 9._

### Data correctness
- [x] 9b.1 Google Sheets "'" prefix — `valueInputOption="RAW"` sends native types; `int()` / `bool()` helpers handle both `Number` and `String` returns from Sheets. No apostrophe prefix needed. ✅ Verified.
- [x] 9b.2 Folder not persisted — folder write path verified correct: 4 columns A–D, INSERT uses `append` (full row), UPDATE uses `lastColOf("folder")="D"`. Root cause was labels using col C not D (fixed in 9b.4). ✅ Verified.
- [x] 9b.3 Full data-schema audit — tasks 17 cols (A–Q), folders 4 cols (A–D), labels 4 cols (A–D); all column positions, booleans ("TRUE"/"FALSE"), integers (RAW Int), dateStr() serial-date conversion verified correct. ✅ Done.
- [x] 9b.4 Labels sort_order — read column D from the `labels` sheet; sort labels in sidebar by `sort_order` ascending. Domain model, entity, DAO ORDER BY, SheetsMapper, SyncWorker lastColOf, DB v3. ✅ Done (session 5).

### Task behaviour
- [x] 9b.5 Complete with subtasks — when completing a task, recursively complete all descendants at every depth (same as PWA). `completeDescendants()` added to `TaskRepositoryImpl`. ✅ Done (session 3).
- [x] 9b.6 Postpone uses recur_value × recur_type — `DeadlinePickerDialog` Postpone button now advances by the task's own recurrence interval (days/weeks/months). ✅ Done (session 3).
- [x] 9b.7 Overdue grouping — all past-due tasks collapsed into single "Overdue" section in Upcoming screen and UpcomingWidget. ✅ Done (session 3).

### Sync & performance
- [ ] 9b.8 Drag-to-reorder creates too many sync operations — current implementation fires `updateTask()` on every intermediate drag position, generating 50–300+ sync queue entries per drag. Fix: accumulate the final order only, write once when drag ends; use `sortOrder` diff, not `updatedAt`.
- [ ] 9b.9 Widget refresh instability — completing a task from a widget sometimes takes 5–15 min for the widget to reflect the change. Investigate Glance `updateAll()` timing; ensure `refreshAll()` is called reliably after every mutation and that `provideGlance` re-reads fresh data.

### UI/UX
- [x] 9b.10 Primary color → `#e07e38` (matches PWA orange). App icon background color verified correct. ✅ Done (session 3).
- [x] 9b.11 Sidebar narrower — 70% of screen width instead of ~90%. `ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.70f))`. ✅ Done (session 3).
- [x] 9b.12 Filter bar — redesigned to 3 icon-only chips (priority/labels/folders), each with its own DropdownMenu; count badge when active; no text wrapping. ✅ Done (session 5).
- [x] 9b.13 Dividers in widgets — solid `WDivider` `ColorProvider` between task rows (was invisible semi-transparent). ✅ Done (session 4).
- [x] 9b.14 Widget header padding — `vertical = 10.dp` top/bottom in `WidgetHeader`. ✅ Done (session 3).
- [x] 9b.15 Widget header & day-header font weight — `FontWeight.Medium` (between Bold and Normal). ✅ Done (session 3).
- [x] 9b.16 Widget colour palette — explicit `WPrimary / WSurface / WOnSurface / WOnSurfaceVariant` `ColorProvider` constants; no longer depends on Material You dynamic colour. ✅ Done (session 4).
- [x] 9b.17 Upcoming day-header weight — `FontWeight.Medium`. ✅ Done (session 3).
- [x] 9b.18 Deadline dialog — both chips use `FilterChip(selected=false)` border style; time chip hidden until date is set; dialog converted to `ModalBottomSheet` for full width. ✅ Done (sessions 3–4).
- [x] 9b.19 TaskFormSheet — calendar icon on date chip; time field hidden until date is set; chip borders consistent with deadline dialog. ✅ Done (session 3).
- [x] 9b.20 Repeat UI redesign — inline `Checkbox` + `RepeatRow` in both `DeadlinePickerDialog` and `TaskFormSheet`. ✅ Done (session 3).
- [x] 9b.21 Slow animations — form sheet opens quickly (`skipPartiallyExpanded = true`); keyboard slide-up animation is a system-level behaviour and cannot be easily suppressed from app code. ✅ Done.
- [x] 9b.24 Date header order — "16 Apr · Thursday · Today" (Today/Tomorrow label moved to end) in both UpcomingScreen and UpcomingWidget. ✅ Done (session 5).
- [x] 9b.25 Repeat row hidden when no date — RepeatRow not shown in TaskFormSheet and DeadlinePickerDialog until a deadline date is selected. ✅ Done (session 5).
- [x] 9b.26 Folder filter — folder dropdown chip added to FilterBar on AllTasks, Upcoming and Completed screens; ViewModels updated with folderFilter flow + toggleFolderFilter(). ✅ Done (session 5).
- [x] 9b.22 LabelScreen hides label filter (showLabelFilter=false); FolderScreen label implicit. ✅ Done (session 3).
- [x] 9b.23 Cold-start blank Upcoming from widget tap — guard against re-navigation when already on UPCOMING route. ✅ Done (session 3).

---

## Stage 10 — Polish & Build

- [ ] 10.1 Dark/light theme: verify all screens and widgets against spec §4 color values
- [ ] 10.2 Priority and deadline colors: cross-check exact hex values
- [ ] 10.3 All icons: Lucide outlined, correct icon per action (see spec §4)
- [ ] 10.4 Error handling: offline state, expired token, sync failure → user-visible feedback
- [ ] 10.5 Build debug APK: `./gradlew assembleDebug`
- [ ] 10.6 Run verification checklist from spec §17

---

*To continue in a new session: read this file and `docs/architecture-spec.md`, find the first `[~]` or first `[ ]` item and continue from there.*
