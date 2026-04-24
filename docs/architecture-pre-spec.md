# Stler Tasks Android — Technical Architecture Specification

**Document type:** Architecture + Product Spec (Pre-implementation)
**Date:** 2026-04-15
**Status:** Approved, ready for implementation
**App language:** English (all UI strings)
**Source of truth for UI:** PWA at `D:\Projects\Tasks` (scanned 2026-04-15)

---

## 1. Context

Existing PWA (Tasks) built with React + TypeScript + Google Sheets API v4, deployed on Vercel. Used primarily on desktop. This Android app replaces the PWA on mobile — full feature parity. Data is stored in the same Google Sheets document (`db_tasks`). No Play Store publishing; APK built for personal use.

---

## 2. Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Kotlin + Jetpack Compose |
| Navigation | Navigation Compose |
| Widgets | Jetpack Glance |
| Auth | Google Identity Services / Credential Manager |
| Local DB | Room |
| Network | Retrofit 2 + OkHttp + Gson |
| Background sync | WorkManager |
| DI | Hilt |
| Preferences | DataStore |
| Drag & Drop | `sh.calvin.reorderable` |
| Icons | `compose-icons-lucide` or equivalent (Lucide icons, outlined style) |
| Image loading | Coil (user avatar) |

---

## 3. Data Model

Full mirror of the Sheets schema. Spreadsheet name: `db_tasks`, 4 sheets.

### Task (sheet `tasks`, columns A–Q)

```
id              String      "tsk-xxxxxxxx"
parent_id       String      "" = root task
folder_id       String      "fld-inbox" default
title           String
status          "pending" | "completed"
priority        "urgent" | "important" | "normal"
deadline_date   String      "YYYY-MM-DD" or ""
deadline_time   String      "HH:MM" or "" (optional)
is_recurring    Boolean
recur_type      "days" | "weeks" | "months" | ""
recur_value     Int
labels          String      comma-separated label IDs, e.g. "lbl-abc,lbl-xyz"
sort_order      Int         multiples of 10 (0, 10, 20, ...)
created_at      String      ISO 8601
updated_at      String      ISO 8601 — used for conflict resolution
completed_at    String      ISO 8601 or ""
is_expanded     Boolean     subtask expand state, synced across devices and widgets
```

### Folder (sheet `folders`)
`id, name, color (hex), sort_order, created_at, updated_at`

Default Inbox: `id="fld-inbox"`, `color="#f97316"`

### Label (sheet `labels`)
`id, name, color (hex), created_at, updated_at`

### Settings (sheet `settings`)
A1 contains JSON: `{"sectionOpen": {"priorities": true, "folders": true, "labels": true}}`

### SyncQueue (Room only, not in Sheets)
`id (autoincrement), entity_type, operation, entity_id, payload_json, retry_count`

---

## 4. Colors & Icons

### Priority Colors (hard-coded)
| Priority | Hex |
|----------|-----|
| Urgent | `#f87171` (red-400) |
| Important | `#fb923c` (orange-400) |
| Normal | `#9ca3af` (gray-400) |

### Deadline Status Colors
| Status | Hex | Condition |
|--------|-----|-----------|
| Overdue | `#f87171` | before today |
| Today | `#16a34a` | same day |
| Tomorrow | `#fb923c` | next day |
| This week | `#a78bfa` | 2–7 days |
| Future | muted gray | >7 days |

### Label / Folder Color Presets (8 options)
`#ef4444`, `#f97316`, `#eab308`, `#22c55e`, `#06b6d4`, `#3b82f6`, `#8b5cf6`, `#6b7280`

### Theme Colors (from `index.css` CSS custom properties → converted to hex)

**Light mode:**
| Token | HSL | Hex |
|-------|-----|-----|
| background | `0 0% 100%` | `#ffffff` |
| foreground | `240 10% 10%` | `#18181f` |
| primary | `25 75% 55%` | `#e07e36` |
| primary-foreground | `0 0% 100%` | `#ffffff` |
| secondary / muted | `25 8% 95%` | `#f5f3f1` |
| muted-foreground | `0 0% 42%` | `#6b6b6b` |
| accent | `38 60% 96%` | `#fdf6ed` |
| accent-foreground | `25 60% 35%` | `#8f4f1c` |
| destructive | `0 70% 67%` | `#e96060` |
| border / input | `0 0% 88%` | `#e0e0e0` |

