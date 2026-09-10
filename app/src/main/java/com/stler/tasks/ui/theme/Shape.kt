package com.stler.tasks.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * All Android chrome uses 10dp rounded rectangles, not fully-rounded pills.
 * Material3's Button/SegmentedButton/FilterChip hardcode "corner full" shape
 * tokens that MaterialTheme.shapes cannot override, so ControlShape is applied
 * explicitly at each call site.
 */
val ControlShape = RoundedCornerShape(10.dp)
