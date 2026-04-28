package com.stler.tasks.widget

import androidx.glance.unit.ColorProvider
import com.stler.tasks.R

/**
 * Explicit widget colour palette that mirrors the app's TasksTheme.
 *
 * Glance's ColorProvider(Color) only accepts a single colour; day/night variants
 * must be declared as XML color resources with a "values-night" qualifier.
 * See res/values/colors.xml and res/values-night/colors.xml.
 *
 * Using these explicit providers keeps widgets consistent with the app regardless
 * of the system's Material You dynamic accent colour.
 */
internal val WPrimary          = ColorProvider(R.color.widget_primary)
internal val WSurface          = ColorProvider(R.color.widget_surface)
internal val WOnSurface        = ColorProvider(R.color.widget_on_surface)
internal val WOnSurfaceVariant = ColorProvider(R.color.widget_on_surface_variant)
internal val WDivider          = ColorProvider(R.color.widget_divider)
internal val WError            = ColorProvider(R.color.widget_error)

// Priority colors (same in light and dark — no night qualifier needed)
internal val WPriorityUrgent    = ColorProvider(R.color.widget_priority_urgent)
internal val WPriorityImportant = ColorProvider(R.color.widget_priority_important)
internal val WPriorityNormal    = ColorProvider(R.color.widget_priority_normal)

// Deadline status colors (same in light and dark)
internal val WDeadlineOverdue   = ColorProvider(R.color.widget_deadline_overdue)
internal val WDeadlineToday     = ColorProvider(R.color.widget_deadline_today)
internal val WDeadlineTomorrow  = ColorProvider(R.color.widget_deadline_tomorrow)
internal val WDeadlineThisWeek  = ColorProvider(R.color.widget_deadline_this_week)

/**
 * Checkmark fill for the completed/pending-complete checkbox.
 * Uses the widget surface color so it always contrasts the [WOnSurface] box background:
 *  - light theme: white (#FFFFFF) checkmark on near-black (#18181F) box → visible
 *  - dark theme: dark (#1C1C1C) checkmark on near-white (#F2F2F2) box → visible
 */
internal val WCheckmark = ColorProvider(R.color.widget_surface)

/**
 * Parses a hex color string (e.g. "#3b82f6") into a Glance [ColorProvider], or null on failure.
 * Shared by [WidgetTaskRow] and [WidgetEventRow].
 */
internal fun hexToColorProvider(hex: String): ColorProvider? {
    if (hex.isBlank()) return null
    return try {
        ColorProvider(Color(android.graphics.Color.parseColor(hex)))
    } catch (_: Exception) {
        null
    }
}
