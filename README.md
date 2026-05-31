# Stler Tasks — Android

[![Live PWA](https://img.shields.io/badge/Stler_Tasks-Live_PWA-E07E38?style=for-the-badge)](https://stler-tasks.vercel.app)
[![Releases](https://img.shields.io/github/v/release/JuliaSivridi/Tasks_Android?style=for-the-badge&logo=github&logoColor=white&color=181717)](https://github.com/JuliaSivridi/Tasks_Android/releases)

![Kotlin](https://img.shields.io/badge/Kotlin_2.2-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android_8.0+-34A853?style=for-the-badge&logo=android&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Room](https://img.shields.io/badge/Room_SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white)
![Google Sheets](https://img.shields.io/badge/Google_Sheets_API-34A853?style=for-the-badge&logo=googlesheets&logoColor=white)
![Google Calendar](https://img.shields.io/badge/Google_Calendar_API-4285F4?style=for-the-badge&logo=googlecalendar&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)

A native Android task manager — the companion app to [Stler Tasks PWA](https://stler-tasks.vercel.app). Both apps share the same `db_tasks` Google Spreadsheet, so tasks, folders, and labels stay in sync between web and phone automatically.

---

## Features

**📋 Task management**
- Create tasks with title, priority, deadline (date + optional time), labels, and folder
- Subtasks at any depth with expand/collapse; progress counter on the parent row
- Recurring tasks — daily / weekly / monthly; completing a recurring task advances the deadline automatically, not marks it done
- Smart input: type `@FolderName`, `#LabelName`, or `!1`/`!2`/`!3` in the title to set folder / label / priority instantly

**🗂️ Organization**
- **Folders** — group tasks; drag to reorder within a folder; hierarchical subtask tree
- **Labels** — colored tags, multiple per task; filterable across all views
- **Priority** — Urgent / Important / Normal; color-coded flags on every task row

**🎛️ App flexibility**
- Each feature area (Folders, Labels, Priorities, Calendars) can be individually toggled on/off in Settings
- All task data is preserved regardless of toggle state — turning a feature back on restores full visibility instantly
- Folders and Labels are managed directly in Settings (create, rename, delete)

**👁️ Views**
- **Upcoming** — tasks + calendar events grouped by day with a scrollable week strip; overdue section at the top
- **All Tasks** — flat list of all pending tasks and calendar events, interleaved by date and priority
- **Folder** — dedicated task list per folder with drag-to-reorder
- **Calendar** — event list for each connected calendar
- **Completed** — archive of done tasks with one-tap restore

**⏰ Deadlines**
- Color-coded by urgency: overdue · today · tomorrow · this week · future
- Swipe left on any task to open the deadline picker instantly
- Postpone button inside the deadline picker (advances by the task's own recurrence interval)

**👆 Swipe gestures**
- Swipe right → complete (green flash, then task disappears or deadline advances)
- Swipe left → open deadline picker (snaps back after closing)

**📅 Google Calendar integration**
- Connect one or more Google Calendars in Settings
- Calendar events displayed inline with tasks in Upcoming and All Tasks, unified by date
- Create, edit, and delete events (including recurring) directly from the app
- Recurring events show a loop icon; delete "this event only" or "all in series"

**🪄 Widgets** — four home screen widgets via Jetpack Glance:
- **Upcoming** — next 7 days of tasks + calendar events grouped by date
- **Folder** — any single folder, hierarchical with expand/collapse
- **Task List** — configurable by folder / label / priority
- **Calendar** — mixed task + event timeline for the next 7 days

**🔄 Sync & offline**
- Full read/write offline via Room (SQLite); sync queue flushes when back online
- Background sync every 30 minutes via WorkManager
- Sync status shown in the top bar: cloud-done (synced) · cloud-upload with counter (pending) · spinning arrows (syncing)
- First sign-in automatically creates the `db_tasks` spreadsheet — no manual setup

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Kotlin | 2.2.10 |
| UI | Jetpack Compose + Material 3 | BOM 2026.02.01 |
| Widgets | Jetpack Glance | 1.1.0 |
| Architecture | MVVM + Repository | — |
| DI | Hilt | 2.59.2 |
| Local DB | Room | 2.7.1 |
| Async | Coroutines + Flow | 1.10.2 |
| Background sync | WorkManager | 2.10.1 |
| Auth | Credential Manager + Google Identity API | 21.3.0 / 1.1.1 |
| Network | Retrofit + OkHttp | 2.11.0 / 4.12.0 |
| Remote storage | Google Sheets API v4 | — |
| Drag & drop | sh.calvin.reorderable | 2.4.3 |
| Min SDK | 26 (Android 8.0) | — |

---

## Architecture

```
ui/
  main/        — Navigation drawer, top bar, main scaffold, FAB
  upcoming/    — Week strip + day-grouped task list
  alltasks/    — Flat task list with priority / label / folder / calendar filters
  folder/      — Hierarchical task list with drag-to-reorder
  calendar/    — Event list per calendar
  completed/   — Completed tasks with restore / delete
  settings/    — Feature toggles, spreadsheet picker, folder/label/calendar management
  task/        — TaskItem, TaskFormSheet, DeadlinePickerDialog, pickers
  theme/       — Color palette, Typography, Theme
data/
  local/       — Room DB (version 8), DAOs, entities
  remote/      — Retrofit + SheetsMapper (row ↔ entity) + CalendarApi
  repository/  — TaskRepositoryImpl + CalendarRepositoryImpl
sync/          — SyncWorker (WorkManager), SyncManager, SyncState
auth/          — GoogleAuthRepository, AuthPreferences (DataStore), FeatureFlags
widget/        — Glance widgets (Upcoming, Folder, TaskList, Calendar) + actions
di/            — Hilt modules
```

**Data flow:** every mutation writes to Room immediately (triggers UI recomposition) and enqueues a SyncQueue entry. SyncWorker drains the queue on the next sync cycle, then pulls fresh data from Sheets into Room.

---

## Data Model

All data lives in a Google Spreadsheet named `db_tasks` — one per Google account, shared with the PWA.

| Sheet | Columns (A → last) |
|---|---|
| `tasks` | id · parent_id · folder_id · title · status · priority · deadline_date · deadline_time · is_recurring · recur_type · recur_value · labels · sort_order · created_at · updated_at · completed_at · is_expanded |
| `folders` | id · name · color · sort_order |
| `labels` | id · name · color · sort_order |

Row 1 of every sheet is a header row. Deleted rows are cleared (all cells emptied) rather than physically removed.

---

## Setup

### Prerequisites

- Android Studio Hedgehog or newer
- Google account
- Google Cloud project with **Google Sheets API**, **Google Drive API**, and **Google Calendar API** enabled

### Google Cloud Console

1. Go to [console.cloud.google.com](https://console.cloud.google.com) and enable **Google Sheets API**, **Google Drive API**, and **Google Calendar API**
2. Create an OAuth 2.0 Client ID → type **Android**
   - Package name: `com.stler.tasks`
   - SHA-1: run `./gradlew signingReport`
3. Create an OAuth 2.0 Client ID → type **Web application** (needed for the token exchange)
4. Set the OAuth consent screen to **Production** so any Google account can sign in

### Local development

```bash
git clone https://github.com/JuliaSivridi/Tasks_Android.git
cd Tasks_Android
```

Add your Web Client ID to `app/src/main/res/values/strings.xml`:

```xml
<string name="google_web_client_id">YOUR_WEB_CLIENT_ID.apps.googleusercontent.com</string>
```

Open in Android Studio and run on a device or emulator (API 26+).

On first sign-in the app automatically finds or creates the `db_tasks` spreadsheet — no manual spreadsheet setup required.

### Release builds

The GitHub Actions workflow (`.github/workflows/release.yml`) builds a signed APK on every `v*` tag push and attaches it to a GitHub Release.

Required secrets: `KEYSTORE_BASE64` · `KEYSTORE_PASSWORD` · `KEY_ALIAS` · `KEY_PASSWORD`

---

## Related

- **PWA version:** [github.com/JuliaSivridi/Tasks](https://github.com/JuliaSivridi/Tasks) — React + TypeScript, same Google Sheets backend
- **Live PWA:** [stler-tasks.vercel.app](https://stler-tasks.vercel.app)
- **Technical specification:** [`docs/tech-spec.md`](docs/tech-spec.md)