**Dark mode:**
| Token | HSL | Hex |
|-------|-----|-----|
| background | `0 0% 11%` | `#1c1c1c` |
| foreground | `0 0% 95%` | `#f2f2f2` |
| card | `0 0% 21%` | `#363636` |
| popover | `0 0% 14%` | `#242424` |
| primary | `25 65% 63%` | `#d98d52` |
| secondary / muted | `0 0% 21%` | `#363636` |
| muted-foreground | `0 0% 58%` | `#949494` |
| accent | `0 0% 18%` | `#2e2e2e` |
| accent-foreground | `0 0% 95%` | `#f2f2f2` |
| destructive | `0 55% 58%` | `#cc5252` |
| border | `0 0% 29%` | `#4a4a4a` |
| input | `0 0% 22%` | `#383838` |

### Icons (Lucide, Outlined)
| Icon | Usage |
|------|-------|
| CalendarClock | Upcoming nav item |
| LayoutList | All Tasks nav item |
| CheckCircle2 | Completed nav item |
| Flag | Priority (colored per level) |
| Inbox | Inbox folder |
| Folder | Other folders |
| Tag | Labels |
| GripVertical | Drag handle (Folder view, left of task) |
| ChevronDown / ChevronRight | Expand/collapse |
| ChevronLeft / ChevronRight | Week navigation |
| Clock | Deadline action button; colored if deadline set |
| Plus | Add task / Add subtask |
| Pencil | Edit task |
| Trash2 | Delete (hover-visible in Completed) |
| RotateCcw | Restore completed task |
| MoreHorizontal | Mobile overflow menu |
| RefreshCw | Recurring indicator; sync spinning |
| ListChecks | Subtask count badge |
| ListTodo | App logo |
| LogOut | Sign out |
| Menu | Mobile sidebar toggle |
| WifiOff | Offline status |
| AlertCircle | Sync error |

---

## 5. Auth

1. First launch → `AuthScreen` with **Sign in with Google** button (Credential Manager)
2. OAuth 2.0, scopes:
   - `https://www.googleapis.com/auth/spreadsheets`
   - `https://www.googleapis.com/auth/drive.metadata.readonly`
3. After sign-in → Drive API: find spreadsheet named `db_tasks` → save `spreadsheetId`
4. DataStore: `accessToken`, `tokenExpiry`, `spreadsheetId`, `userEmail`, `userName`, `userAvatarUrl`
5. Token refresh before expiry
6. Sign-out: clear DataStore + Room

---

## 6. Navigation & Layout

### Navigation Drawer (left slide-out)

```
[+ Add task]           ← primary button
───────────────────
Upcoming               CalendarClock icon
All tasks              LayoutList icon
Completed              CheckCircle2 icon
───────────────────
Priorities ▾           collapsible (ChevronDown/Right)
  · Urgent             Flag #f87171
  · Important          Flag #fb923c
  · Normal             Flag #9ca3af
───────────────────
Folders ▾              collapsible; "+" to add
  · Inbox              Inbox icon #f97316
  · [folder name]      Folder icon [folder.color]
  · ...
───────────────────
Labels ▾               collapsible; "+" to add
  · [label name]       Tag icon [label.color]
  · ...
───────────────────
[sync footer]          RefreshCw (spinning if syncing) + "Synced HH:mm" / "Syncing..." / "Not synced"
```

- Section collapse state → DataStore, synced with Sheets `settings.sectionOpen`
- Long-press or "..." on folder/label → context menu: Edit, Delete
- Deleting folder → tasks moved to Inbox (with confirmation)

### TopAppBar (Android addition not in PWA)

```
[≡]  [Screen title]            [sync-icon]  [avatar]
```

- **Left**: hamburger (open Drawer)
- **Center**: current screen name
- **Right (1)**: sync status icon
  - Spinning RefreshCw → syncing
  - Cloud + number → N changes pending
  - Cloud + check → idle/synced
- **Right (2)**: circular user avatar (Coil) → dropdown: user name/email + **Sign out** (LogOut icon, destructive color)

---

## 7. Screens

### 7.1 Upcoming

