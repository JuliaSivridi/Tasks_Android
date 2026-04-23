# Stler Tasks — Android

A native Android task manager — the companion app to [Stler Tasks PWA](https://stler-tasks.vercel.app). Shares the same Google Sheets backend, so tasks sync seamlessly between the web app and the phone.

> Inspired by [Todoist](https://todoist.com/) — one of the finest personal task managers out there. Stler Tasks delivers a similar UX as a native Android app backed solely by Google Sheets, with no subscription fee.

---

## Features

- **Multiple views** — Upcoming (day-grouped with week strip), All Tasks, Priority, Folders, Labels, Completed
- **Priority levels** — Urgent / Important / Normal with color-coded flags and dedicated sidebar navigation
- **Task hierarchy** — subtasks with expand/collapse and drag-to-reorder within folders
- **Indent / outdent** — reparent tasks via the "..." menu (Make subtask of above / Move up a level)
- **Deadlines** — date + optional time, color-coded: overdue (red) / today (green) / tomorrow (orange) / this week (violet)
- **Recurring tasks** — daily / weekly / monthly; completing advances the deadline automatically
- **Labels & Folders** — organize tasks with colored labels and folders; both collapsible in the sidebar
- **Home screen widgets** — Upcoming tasks, Folder tasks, and configurable Task List widgets via Glance
- **Offline-first** — full read/write without internet via Room (SQLite); syncs automatically on reconnect
- **Google Sheets sync** — tasks, folders, and labels live in the user's own `db_tasks` spreadsheet

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Widgets | Jetpack Glance |
| Architecture | MVVM + Repository |
| DI | Hilt |
| Local DB | Room (SQLite) |
| Async | Coroutines + Flow |
| Navigation | Navigation Compose |
| Auth | Credential Manager + Google Identity Services (OAuth 2.0) |
| Network | Retrofit + OkHttp |
| Sync | WorkManager |
| Drag & drop | sh.calvin.reorderable |
| Remote DB | Google Sheets API v4 |
| Min SDK | 26 (Android 8.0) |

## Setup

### Prerequisites

- Google account
- Google Cloud project with **Google Sheets API v4** and **Google Drive API v3** enabled
- OAuth 2.0 Client ID — type: **Android** (with your app's SHA-1) + type: **Web application** (for token exchange)
- Android Studio Hedgehog or newer

### Google Cloud Console

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Enable **Google Sheets API v4** and **Google Drive API v3**
3. Create an OAuth 2.0 Client ID → type: **Android**
   - Package name: `com.stler.tasks`
   - SHA-1: run `./gradlew signingReport` to get it
4. Create an OAuth 2.0 Client ID → type: **Web application** (needed for the token exchange flow)
5. Add your Google account as a **test user** in the OAuth consent screen

### Local Development

```bash
git clone https://github.com/JuliaSivridi/Tasks_Android.git
cd Tasks_Android
```

Add your Web Client ID to `app/src/main/res/values/strings.xml`:
```xml
<string name="google_web_client_id">YOUR_WEB_CLIENT_ID.apps.googleusercontent.com</string>
```

Open the project in Android Studio and run on a device or emulator (API 26+).

## Data Model

Shares the same `db_tasks` Google Spreadsheet as the PWA. The app finds it automatically via Drive API on first login.

| Sheet | Columns |
|---|---|
| `tasks` | id, parent_id, folder_id, title, status, priority, deadline_date, deadline_time, is_recurring, recur_type, recur_value, labels, sort_order, created_at, updated_at, completed_at, is_expanded |
| `folders` | id, name, color, sort_order |
| `labels` | id, name, color, sort_order |

## Architecture

```
ui/
  main/          — Navigation drawer, top bar, main scaffold
  upcoming/      — Week strip + day-grouped task list
  alltasks/      — Flat task list with priority/label/folder filters
  folder/        — Hierarchical task list with drag-to-reorder
  label/         — Tasks filtered by label
  priority/      — Tasks filtered by priority level
  completed/     — Completed tasks with restore/delete
  task/          — TaskItem, TaskFormSheet, DeadlinePickerDialog, etc.
  theme/         — Color palette, Typography, Theme
data/
  local/         — Room database, DAOs, entities
  remote/        — Retrofit API, SheetsMapper
  repository/    — TaskRepositoryImpl (single source of truth)
sync/            — SyncWorker (WorkManager), SyncManager
auth/            — GoogleAuthRepository, AuthPreferences (DataStore)
widget/          — Glance widgets (Upcoming, Folder, TaskList)
di/              — Hilt modules
```

## Widgets

Three home screen widgets are available — long-press the home screen → Widgets → Stler Tasks:

| Widget | Description |
|---|---|
| **Upcoming** | Next 7 days of tasks grouped by date |
| **Folder** | All pending tasks in a chosen folder |
| **Task List** | Configurable list with a custom title |

Tap any task row in a widget to open the app directly on that task.

## Related

- **PWA version:** [github.com/JuliaSivridi/Tasks](https://github.com/JuliaSivridi/Tasks) — React + TypeScript, same Google Sheets backend
- **Live PWA:** [stler-tasks.vercel.app](https://stler-tasks.vercel.app)
