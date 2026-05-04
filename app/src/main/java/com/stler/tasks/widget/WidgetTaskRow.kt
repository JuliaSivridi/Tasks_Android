package com.stler.tasks.widget

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import com.stler.tasks.R
import com.stler.tasks.domain.model.Priority
import com.stler.tasks.domain.model.Task
import com.stler.tasks.ui.task.DeadlineStatus
import com.stler.tasks.ui.task.deadlineLabel
import com.stler.tasks.ui.task.deadlineStatus

/**
 * Widget task row — mirrors the app's TaskItem layout (without action buttons):
 *
 *   [chevron?] [checkbox] Title
 *                         deadline  #label1  folder  ✓N ○N ≡N
 *
 * Checkbox — neutral (no priority color), theme-aware:
 *   Unchecked: ic_check_box_outline_blank tinted with WOnSurface (near-black in light,
 *              near-white in dark) — matches the app's stroke-only rounded rectangle.
 *   Checked/pending: WOnSurface box with WCheckmark (= widget_surface = white in light,
 *              dark in dark) checkmark — always high-contrast in both themes.
 *
 * Tapping the checkbox area marks the task complete.
 * Tapping the text column opens the task for editing.
 *
 * @param labelItems        List of (labelName, hexColor) pairs for the meta row
 * @param pendingChildCount Remaining (non-completed) child tasks; shown in meta row when > 0
 * @param totalChildCount   total child task count; shown when > 0
 */