- **Date strip** (horizontal, scrollable):
  - 7 day-pills: shows day number + first letter of weekday
  - Each pill has a dot indicator below: green (today), primary orange (has tasks), transparent (empty)
  - Navigation arrows (ChevronLeft / ChevronRight) to shift week by ±1
  - **"Today" button** to the right of the strip: bordered primary if on current week, muted otherwise
  - Selected pill: `bg-accent` background; active date tracked by IntersectionObserver
- **Filter pills** (below strip): priority filter chips + label filter chips
- **Task list**: grouped by date header
  - "Overdue" (`text-red-400`)
  - "Today" (`text-green-600`)
  - "Tomorrow" (`text-orange-400`)
  - Date strings for future days
- **Root tasks only** (no subtasks shown)
- Only tasks that have a `deadline_date`

### 7.2 All Tasks

- **Filter pills**: priority chips + label chips
- Flat list of all pending root tasks
- Sort: priority (urgent→important→normal) → deadline (earliest first) → created_at

### 7.3 Folder

- **Hierarchical** view: root tasks → subtasks (indented `depth × 20dp`)
- `is_expanded` controls subtask visibility (synced with Sheets and Folder widget)
- **Drag handle** (GripVertical, 14dp) on the far **left** of each task
  - Vertical drag: reorders within same parent (sort_order = index × 10)
  - Horizontal drag >50dp right: reparents task under the task it's dragged over
- Task action buttons visible on right (see §8)

### 7.4 Label

- Flat list of pending root tasks that have `selectedLabelId` in their `labels` field
- Filter pills: priority chips only
- Sort: priority → deadline → created_at

### 7.5 Priority

- Three tabs or sub-nav: Urgent / Important / Normal
- Root pending tasks for selected priority
- Sort: deadline → created_at

### 7.6 Completed

- Tasks where `status = "completed"`
- Sort: `completed_at` desc (fallback `updated_at`)
- One-line display: title (strikethrough, opacity 70%) + metadata below
- Metadata: completion timestamp (`MMM d, yyyy HH:mm`), label badges, folder name

---

## 8. Task Item

### Row 1 (always visible)
```
[expand icon?]  [checkbox]  [title]  [action buttons]
```

- **Expand/collapse** (ChevronDown/Right, 13dp): shown only if task has pending children; indented by `depth × 20dp`
- **Checkbox** (16dp): border color = priority color; `checked` state = filled with priority color
- **Title**: normal weight; on long-press or swipe → action buttons visible on mobile
- **Action buttons** (always 6, right side):

| Icon | Action | Note |
|------|--------|------|
| Clock (15dp) | Set/edit deadline | Colored with deadline status if deadline exists |
| Flag (15dp) | Change priority | Colored with current priority |
| Tag (15dp) | Assign/remove labels | Dropdown label picker |
| Plus (15dp) | Add subtask | Opens Create Task sheet with parent_id set |
| Pencil (15dp) | Edit task | Opens Edit Task sheet |
| Trash2 (15dp) | Delete task | Confirmation dialog |

**Mobile**: show Clock always + MoreHorizontal icon → bottom sheet/dropdown with: Priority submenu, Labels submenu, Add subtask, Edit, Delete

### Row 2 (metadata, shown if task has deadline / labels / folder / is_recurring / children)
```
[RefreshCw?]  [deadline label]  [label badges]  [folder name?]  [subtask count?]
Left padding: 52dp  gap: 12dp
```

- **RefreshCw** (12dp, muted opacity-60): shown if `is_recurring = true`
- **Deadline label**: colored text (see §4), format examples: "Yesterday", "Today 14:30", "Tomorrow", "Wednesday", "25 Dec"
- **Label badges**: Tag icon (12dp, label.color) + label name, colored
- **Folder name**: gray text; shown when "showFolder" context (e.g., in Upcoming/AllTasks)
- **Subtask count**: ListChecks icon (12dp) + "X/Y" (completed/total)

### Completed task row
```
[RotateCcw]  [title strikethrough]  [metadata]  [Trash2 on hover]
```
- RotateCcw → restore to pending
- Trash2 → permanent delete (visible on hover/long-press)

---

## 9. Create / Edit Task Sheet

**Bottom sheet**, max height 90%, scrollable.

### Fields (in order)

