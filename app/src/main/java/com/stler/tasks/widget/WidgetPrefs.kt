package com.stler.tasks.widget

import android.content.Context

/**
 * Per-widget config stored in SharedPreferences, keyed by the raw appWidgetId integer.
 * This integer is stable: it's the same in the config activity (from intent extras) and
 * in provideGlance (via GlanceAppWidgetManager.getAppWidgetId(id)).
 */
object WidgetPrefs {
    private const val FILE = "widget_config"

    fun getFolderId(context: Context, appWidgetId: Int): String =
        prefs(context).getString("folder_$appWidgetId", "fld-inbox") ?: "fld-inbox"

    fun setFolderId(context: Context, appWidgetId: Int, value: String) =
        prefs(context).edit().putString("folder_$appWidgetId", value).commit()

    // ── TaskList widget filters — multi-select (comma-separated sets) ──────────
    // New keys: filterFolders_N / filterLabels_N / filterPriorities_N
    // Migration: if new key absent, fall back to old single-value filterFolder_N etc.
    // Old single-value keys are kept in place so a downgrade still works.

    fun getFilterFolders(context: Context, appWidgetId: Int): Set<String> {
        val p = prefs(context)
        val newVal = p.getString("filterFolders_$appWidgetId", null)
        if (newVal != null) return newVal.split(",").filter { it.isNotBlank() }.toSet()
        // Migration from old single-value pref
        val oldVal = p.getString("filterFolder_$appWidgetId", null)
        return if (oldVal != null) setOf(oldVal) else emptySet()
    }

    fun setFilterFolders(context: Context, appWidgetId: Int, ids: Set<String>) =
        prefs(context).edit()
            .putString("filterFolders_$appWidgetId", ids.joinToString(","))
            .apply()

    fun getFilterLabels(context: Context, appWidgetId: Int): Set<String> {
        val p = prefs(context)
        val newVal = p.getString("filterLabels_$appWidgetId", null)
        if (newVal != null) return newVal.split(",").filter { it.isNotBlank() }.toSet()
        val oldVal = p.getString("filterLabel_$appWidgetId", null)
        return if (oldVal != null) setOf(oldVal) else emptySet()
    }

    fun setFilterLabels(context: Context, appWidgetId: Int, ids: Set<String>) =
        prefs(context).edit()
            .putString("filterLabels_$appWidgetId", ids.joinToString(","))
            .apply()

    fun getFilterPriorities(context: Context, appWidgetId: Int): Set<String> {
        val p = prefs(context)
        val newVal = p.getString("filterPriorities_$appWidgetId", null)
        if (newVal != null) return newVal.split(",").filter { it.isNotBlank() }.toSet()
        val oldVal = p.getString("filterPriority_$appWidgetId", null)
        return if (oldVal != null) setOf(oldVal) else emptySet()
    }

    fun setFilterPriorities(context: Context, appWidgetId: Int, ids: Set<String>) =
        prefs(context).edit()
            .putString("filterPriorities_$appWidgetId", ids.joinToString(","))
            .apply()

    // ── Legacy single-value filter accessors (kept for downgrade compat) ──────

    fun getFilterFolder(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("filterFolder_$appWidgetId", null)

    fun getFilterLabel(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("filterLabel_$appWidgetId", null)

    fun getFilterPriority(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("filterPriority_$appWidgetId", null)

    // ── Widget "configured" flag ──────────────────────────────────────────────
    // Set to true by WidgetConfigActivity after the user confirms.
    // The receiver's onUpdate skips unconfigured widget IDs so the initial
    // APPWIDGET_UPDATE broadcast (which fires before the config activity saves prefs)
    // doesn't render stale / default content.

    fun isConfigured(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean("configured_$appWidgetId", false)

    fun setConfigured(context: Context, appWidgetId: Int) =
        prefs(context).edit().putBoolean("configured_$appWidgetId", true).apply()

    /** Migration helper: FolderWidget instances that existed before the "configured" flag
     *  was introduced will have their folder_N pref set — treat those as already configured. */
    fun isFolderWidgetConfigured(context: Context, appWidgetId: Int): Boolean =
        isConfigured(context, appWidgetId) ||
        prefs(context).contains("folder_$appWidgetId")

    // ── Calendar widget prefs ─────────────────────────────────────────────────
    // calendarIds: comma-separated Google Calendar IDs configured for this widget.
    // Empty string or absent → fall back to globally selected calendars.
    // calendarName: display name shown in the widget header (e.g. "Work").

    fun getCalendarWidgetIds(context: Context, appWidgetId: Int): Set<String> {
        val str = prefs(context).getString("calendarIds_$appWidgetId", null)
            ?: return emptySet()
        return str.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun setCalendarWidgetIds(context: Context, appWidgetId: Int, ids: Set<String>) =
        prefs(context).edit()
            .putString("calendarIds_$appWidgetId", ids.joinToString(","))
            .commit()

    fun getCalendarWidgetName(context: Context, appWidgetId: Int): String =
        prefs(context).getString("calendarName_$appWidgetId", "") ?: ""

    fun setCalendarWidgetName(context: Context, appWidgetId: Int, name: String) =
        prefs(context).edit().putString("calendarName_$appWidgetId", name).commit()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
