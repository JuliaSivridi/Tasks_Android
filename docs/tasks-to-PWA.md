# Stler Tasks Android — Differences vs PWA

_Last updated: 2026-04-18_

This document lists every known difference between the Android app and the original Progressive Web App (PWA), categorised as improvements, parity items, or known gaps.

---

## ✅ Improvements over the PWA

### UI / UX

| Feature | PWA | Android |
|---|---|---|
| **Native performance** | Browser rendering, occasional jank | Native Compose rendering, smooth 60 fps |
| **Home-screen widgets** | Not available | 3 widget types: Upcoming, Folder, Task List |
| **Offline-first** | Requires network for most actions | All reads/writes go to Room first; sync happens in background |
| **Drag-to-reorder** | Supported | Supported (FolderScreen, `sh.calvin.reorderable`) |
| **Recurring task deadline dialog** | Separate screen / dialog | Inline toggle + interval row in DeadlinePickerDialog |
| **Postpone button** | Dialog-level shortcut | ✅ Same — advances by task's own recurrence interval (not always +1 day) |
| **Smart title parsing** | `@Folder` and `#Label` | `@Folder`, `#Label`, plus `!1` / `!2` / `!3` for priority shortcuts |
| **Week strip in Upcoming** | Calendar week navigation | ✅ Same, with animated scroll sync |
| **Overdue section** | Single "Overdue" header for all past tasks | ✅ Same — all past dates collapsed under one red "Overdue" header |
| **Label screen** | Shows label chip on each row | Hides label chip (redundant on label view) — cleaner |
| **Folder screen** | Shows folder chip on each row | Hides folder chip (redundant on folder view) — cleaner |
| **Material 3 theming** | CSS custom properties | Full Material You dynamic colour |
| **System back gesture** | Browser back button behaviour | Android back gesture / button works correctly |
| **Subtask progress indicator** | ✓ / total count display | ✓ completed + ○ pending + ≡ total displayed in row 2 |

### Sync

| Feature | PWA | Android |
|---|---|---|
| **Sync trigger** | On page load + manual button | Manual button + automatic 30-min WorkManager periodic job |
| **Sync queue** | None — writes fail silently offline | Local `SyncQueue` — writes survive offline, drained on reconnect |
| **Widget sync** | N/A | `WidgetRefresher` called after every write so widgets update instantly |
| **Drive search** | User pastes spreadsheet ID | Auto-discovered via Google Drive API on first sync |

---

## 🔄 Feature Parity (same behaviour as PWA)

- Task CRUD with all fields (title, folder, labels, priority, deadline date/time, recurrence)
- Subtask tree (unlimited nesting, expand/collapse per task)
- Completed tasks screen with restore and delete
- All Tasks view (flat list across all folders)
- Priority view (Urgent / Important / Normal)
- Label and Folder management (create, edit, delete from sidebar)
- Sync to/from Google Sheets (same spreadsheet schema)
- Recurring task completion → deadline advances by `recurValue × recurType`
- Overdue tasks in Upcoming: all dates before today shown under single header
- Upcoming: timed tasks before untimed tasks within each day group
- Deadline colour coding: overdue=red, today=primary, tomorrow=tertiary, future=muted

---

## ⚠️ Known Gaps / Not Yet Implemented

| Feature | PWA | Android status |
|---|---|---|
| **Notifications / reminders** | Not in PWA either | Not implemented |
| **Share sheet integration** | Not in PWA | Not implemented |
| **Dark / light mode toggle** | Manual CSS toggle | Follows system setting (Material You) — no manual override |
| **Completed tasks filters** | Date range filters | Only basic list, no date range filter yet |
| **Search** | Full-text search across tasks | Not implemented |
| **Sidebar label ordering** | Custom drag order | Labels shown in creation order (no custom sort) |
| **Multi-select** | Not in PWA | Not implemented |
| **Task attachments** | Not in PWA | Not implemented |
| **Spreadsheet column order** | Fixed schema | Same fixed schema — any manual changes to the sheet break sync |

---

## 📝 Behavioural Differences (not bugs, just different)

| Aspect | PWA | Android |
|---|---|---|
| **Deadline label in Upcoming rows** | Shows date + time | Shows time only (date is in the section header — less repetition) |
| **Deadline label format** | e.g. "18 Apr 14:30" | "Today 14:30", "Tomorrow", weekday name, "d MMM" — more human-friendly |
| **Folder widget** | Not available | Configurable folder widget with expand/collapse subtree support |
| **Inbox folder** | ID `fld-inbox`, name "Inbox" | Same ID, same name — fully compatible |
| **Auth flow** | Google OAuth in browser | Native Credential Manager (no browser redirect) |
| **Sync status** | Spinner in header | Icon in TopAppBar: idle / pending count / animating spinner |