1. **Title** (required)
   - Placeholder: "Task name"
   - Auto-focus on open
   - **Smart parsing**: `@FolderName` → sets folder (removed from title); `#LabelName` → adds label (removed from title)

2. **Labels** (optional, multi-select chips)
   - "New" button (inline) → shows 8-color picker + name input → Enter to create, Escape to cancel
   - Chips: border colored, `bg-accent` if selected
   - Color presets: `#ef4444 #f97316 #eab308 #22c55e #06b6d4 #3b82f6 #8b5cf6 #6b7280`

3. **Priority** (3 equal-width buttons)
   - Urgent | Important | Normal
   - Selected: colored border + colored text + `bg-accent` background
   - Default for new tasks: Normal

4. **Deadline** (two columns)
   - Left: "Due date" — DatePicker
   - Right: "Time" — TimePicker (optional)

5. **Repeat** (checkbox + conditional)
   - Checkbox: "Recurring task"
   - If checked: "Every [number 1–365] [days / weeks / months]"
   - Indented (ml-24dp)

### Bottom buttons: **Cancel** (outlined) + **Create** / **Save** (primary)

### Default folder logic
- If current screen is a specific folder → pre-fill that folder
- Otherwise → Inbox
- Tapping folder field → scrollable popup list, single select

### Edit Task differences
- Title: "Edit task"
- Button: "Save"
- All fields pre-filled
- No "Add subtask" in this sheet (use action button on task row instead)
- "Delete task" button (destructive, with confirmation)

---

## 10. Recurring Tasks

- Completing a recurring task → **does not** mark as completed
- Instead: advances `deadline_date` to next occurrence (`getNextDueDate`)
- Formula: `deadline_date + recur_value × recur_type`
- Updates `updated_at`
- Shown in all views with new deadline

---

## 11. Folder & Label Management

From sidebar context menu ("..." on hover / long-press):
- **Folders**: Edit (name + color), Delete (confirm: tasks → Inbox)
- **Labels**: Edit (name + color), Delete (label removed from tasks)
- Color picker: 8 preset circles + selected border highlight
- "+" button in section header → create new folder/label

---

## 12. Synchronization

### Pull (Sheets → Room)
- `SyncWorker`: `PeriodicWorkRequest` every 30 minutes
- Also on network reconnect (`NetworkCallback`)
- Fetches `tasks!A:Q`, `folders!A:F`, `labels!A:D`, `settings!A1`
- Conflict: `updated_at` — newer wins
- After pull: refresh all active Glance widgets

### Push (Room → Sheets)
- Any mutation → immediately update Room (optimistic) + enqueue to `SyncQueue`
- `SyncWorker` drains queue: `values.update` (existing) / `values.append` (new) / mark deleted
- Retry: WorkManager exponential backoff, max 5 attempts

### Sync status (shown in TopAppBar)
- Spinning → in progress
- Badge count → pending items in SyncQueue
- Check → idle

### Expand state (`is_expanded`)
- Synced to Sheets **without** bumping `updated_at` (to avoid overwriting real edits)
- Shared between app and Folder widget

---

## 13. Widgets (Jetpack Glance)

### Common
- Default size: **4×4** (resizable)
- Per task: checkbox left (complete → optimistic + SyncQueue), title tap → deeplink to task edit
- "+" in widget header → opens app on Create Task screen
- No action buttons (no Clock/Flag/Tag/Plus/Pencil/Trash)
- Theme follows system (dark/light)

### 13.1 Widget "Upcoming Tasks"
- Root pending tasks with deadline in next 7 days
- No day-selector strip
- Grouped by day: Today / Tomorrow / Wed Apr 16 / ...
- Deadline color coding (same as app)
- **No subtasks**
- No config; WidgetConfigActivity shows only "Add widget"

### 13.2 Widget "Folder"
- User selects folder at config time
- **Hierarchical**: root tasks + subtasks with indent (mirrors FolderScreen)
- `is_expanded` shared with app (same Room field, same Sheets sync)
- Sorted by `sort_order`
- No drag-and-drop (widget limitation)

### 13.3 Widget "Task List"
- Flat list of pending root tasks
- Optional filter combination at config time: Folder + Label + Priority (any combination)
- No selection → all pending root tasks
- Sort: priority → deadline → sort_order

