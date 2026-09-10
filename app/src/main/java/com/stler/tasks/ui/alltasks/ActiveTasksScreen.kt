package com.stler.tasks.ui.alltasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.stler.tasks.ui.theme.ControlShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.domain.model.Task
import com.stler.tasks.ui.completed.CompletedScreen
import com.stler.tasks.ui.completed.CompletedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTasksScreen(
    onEditTask          : (Task) -> Unit = {},
    onAddSubtask        : (Task) -> Unit = {},
    onEditEvent         : (CalendarEvent) -> Unit = {},
    onEditEventSchedule : (CalendarEvent) -> Unit = {},
    allTasksViewModel   : AllTasksViewModel = hiltViewModel(),
    completedViewModel  : CompletedViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            SegmentedButton(
                selected = selectedTab == 0,
                onClick  = { selectedTab = 0 },
                shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2, baseShape = ControlShape),
            ) { Text("Active") }
            SegmentedButton(
                selected = selectedTab == 1,
                onClick  = { selectedTab = 1 },
                shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2, baseShape = ControlShape),
            ) { Text("Done") }
        }

        if (selectedTab == 0) {
            AllTasksScreen(
                onEditTask          = onEditTask,
                onAddSubtask        = onAddSubtask,
                onEditEvent         = onEditEvent,
                onEditEventSchedule = onEditEventSchedule,
                viewModel           = allTasksViewModel,
            )
        } else {
            CompletedScreen(viewModel = completedViewModel)
        }
    }
}
