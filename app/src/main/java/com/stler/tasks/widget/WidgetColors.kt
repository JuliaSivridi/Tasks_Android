package com.stler.tasks.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/**
 * Explicit widget colour palette that mirrors the app's TasksTheme.
 *
 * Glance uses the system's Material You dynamic colours by default, which can
 * produce a wrong orange or a reddish surface tint when the user's wallpaper
 * is warm-toned.  Using these explicit providers keeps widgets consistent with
 * the app regardless of system accent colour.
 */
internal val WPrimary            = ColorProvider(Color(0xFFE07E38), Color(0xFFD98D52))
internal val WSurface            = ColorProvider(Color(0xFFFFFFFF), Color(0xFF363636))
internal val WOnSurface          = ColorProvider(Color(0xFF18181F), Color(0xFFF2F2F2))
internal val WOnSurfaceVariant   = ColorProvider(Color(0xFF6B6B6B), Color(0xFF949494))
internal val WDivider            = ColorProvider(Color(0xFFE0E0E0), Color(0xFF4A4A4A))
