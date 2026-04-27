package com.stler.tasks.widget

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
 *                         deadline · #label1 · folder
 *                         ✓N ○N ≡N   (subtask counters, FolderWidget only)
 *
 * Checkbox:
 *   - Unchecked: [ic_check_box_outline_blank] tinted with priority color — a clean outline square.
 *   - Pending-complete: priority-colored filled box with white checkmark — immediate visual feedback
 *     between the tap and the next widget refresh (driven by [pendingCompleteId]).
 *
 * Tapping the checkbox area marks the task complete.
 * Tapping the text column opens the task for editing.
 *
 * @param labelItems        List of (labelName, hexColor) pairs for the meta row
 * @param folderHexColor    Hex color string for the folder name; blank = default color
 * @param pendingChildCount Number of pending (non-completed) child tasks
 * @param completedChildCount Number of completed child tasks
 * @param totalChildCount   Total child count (pending + completed); shown when > 0
 * @param pendingCompleteId If equal to [task].id the checkbox renders as checked (visual-only,
 *                          before Room confirms). Expires after a short timestamp window.
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

    // Row 2 (meta): deadline, recurring indicator, labels, folder
    val hasMetaRow = dlLabel != null || labelItems.isNotEmpty()
        || folderName.isNotBlank() || task.isRecurring
    // Row 3 (counters): child task stats — always on its own row so it always fits and aligns
    val hasCounterRow = totalChildCount > 0

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

            // ── Checkbox — 32dp touch target, 20dp visual ─────────────────────
            // Unchecked: ic_check_box_outline_blank tinted with priority color.
            // Pending-complete: priority-colored filled box with white checkmark icon.
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
                if (showCheckmark) {
                    // Filled: priority-colored box with white checkmark
                    Box(
                        modifier = GlanceModifier
                            .size(20.dp)
                            .cornerRadius(3.dp)
                            .background(priorityColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider           = ImageProvider(R.drawable.ic_check_mark),
                            contentDescription = "Completing…",
                            modifier           = GlanceModifier.size(14.dp),
                            colorFilter        = ColorFilter.tint(WCheckmark),
                        )
                    }
                } else {
                    // Outline: clean square icon tinted with priority color
                    Image(
                        provider           = ImageProvider(R.drawable.ic_check_box_outline_blank),
                        contentDescription = "Mark complete",
                        modifier           = GlanceModifier.size(20.dp),
                        colorFilter        = ColorFilter.tint(priorityColor),
                    )
                }
            }

            Spacer(GlanceModifier.width(8.dp))

            // ── Title + meta row + counter row ────────────────────────────────
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

                // Row 2: ↻ deadline · #label(s) · folder
                if (hasMetaRow) {
                    Row(
                        modifier          = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        var hasContent = false

                        // Recurring indicator — shares a logical slot with deadline
                        if (task.isRecurring) {
                            Text(
                                text  = "↻",
                                style = TextStyle(color = WOnSurfaceVariant, fontSize = 14.sp),
                            )
                            // hasContent stays false: deadline follows without " · "
                            if (dlLabel == null) hasContent = true
                        }

                        // Deadline — space after ↻ (if recurring), separator otherwise
                        if (dlLabel != null) {
                            val sep = when {
                                task.isRecurring -> " "     // "↻ HH:MM"
                                hasContent       -> " · "
                                else             -> ""
                            }
                            Text(
                                text  = "$sep$dlLabel",
                                style = TextStyle(color = dlColor, fontSize = 14.sp),
                            )
                            hasContent = true
                        }

                        // Labels
                        labelItems.forEachIndexed { i, (name, hexColor) ->
                            val prefix = when {
                                i == 0 && hasContent -> " · #"
                                i == 0               -> "#"
                                else                 -> ", #"
                            }
                            val lColor = hexToColorProvider(hexColor) ?: WOnSurfaceVariant
                            Text(
                                text  = "$prefix$name",
                                style = TextStyle(color = lColor, fontSize = 14.sp),
                            )
                            hasContent = true
                        }

                        // Folder
                        if (folderName.isNotBlank()) {
                            val sep    = if (hasContent) " · " else ""
                            val fColor = hexToColorProvider(folderHexColor) ?: WOnSurfaceVariant
                            Text(
                                text  = "$sep$folderName",
                                style = TextStyle(color = fColor, fontSize = 14.sp),
                            )
                        }
                    }
                }

                // Row 3: child task counters — separate row so icons always align and always fit
                // Shows: ✓completed  ○pending  ≡total
                if (hasCounterRow) {
                    Row(
                        modifier          = GlanceModifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            provider           = ImageProvider(R.drawable.ic_check_mark),
                            contentDescription = null,
                            modifier           = GlanceModifier.size(11.dp),
                            colorFilter        = ColorFilter.tint(WOnSurfaceVariant),
                        )
                        Spacer(GlanceModifier.width(2.dp))
                        Text(
                            text  = "$completedChildCount",
                            style = TextStyle(color = WOnSurfaceVariant, fontSize = 12.sp),
                        )
                        Spacer(GlanceModifier.width(5.dp))
                        Image(
                            provider           = ImageProvider(R.drawable.ic_radio_button_unchecked),
                            contentDescription = null,
                            modifier           = GlanceModifier.size(11.dp),
                            colorFilter        = ColorFilter.tint(WOnSurfaceVariant),
                        )
                        Spacer(GlanceModifier.width(2.dp))
                        Text(
                            text  = "$pendingChildCount",
                            style = TextStyle(color = WOnSurfaceVariant, fontSize = 12.sp),
                        )
                        Spacer(GlanceModifier.width(5.dp))
                        Image(
                            provider           = ImageProvider(R.drawable.ic_format_list_bulleted),
                            contentDescription = null,
                            modifier           = GlanceModifier.size(11.dp),
                            colorFilter        = ColorFilter.tint(WOnSurfaceVariant),
                        )
                        Spacer(GlanceModifier.width(2.dp))
                        Text(
                            text  = "$totalChildCount",
                            style = TextStyle(color = WOnSurfaceVariant, fontSize = 12.sp),
                        )
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
