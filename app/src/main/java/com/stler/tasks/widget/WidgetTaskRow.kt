package com.stler.tasks.widget

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.height
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.Task
import com.stler.tasks.ui.task.DeadlineStatus
import com.stler.tasks.ui.task.deadlineLabel
import com.stler.tasks.ui.task.deadlineStatus

/**
 * Widget task row — mirrors the app's TaskItem layout (without action buttons):
 *
 *   [chevron?] [checkbox] Title
 *                         deadline · #label1 · folder
 *
 * Checkbox: hollow square in priority color (outer box = priority color,
 *           inner box = surface color, giving a border effect).
 * Tapping checkbox marks the task complete.
 * Tapping the text column opens the task for editing.
 *
 * @param labelItems List of (labelName, hexColor) pairs for row 2
 * @param folderHexColor Hex color string for the folder name in row 2; blank = default color
 * @param pendingChildCount Number of pending (non-completed) child tasks; shown in row 2 when > 0
 */
@Composable
fun WidgetTaskRow(
    task: Task,
    indentLevel: Int = 0,
    hasChildren: Boolean = false,
    labelItems: List<Pair<String, String>> = emptyList(), // (name, "#rrggbb")
    folderName: String = "",
    folderHexColor: String = "",
    /** When false the 24dp chevron spacer is omitted (use in Upcoming / TaskList where
     *  expand is never shown). Keeps in FolderWidget where it aligns subtasks. */
    showExpandSpace: Boolean = true,
    /** When true only the time portion of the deadline is shown in row 2 (date is
     *  already visible in the section header, e.g. UpcomingWidget). */
    timeOnly: Boolean = false,
    /** Pending child task count (FolderWidget only). Shown in row 2 when > 0. */
    pendingChildCount: Int = 0,
) {
    val priorityColor = when (task.priority) {
        Priority.URGENT    -> WPriorityUrgent
        Priority.IMPORTANT -> WPriorityImportant
        Priority.NORMAL    -> WPriorityNormal
    }

    val dlStatus = deadlineStatus(task.deadlineDate)
    val dlLabel  = deadlineLabel(task.deadlineDate, task.deadlineTime, includeDate = !timeOnly)
    val dlColor  = when (dlStatus) {
        DeadlineStatus.OVERDUE   -> WDeadlineOverdue
        DeadlineStatus.TODAY     -> WDeadlineToday
        DeadlineStatus.TOMORROW  -> WDeadlineTomorrow
        DeadlineStatus.THIS_WEEK -> WDeadlineThisWeek
        else                     -> WOnSurfaceVariant
    }

    val hasRow2 = dlLabel != null || labelItems.isNotEmpty() || folderName.isNotBlank()
        || task.isRecurring || pendingChildCount > 0

    // Wrap in Column so we can add a thin bottom divider (matches app's HorizontalDivider).
    // Semi-transparent gray works on both light and dark widget backgrounds.
    Column(modifier = GlanceModifier.fillMaxWidth()) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(
                start  = (8 + indentLevel * 16).dp,
                end    = 8.dp,
                top    = 4.dp,
                bottom = 4.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        // ── Chevron — 28dp touch target, 11sp visual glyph ──────────────
        // Widgets are compact UI; 28dp is a reasonable touch target here.
        if (hasChildren) {
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .clickable(
                        actionRunCallback<ToggleExpandAction>(
                            actionParametersOf(
                                taskIdKey to task.id,
                                expandKey to !task.isExpanded,
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = if (task.isExpanded) "▼" else "▶",
                    style = TextStyle(
                        color    = WOnSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
            }
        } else if (showExpandSpace) {
            Spacer(GlanceModifier.width(28.dp))
        }

        Spacer(GlanceModifier.width(6.dp))

        // ── Checkbox — 32dp touch target, 20dp visual ─────────────────────
        // Outer box: 32dp touch target with click handler.
        // Inner box: visual checkbox (priority border + surface fill).
        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .clickable(
                    actionRunCallback<CompleteTaskAction>(
                        actionParametersOf(taskIdKey to task.id)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(20.dp)
                    .cornerRadius(3.dp)
                    .background(priorityColor),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(17.dp)
                        .cornerRadius(2.dp)
                        .background(WSurface),
                ) {}
            }
        }

        Spacer(GlanceModifier.width(8.dp))

        // ── Title + row 2 ──────────────────────────────────────────────────
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .wrapContentHeight()
                .clickable(
                    actionStartActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("stlertasks://task/${task.id}"))
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP,
                            )
                    )
                ),
        ) {
            // Row 1: title
            Text(
                text     = task.title,
                maxLines = 2,
                style    = TextStyle(
                    color      = WOnSurface,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )

            // Row 2: ↻ · deadline · #label(s) · folder · child count
            if (hasRow2) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    var hasContent = false

                    // Recurring indicator
                    if (task.isRecurring) {
                        Text(
                            text  = "↻",
                            style = TextStyle(color = WOnSurfaceVariant, fontSize = 14.sp),
                        )
                        hasContent = true
                    }

                    // Deadline (already has its status color)
                    if (dlLabel != null) {
                        val sep = if (hasContent) " · " else ""
                        Text(
                            text  = "$sep$dlLabel",
                            style = TextStyle(color = dlColor, fontSize = 14.sp),
                        )
                        hasContent = true
                    }

                    // Labels — each with its own color
                    labelItems.forEachIndexed { i, (name, hexColor) ->
                        val prefix = when {
                            i == 0 && hasContent -> " · #"
                            i == 0               -> "#"
                            else                 -> ", #"
                        }
                        val lColor = hexToColorProvider(hexColor)
                            ?: WOnSurfaceVariant
                        Text(
                            text  = "$prefix$name",
                            style = TextStyle(color = lColor, fontSize = 14.sp),
                        )
                        hasContent = true
                    }

                    // Folder with its own color
                    if (folderName.isNotBlank()) {
                        val sep = if (hasContent) " · " else ""
                        val fColor = hexToColorProvider(folderHexColor)
                            ?: WOnSurfaceVariant
                        Text(
                            text  = "$sep$folderName",
                            style = TextStyle(color = fColor, fontSize = 14.sp),
                        )
                        hasContent = true
                    }

                    // Pending child count (FolderWidget parent tasks)
                    if (pendingChildCount > 0) {
                        val sep = if (hasContent) "  " else ""
                        Text(
                            text  = "${sep}○ $pendingChildCount",
                            style = TextStyle(color = WOnSurfaceVariant, fontSize = 14.sp),
                        )
                    }
                }
            }
        }
    }
    // Thin divider line below each row — 1dp so it renders as at least 1 physical pixel
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(WDivider),
    ) {}
    } // end Column
}

/** Parses a hex color string (e.g. "#3b82f6") into a Glance [ColorProvider], or null on failure. */
private fun hexToColorProvider(hex: String): ColorProvider? {
    if (hex.isBlank()) return null
    return try {
        ColorProvider(Color(android.graphics.Color.parseColor(hex)))
    } catch (_: Exception) {
        null
    }
}
