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
