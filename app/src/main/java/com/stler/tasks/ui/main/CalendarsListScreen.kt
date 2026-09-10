package com.stler.tasks.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stler.tasks.domain.model.CalendarItem
import com.stler.tasks.ui.util.EmptyState
import com.stler.tasks.util.toComposeColor

@Composable
fun CalendarsListScreen(
    calendars              : List<CalendarItem>,
    onNavigateToCalendar   : (String) -> Unit,
) {
    if (calendars.isEmpty()) {
        EmptyState(
            icon     = Icons.Outlined.CalendarMonth,
            message  = "No calendars",
            subtitle = "Select calendars in Settings",
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(calendars, key = { it.id }) { calendar ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToCalendar(calendar.id) }
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 56.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector        = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint               = calendar.color.toComposeColor(),
                    modifier           = Modifier.size(22.dp),
                )
                Text(
                    text     = calendar.summary,
                    style    = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector        = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(20.dp),
                )
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
