package com.stler.tasks.util

import androidx.compose.ui.graphics.Color

/** Parses a hex color string (e.g. "#f97316") into a Compose [Color]. */
fun String.toComposeColor(): Color = try {
    Color(android.graphics.Color.parseColor(this))
} catch (_: Exception) {
    Color.Gray
}