@Composable
fun WidgetTaskRow(
    task: Task,
    indentLevel: Int = 0,
    hasChildren: Boolean = false,
    labelItems: List<Pair<String, String>> = emptyList(), // (name, "#rrggbb")
    folderName: String = "",
    folderHexColor: String = "",
    /** When false the 24dp chevron spacer is omitted (Upcoming / TaskList). */
    showExpandSpace: Boolean = true,
    /** When true only the time portion of the deadline is shown (date already in section header). */
    timeOnly: Boolean = false,
    pendingChildCount  : Int = 0,
    completedChildCount: Int = 0,
    totalChildCount    : Int = 0,
    pendingCompleteId  : String? = null,
) {
    val showCheckmark = (task.id == pendingCompleteId)
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
        || task.isRecurring || totalChildCount > 0

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
            // ── Chevron — 28dp touch target ───────────────────────────────────
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
                        style = TextStyle(color = WOnSurfaceVariant, fontSize = 11.sp),
                    )
                }
            } else if (showExpandSpace) {
                Spacer(GlanceModifier.width(28.dp))
            }

            Spacer(GlanceModifier.width(6.dp))

            // ── Checkbox — 36dp touch target ──────────────────────────────────
            // Sizes tuned to match the app's TaskCheckbox visual weight (18dp canvas):
            //   Unchecked: 26dp icon — the outline square occupies ~75% of the icon
            //              viewport (18/24 ratio), giving ≈19.5dp visible stroke square.
            //   Checked:   24dp filled box — appears similar in weight to the outline.
            // Both states use the task's priority color (urgent=red, important=orange, normal=gray).
            Box(
                modifier = GlanceModifier
                    .size(36.dp)
                    .clickable(
                        actionRunCallback<CompleteTaskAction>(
                            actionParametersOf(taskIdKey to task.id)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (showCheckmark) {
                    // Filled priority-colored box with white checkmark inside
                    Box(
                        modifier = GlanceModifier
                            .size(24.dp)
                            .cornerRadius(3.dp)
                            .background(priorityColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider           = ImageProvider(R.drawable.ic_check_mark),
                            contentDescription = "Completing…",
                            modifier           = GlanceModifier.size(16.dp),
                            colorFilter        = ColorFilter.tint(WCheckmark),
                        )
                    }
                } else {
                    // Outline square icon tinted with priority color
                    Image(
                        provider           = ImageProvider(R.drawable.ic_check_box_outline_blank),
                        contentDescription = "Mark complete",
                        modifier           = GlanceModifier.size(26.dp),
                        colorFilter        = ColorFilter.tint(priorityColor),
                    )
                }
            }

            Spacer(GlanceModifier.width(8.dp))

            // ── Title + meta row ──────────────────────────────────────────────
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

                // Row 2: ↻ deadline  #label(s)  folder  ✓N ○N ≡N
                // Items separated by double-space (matching app's style — no dot separators).
                // verticalAlignment = CenterVertically so the small counter icons (11dp) sit
                // at mid-text rather than at the top of the row.
                // Counter children: 6 total (Image+Text × 3, padding instead of Spacer)
                // to stay within Glance/RemoteViews child count limits in LazyColumn items.
                if (hasRow2) {
                    Row(
                        modifier          = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        var hasContent = false

                        // ↻ — tight grouping with deadline, no separator between them
                        if (task.isRecurring) {
                            Text(
                                text  = "↻",
                                style = TextStyle(color = WOnSurfaceVariant, fontSize = 14.sp),
                            )
                            if (dlLabel == null) hasContent = true
                        }

                        // Deadline
                        if (dlLabel != null) {
                            val sep = if (task.isRecurring) " " else if (hasContent) "  " else ""
                            Text(
                                text  = "$sep$dlLabel",
                                style = TextStyle(color = dlColor, fontSize = 14.sp),
                            )
                            hasContent = true
                        }

                        // Labels
                        labelItems.forEachIndexed { i, (name, hexColor) ->
                            val prefix = if (i == 0 && hasContent) "  #" else if (i == 0) "#" else "  #"
                            val lColor = hexToColorProvider(hexColor) ?: WOnSurfaceVariant
                            Text(
                                text  = "$prefix$name",
                                style = TextStyle(color = lColor, fontSize = 14.sp),
                            )
                            hasContent = true
                        }

                        // Folder
                        if (folderName.isNotBlank()) {
                            val sep    = if (hasContent) "  " else ""
                            val fColor = hexToColorProvider(folderHexColor) ?: WOnSurfaceVariant
                            Text(
                                text  = "$sep$folderName",
                                style = TextStyle(color = fColor, fontSize = 14.sp),
                            )
                            hasContent = true
                        }

                        // Child task counters: ✓completed  ○pending  ≡total
                        // 6 children (Image+Text × 3) with padding instead of Spacer children
                        // to keep the total row child count low and avoid Glance clipping.
                        if (totalChildCount > 0) {
                            Image(
                                provider           = ImageProvider(R.drawable.ic_check_mark),
                                contentDescription = null,
                                modifier           = GlanceModifier.size(11.dp)
                                    .padding(start = if (hasContent) 6.dp else 0.dp),
                                colorFilter        = ColorFilter.tint(WOnSurfaceVariant),
                            )
                            Text(
                                text     = "$completedChildCount",
                                modifier = GlanceModifier.padding(start = 2.dp, end = 5.dp),
                                style    = TextStyle(color = WOnSurfaceVariant, fontSize = 12.sp),
                            )
                            Image(
                                provider           = ImageProvider(R.drawable.ic_radio_button_unchecked),
                                contentDescription = null,
                                modifier           = GlanceModifier.size(11.dp),
                                colorFilter        = ColorFilter.tint(WOnSurfaceVariant),
                            )
                            Text(
                                text     = "$pendingChildCount",
                                modifier = GlanceModifier.padding(start = 2.dp, end = 5.dp),
                                style    = TextStyle(color = WOnSurfaceVariant, fontSize = 12.sp),
                            )
                            Image(
                                provider           = ImageProvider(R.drawable.ic_format_list_bulleted),
                                contentDescription = null,
                                modifier           = GlanceModifier.size(11.dp),
                                colorFilter        = ColorFilter.tint(WOnSurfaceVariant),
                            )
                            Text(
                                text     = "$totalChildCount",
                                modifier = GlanceModifier.padding(start = 2.dp),
                                style    = TextStyle(color = WOnSurfaceVariant, fontSize = 12.sp),
                            )
                        }
                    }
                }
            }
        }
        // Thin divider below each row
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(WDivider),
        ) {}
    }
}