---

## 14. Widget Config Activity

System Activity, launched by Android when user adds widget.

- **Upcoming**: no config, just "Add widget" button
- **Folder**: required folder selection (dropdown)
- **Task List**: 3 optional dropdowns: Folder / Label / Priority

Config stored in DataStore keyed by `widget_config_{appWidgetId}`.

Deeplinks: `MainActivity` handles `stlertasks://task/{taskId}` and `stlertasks://create`.

---

## 15. Project Structure

```
D:\Projects\Tasks_android\
├── docs/
│   ├── technical-doc-PWA.html
│   └── architecture-spec.md     ← this document
├── tasks.md                     ← task decomposition with progress
└── app/
    └── src/main/java/com/stler/tasks/
        ├── data/
        │   ├── local/           Task/Folder/Label/SyncQueue entities + DAOs + TaskDatabase
        │   ├── remote/          SheetsApi (Retrofit) + SheetsClient (OkHttp) + SheetsMapper
        │   └── repository/      TaskRepository
        ├── domain/model/        Task, Folder, Label (plain data classes)
        ├── ui/
        │   ├── MainActivity.kt  (single Activity, NavHost, handles deeplinks)
        │   ├── auth/            AuthScreen
        │   ├── main/            MainScreen (Scaffold + Drawer), SidebarMenu, TopAppBar
        │   ├── tasks/           UpcomingScreen, AllTasksScreen, FolderScreen,
        │   │                    LabelScreen, PriorityScreen, CompletedScreen
        │   ├── task/            TaskItem (composable), CreateTaskSheet, TaskDetailSheet,
        │   │                    DeadlinePickerDialog, PriorityPickerSheet, LabelPickerSheet
        │   └── widgetconfig/    WidgetConfigActivity
        ├── widget/
        │   ├── common/          TaskRow (Glance), WidgetHeader
        │   ├── upcoming/        UpcomingWidget + UpcomingWidgetReceiver
        │   ├── folder/          FolderWidget + FolderWidgetReceiver
        │   └── tasklist/        TaskListWidget + TaskListWidgetReceiver
        ├── sync/                SyncWorker, NetworkObserver
        └── di/                  AppModule (Hilt)
```

---

## 16. Key Dependencies

```gradle
// Widgets
implementation("androidx.glance:glance-appwidget:1.1.0")

// Room
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// WorkManager
implementation("androidx.work:work-runtime-ktx:2.9.0")

// Google Auth
implementation("com.google.android.gms:play-services-auth:21.0.0")
implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

// Network
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// Hilt
implementation("com.google.dagger:hilt-android:2.51")
kapt("com.google.dagger:hilt-android-compiler:2.51")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.0.0")

// Navigation
implementation("androidx.navigation:navigation-compose:2.7.7")

// Drag & Drop
implementation("sh.calvin.reorderable:reorderable:2.1.1")

// Image loading (avatar)
implementation("io.coil-kt:coil-compose:2.6.0")
```

---

## 17. Verification Checklist

- [x] Sign in → `db_tasks` found → main screen with sidebar
- [x] Add task in PWA → sync in Android → task appears
- [x] Add task in Android → appears in PWA
- [x] Default folder in Create Task = current folder screen or Inbox
- [x] @FolderName and #LabelName smart parsing in title field
- [x] Inline label creation in task modal
- [x] Priority color on checkbox border
- [x] Deadline color coding on task metadata row
- [x] Task action buttons (all 6) visible on all pending-task screens
- [x] Mobile: Clock + MoreHorizontal → full action menu
- [x] Completed screen: RotateCcw + Trash2 only
- [x] Recurring task: completion advances deadline, does not mark done
- [x] Subtask count "X/Y" badge shown on tasks with children
- [x] Folder view: drag-and-drop vertical (reorder) + horizontal (reparent)
- [x] Folder view: is_expanded state persists across app restarts
- [x] Widget Folder: is_expanded shared with app
- [x] Widget checkbox: marks task complete → changes in Sheets
- [x] Widget task tap: opens app on task edit screen
- [x] Widget "+" tap: opens app on create screen
- [x] Dark mode correct on all screens and widgets
- [x] Collapse state of sidebar sections syncs with PWA
- [x] APK installs and runs without Play Store
