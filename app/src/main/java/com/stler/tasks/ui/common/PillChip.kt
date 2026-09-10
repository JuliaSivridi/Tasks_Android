package com.stler.tasks.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stler.tasks.ui.theme.ControlShape

/**
 * The one chip component for the whole app: filter toggles, date/time fields,
 * folder/label/priority pickers. Replaces one-off Material3 FilterChip uses
 * to get a consistent ControlShape (10dp rounded rectangle), controllable
 * padding, and predictable content colors.
 *
 * Selection color:
 *   - [activeColor] provided → bg/border/content use that color when selected
 *   - [activeColor] null     → falls back to primary
 *
 * Content color override (unselected only):
 *   - [contentColor] provided → icons/text use that color regardless of selected state
 *     (used for deadline-status coloring on date/time chips that stay outlined)
 *   - When selected=true, [activeColor]/primary always wins
 */
@Composable
fun PillChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String = "",
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    activeColor: Color? = null,
    contentColor: Color? = null,
) {
    val base = activeColor ?: MaterialTheme.colorScheme.primary
    val resolvedContentColor = when {
        selected     -> base
        contentColor != null -> contentColor
        else         -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick      = onClick,
        modifier     = modifier,
        shape        = ControlShape,
        color        = if (selected) base.copy(alpha = 0.15f) else Color.Transparent,
        contentColor = resolvedContentColor,
        border       = BorderStroke(1.dp, if (selected) base else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier             = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leadingContent?.invoke()
            if (label.isNotEmpty()) {
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            trailingContent?.invoke()
        }
    }
}
