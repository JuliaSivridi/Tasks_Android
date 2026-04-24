package com.stler.tasks.ui.util

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Placeholder shimmer that mimics the task list layout while data is loading.
 *
 * Shows [itemCount] skeleton rows that pulse in opacity.
 * Each row mirrors TaskItem layout: checkbox placeholder + two lines of text.
 */
@Composable
fun ShimmerTaskList(
    itemCount: Int = 6,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue  = 0.6f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )

    Column(modifier = modifier) {
        repeat(itemCount) { index ->
            ShimmerTaskRow(alpha = alpha, index = index)
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

// Title width fractions vary by row index so skeleton lines look natural
private val titleFractions = listOf(0.75f, 0.55f, 0.80f, 0.65f, 0.70f, 0.60f)

@Composable
private fun ShimmerTaskRow(alpha: Float, index: Int) {
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val shape = RoundedCornerShape(4.dp)
    val titleFraction = titleFractions[index % titleFractions.size]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Expand placeholder
            Spacer(Modifier.width(40.dp))

            // Checkbox placeholder
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(shimmerColor),
            )

            Spacer(Modifier.width(8.dp))

            // Title placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(titleFraction)
                    .height(14.dp)
                    .clip(shape)
                    .background(shimmerColor),
            )
        }

        // Metadata placeholder row (deadline / label)
        Row(
            modifier = Modifier.padding(start = 74.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(11.dp)
                    .clip(shape)
                    .background(shimmerColor.copy(alpha = shimmerColor.alpha * 0.7f)),
            )
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(11.dp)
                    .clip(shape)
                    .background(shimmerColor.copy(alpha = shimmerColor.alpha * 0.7f)),
            )
        }
    }
}
