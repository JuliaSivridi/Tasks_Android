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

    fun getFilterFolder(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("filterFolder_$appWidgetId", null)

    fun setFilterFolder(context: Context, appWidgetId: Int, value: String?) =
        prefs(context).edit().let {
            if (value != null) it.putString("filterFolder_$appWidgetId", value)
            else it.remove("filterFolder_$appWidgetId")
        }.commit()

    fun getFilterLabel(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("filterLabel_$appWidgetId", null)

    fun setFilterLabel(context: Context, appWidgetId: Int, value: String?) =
        prefs(context).edit().let {
            if (value != null) it.putString("filterLabel_$appWidgetId", value)
            else it.remove("filterLabel_$appWidgetId")
        }.commit()

    fun getFilterPriority(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("filterPriority_$appWidgetId", null)

    fun setFilterPriority(context: Context, appWidgetId: Int, value: String?) =
        prefs(context).edit().let {
            if (value != null) it.putString("filterPriority_$appWidgetId", value)
            else it.remove("filterPriority_$appWidgetId")
        }.commit()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
