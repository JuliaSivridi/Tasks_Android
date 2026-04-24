package com.stler.tasks.ui.task

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.stler.tasks.domain.model.Priority

@Composable
fun TaskCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    priority: Priority,
    modifier: Modifier = Modifier,
) {
    val color = priorityColor(priority)

    Box(
        modifier = modifier
            .size(40.dp)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val cornerRadius = CornerRadius(3.dp.toPx())

            if (checked) {
                // Filled rounded rectangle
                drawRoundRect(
                    color = color,
                    cornerRadius = cornerRadius,
                )
                // White checkmark: moveTo(0.2,0.5) lineTo(0.42,0.72) lineTo(0.8,0.3)
                val path = Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.5f)
                    lineTo(size.width * 0.42f, size.height * 0.72f)
                    lineTo(size.width * 0.8f, size.height * 0.3f)
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(
                        width = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            } else {
                // Stroke-only rounded rectangle (no fill)
                drawRoundRect(
                    color = color,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 1.8.dp.toPx()),
                )
            }
        }
    }
}
