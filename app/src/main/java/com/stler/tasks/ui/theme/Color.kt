package com.stler.tasks.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand / Primary ──────────────────────────────────────────────────────────
val Primary = Color(0xFFE07E38)          // #e07e38 – PWA orange
val PrimaryDark = Color(0xFFD98D52)      // hsl(25 65% 63%) – lighter orange for dark mode
val OnPrimary = Color(0xFFFFFFFF)

// ── Backgrounds ──────────────────────────────────────────────────────────────
val Background = Color(0xFFFFFFFF)
val BackgroundDark = Color(0xFF1C1C1C)   // hsl(0 0% 11%)

val Surface = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF363636)      // hsl(0 0% 21%) – card

val Popover = Color(0xFFFFFFFF)
val PopoverDark = Color(0xFF242424)      // hsl(0 0% 14%)

// ── Text / Foreground ─────────────────────────────────────────────────────────
val Foreground = Color(0xFF18181F)       // hsl(240 10% 10%)
val ForegroundDark = Color(0xFFF2F2F2)  // hsl(0 0% 95%)

val MutedForeground = Color(0xFF6B6B6B) // hsl(0 0% 42%)
val MutedForegroundDark = Color(0xFF949494) // hsl(0 0% 58%)

// ── Accent ────────────────────────────────────────────────────────────────────
val Accent = Color(0xFFFDF6ED)           // hsl(38 60% 96%) – very light orange
val AccentForeground = Color(0xFF8F4F1C) // hsl(25 60% 35%)
val AccentDark = Color(0xFF2E2E2E)       // hsl(0 0% 18%)

// ── Secondary / Muted ─────────────────────────────────────────────────────────
val Secondary = Color(0xFFF5F3F1)        // hsl(25 8% 95%)
val SecondaryDark = Color(0xFF363636)

// ── Borders ───────────────────────────────────────────────────────────────────
val Border = Color(0xFFE0E0E0)           // hsl(0 0% 88%)
val BorderDark = Color(0xFF4A4A4A)       // hsl(0 0% 29%)
val Input = Color(0xFFE0E0E0)
val InputDark = Color(0xFF383838)        // hsl(0 0% 22%)

// ── Destructive ───────────────────────────────────────────────────────────────
val Destructive = Color(0xFFE96060)      // hsl(0 70% 67%)
val DestructiveDark = Color(0xFFCC5252)  // hsl(0 55% 58%)
val OnDestructive = Color(0xFFFFFFFF)

// ── Priority Colors ───────────────────────────────────────────────────────────
val PriorityUrgent = Color(0xFFF87171)   // red-400
val PriorityImportant = Color(0xFFFB923C) // orange-400
val PriorityNormal = Color(0xFF9CA3AF)   // gray-400

// ── Deadline Status Colors ────────────────────────────────────────────────────
val DeadlineOverdue = Color(0xFFF87171)  // red-400
val DeadlineToday = Color(0xFF16A34A)    // green-600
val DeadlineTomorrow = Color(0xFFFB923C) // orange-400
val DeadlineThisWeek = Color(0xFFA78BFA) // violet-400

// ── Label / Folder Color Presets ──────────────────────────────────────────────
val ColorPresets = listOf(
    Color(0xFFEF4444), // red-500
    Color(0xFFF97316), // orange-500  ← also Inbox default
    Color(0xFFEAB308), // yellow-500
    Color(0xFF22C55E), // green-500
    Color(0xFF06B6D4), // cyan-500
    Color(0xFF3B82F6), // blue-500
    Color(0xFF8B5CF6), // purple-500
    Color(0xFF6B7280), // gray-500
)

val InboxColor = Color(0xFFF97316)       // orange-500
