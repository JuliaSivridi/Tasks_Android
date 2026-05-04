package com.stler.tasks.ui.main

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.domain.model.Folder
import com.stler.tasks.domain.model.Label
import com.stler.tasks.domain.model.Task
import com.stler.tasks.ui.alltasks.AllTasksScreen
import com.stler.tasks.ui.calendar.CalendarScreen
import com.stler.tasks.ui.completed.CompletedScreen
import com.stler.tasks.ui.folder.FolderScreen
import com.stler.tasks.ui.label.LabelScreen
import com.stler.tasks.ui.navigation.Screen
import com.stler.tasks.ui.priority.PriorityScreen
import com.stler.tasks.ui.task.TaskFormResult
import com.stler.tasks.ui.task.TaskFormSheet
import com.stler.tasks.ui.task.TaskFormViewModel
import com.stler.tasks.ui.settings.SettingsScreen
import com.stler.tasks.ui.upcoming.UpcomingScreen
import com.stler.tasks.ui.util.LocalSnackbarHostState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private sealed interface FolderDialogMode {
    data object Create : FolderDialogMode
    data class  Edit(val folder: Folder) : FolderDialogMode
}

private sealed interface LabelDialogMode {
    data object Create : LabelDialogMode
    data class  Edit(val label: Label) : LabelDialogMode
}

@Composable
fun MainScreen(
    onSignOut: () -> Unit = {},
    initialDeepLinkUri: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
    formViewModel: TaskFormViewModel = hiltViewModel(),
) {
    // ── Settings overlay — shown instead of the main content when true ───────
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) {
        SettingsScreen(onNavigateBack = { showSettings = false })
        return
    }

    val navController     = rememberNavController()
    val drawerState       = rememberDrawerState(DrawerValue.Closed)
    val scope             = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val folders           by viewModel.folders.collectAsStateWithLifecycle()
    val labels            by viewModel.labels.collectAsStateWithLifecycle()
    val syncState         by viewModel.syncState.collectAsStateWithLifecycle()
    val authData          by viewModel.authData.collectAsStateWithLifecycle()
    val sidebarState      by viewModel.sidebarState.collectAsStateWithLifecycle()
    val selectedCalendars by viewModel.selectedCalendars.collectAsStateWithLifecycle()

    val backStackEntry   by navController.currentBackStackEntryAsState()
    val currentRoute     = backStackEntry?.destination?.route
    val currentFolderId  = backStackEntry?.arguments?.getString("folderId")
    val currentLabelId   = backStackEntry?.arguments?.getString("labelId")
    val currentPriority  = backStackEntry?.arguments?.getString("priority")
    val currentCalendarId = backStackEntry?.arguments?.getString("calendarId")

    val screenTitle = when {
        currentRoute == Screen.UPCOMING  -> "Upcoming"
        currentRoute == Screen.ALL_TASKS -> "All Tasks"
        currentRoute == Screen.COMPLETED -> "Completed"
        currentRoute == Screen.FOLDER    -> folders.find { it.id == currentFolderId }?.name ?: "Folder"
        currentRoute == Screen.LABEL     -> labels.find { it.id == currentLabelId }?.name ?: "Label"
        currentRoute == Screen.PRIORITY  -> when (currentPriority) {
            "urgent" -> "Urgent"; "important" -> "Important"; else -> "Normal"
        }
        currentRoute == Screen.CALENDAR  -> selectedCalendars.find { it.id == currentCalendarId }?.summary ?: "Calendar"
        else -> "Stler Tasks"
    }

    // ── Folder / Label dialog state ───────────────────────────────────────────
    var folderDialog  by remember { mutableStateOf<FolderDialogMode?>(null) }
    var labelDialog   by remember { mutableStateOf<LabelDialogMode?>(null) }

    // ── Task form state ───────────────────────────────────────────────────────
    var showForm                     by remember { mutableStateOf(false) }
    var editingTask                  by remember { mutableStateOf<Task?>(null) }
    var editingCalendarEvent         by remember { mutableStateOf<CalendarEvent?>(null) }
    var editingCalendarEventScheduleOnly by remember { mutableStateOf(false) }
    var formFolderId                 by remember { mutableStateOf("fld-inbox") }
    var formParentId                 by remember { mutableStateOf("") }

    fun openCreate(folderId: String = "fld-inbox", parentId: String = "") {
        editingTask                      = null
        editingCalendarEvent             = null
        editingCalendarEventScheduleOnly = false
        formFolderId                     = folderId
        formParentId                     = parentId
        showForm                         = true
    }
    fun openEdit(task: Task) {
        editingTask                      = task
        editingCalendarEvent             = null
        editingCalendarEventScheduleOnly = false
        formFolderId                     = task.folderId
        formParentId                     = task.parentId
        showForm                         = true
    }
    fun openEditEvent(event: CalendarEvent) {
        editingTask                      = null
        editingCalendarEvent             = event
        editingCalendarEventScheduleOnly = false
        showForm                         = true
    }
    /** Opens the event form in schedule-only mode (date/time/repeat only, no title/calendar). */
    fun openEditEventSchedule(event: CalendarEvent) {
        editingTask                      = null
        editingCalendarEvent             = event
        editingCalendarEventScheduleOnly = true
        showForm                         = true
    }
    fun openAddSubtask(parent: Task) {
        editingTask                      = null
        editingCalendarEvent             = null
        editingCalendarEventScheduleOnly = false
        formFolderId                     = parent.folderId
        formParentId                     = parent.id
        showForm                         = true
    }

    // ── Deeplink handling ─────────────────────────────────────────────────────
    LaunchedEffect(initialDeepLinkUri) {
        val uri = initialDeepLinkUri ?: return@LaunchedEffect
        // Wait until NavHost has pushed its start destination — ensures the graph
        // is fully ready before we call navigate(), which avoids a crash on cold start.
        navController.currentBackStackEntryFlow.first()
        when {
            uri.startsWith("stlertasks://task/") -> {
                val taskId = uri.removePrefix("stlertasks://task/").trimEnd('/')
                if (taskId.isNotBlank()) {
                    // Find the task in all pending tasks or completed tasks
                    val allPending = viewModel.allTasksForDeepLink.first()
                    val task = allPending.find { it.id == taskId }
                    if (task != null) openEdit(task)
                }
                onDeepLinkConsumed()
            }
            uri.startsWith("stlertasks://event/") -> {
                // Format: stlertasks://event/{calendarId}/{eventId}
                val path  = uri.removePrefix("stlertasks://event/")
                val slash = path.indexOf('/')
                if (slash > 0) {
                    val eventId = path.substring(slash + 1).trimEnd('/')
                    if (eventId.isNotBlank()) {
                        // withTimeoutOrNull: avoids blocking indefinitely on cold start
                        // when Room hasn't emitted yet (large DB / slow disk).
                        val allEvents = withTimeoutOrNull(2_000L) {
                            viewModel.allEventsForDeepLink.first()
                        } ?: emptyList()
                        val event = allEvents.find { it.id == eventId }
                        if (event != null) openEditEvent(event)
                    }
                }
                onDeepLinkConsumed()
            }
            uri.startsWith("stlertasks://create") -> {
                val folderId = Uri.parse(uri)
                    .getQueryParameter("folderId") ?: "fld-inbox"
                openCreate(folderId)
                onDeepLinkConsumed()
            }
            uri == "stlertasks://upcoming" -> {
                // On cold start the NavHost is already at UPCOMING (start destination),
                // so we must NOT navigate — doing so would replace the ViewModel and
                // produce a blank screen while WhileSubscribed(5000) restarts the DB flow.
                // Only navigate if we're currently somewhere else (e.g. a folder screen).
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                if (currentRoute != Screen.UPCOMING) {
                    val popped = navController.popBackStack(Screen.UPCOMING, inclusive = false)
                    if (!popped) {
                        navController.navigate(Screen.UPCOMING) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                }
                onDeepLinkConsumed()
            }
            uri == "stlertasks://all_tasks" -> {
                navController.navigate(Screen.ALL_TASKS) {
                    popUpTo(navController.graph.startDestinationId)
                    launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
            uri.startsWith("stlertasks://folder/") -> {
                val folderId = uri.removePrefix("stlertasks://folder/").trimEnd('/')
                if (folderId.isNotBlank()) {
                    navController.navigate(Screen.folderRoute(folderId)) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                }
                onDeepLinkConsumed()
            }
        }
    }

    // Folder context for the "+" sidebar button
    val sidebarFolderContext = if (currentRoute == Screen.FOLDER) currentFolderId ?: "fld-inbox"
                               else "fld-inbox"

    fun handleFormResult(result: TaskFormResult) {
        val et = editingTask
        if (et != null) formViewModel.updateTask(et, result)
        else            formViewModel.createTask(result)
        showForm = false
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId)
            launchSingleTop = true
        }
        scope.launch { drawerState.close() }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SidebarMenu(
                currentRoute      = currentRoute,
                currentFolderId   = currentFolderId,
                currentLabelId    = currentLabelId,
                currentPriority   = currentPriority,
                currentCalendarId = currentCalendarId,
                folders           = folders,
                labels            = labels,
                selectedCalendars = selectedCalendars,
                syncState         = syncState,
                sidebarState      = sidebarState,
                onNavigate      = ::navigateTo,
                onToggleSection = viewModel::toggleSection,
                onAddTask       = { openCreate(sidebarFolderContext); scope.launch { drawerState.close() } },
                onAddFolder     = { folderDialog = FolderDialogMode.Create },
                onAddLabel      = { labelDialog  = LabelDialogMode.Create },
                onEditFolder    = { folderDialog = FolderDialogMode.Edit(it) },
                onDeleteFolder  = { viewModel.deleteFolder(it.id) },
                onEditLabel     = { labelDialog  = LabelDialogMode.Edit(it) },
                onDeleteLabel   = { viewModel.deleteLabel(it.id) },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TasksTopAppBar(
                    title                = screenTitle,
                    syncState            = syncState,
                    userName             = authData.userName,
                    userEmail            = authData.userEmail,
                    userAvatarUrl        = authData.userAvatarUrl,
                    onMenuClick          = { scope.launch { drawerState.open() } },
                    onSyncClick          = viewModel::triggerSync,
                    onSignOut            = onSignOut,
                    onNavigateToSettings = { showSettings = true },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { openCreate(sidebarFolderContext) }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add task")
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            NavHost(
                navController    = navController,
                startDestination = Screen.UPCOMING,
                modifier         = Modifier.padding(innerPadding),
            ) {
                composable(Screen.UPCOMING) {
                    UpcomingScreen(
                        onEditTask           = { openEdit(it) },
                        onAddSubtask         = { openAddSubtask(it) },
                        onEditEvent          = { openEditEvent(it) },
                        onEditEventSchedule  = { openEditEventSchedule(it) },
                    )
                }
                composable(Screen.ALL_TASKS) {
                    AllTasksScreen(
                        onEditTask           = { openEdit(it) },
                        onAddSubtask         = { openAddSubtask(it) },
                        onEditEvent          = { openEditEvent(it) },
                        onEditEventSchedule  = { openEditEventSchedule(it) },
                    )
                }
                composable(Screen.COMPLETED) {
                    CompletedScreen()
                }
                composable(
                    route     = Screen.FOLDER,
                    arguments = listOf(navArgument("folderId") { type = NavType.StringType }),
                ) { entry ->
                    val folderId = entry.arguments?.getString("folderId") ?: return@composable
                    FolderScreen(
                        folderId     = folderId,
                        onEditTask   = { openEdit(it) },
                        onAddSubtask = { openAddSubtask(it) },
                    )
                }
                composable(
                    route     = Screen.LABEL,
                    arguments = listOf(navArgument("labelId") { type = NavType.StringType }),
                ) {
                    LabelScreen(
                        onEditTask   = { openEdit(it) },
                        onAddSubtask = { openAddSubtask(it) },
                    )
                }
                composable(
                    route     = Screen.PRIORITY,
                    arguments = listOf(navArgument("priority") { type = NavType.StringType }),
                ) { entry ->
                    PriorityScreen(
                        priority     = entry.arguments?.getString("priority") ?: "normal",
                        onEditTask   = { openEdit(it) },
                        onAddSubtask = { openAddSubtask(it) },
                    )
                }
                composable(
                    route     = Screen.CALENDAR,
                    arguments = listOf(navArgument("calendarId") { type = NavType.StringType }),
                ) { entry ->
                    val calendarId = entry.arguments?.getString("calendarId") ?: return@composable
                    CalendarScreen(
                        calendarId          = calendarId,
                        onEditEvent         = { openEditEvent(it) },
                        onEditEventSchedule = { openEditEventSchedule(it) },
                    )
                }
            }
        }
    }

    // ── Folder / Label dialogs ────────────────────────────────────────────────
    when (val fd = folderDialog) {
        is FolderDialogMode.Create -> FolderFormDialog(
            onConfirm = { name, color -> viewModel.createFolder(name, color); folderDialog = null },
            onDismiss = { folderDialog = null },
        )
        is FolderDialogMode.Edit -> FolderFormDialog(
            existing  = fd.folder,
            onConfirm = { name, color -> viewModel.updateFolder(fd.folder, name, color); folderDialog = null },
            onDismiss = { folderDialog = null },
        )
        null -> Unit
    }
    when (val ld = labelDialog) {
        is LabelDialogMode.Create -> LabelFormDialog(
            onConfirm = { name, color -> viewModel.createLabel(name, color); labelDialog = null },
            onDismiss = { labelDialog = null },
        )
        is LabelDialogMode.Edit -> LabelFormDialog(
            existing  = ld.label,
            onConfirm = { name, color -> viewModel.updateLabel(ld.label, name, color); labelDialog = null },
            onDismiss = { labelDialog = null },
        )
        null -> Unit
    }

    // ── Task / Event form sheet ───────────────────────────────────────────────
    if (showForm) {
        TaskFormSheet(
            task            = editingTask,
            calendarEvent   = editingCalendarEvent,
            scheduleOnly    = editingCalendarEventScheduleOnly,
            initialFolderId = formFolderId,
            initialParentId = formParentId,
            labels          = labels,
            folders         = folders,
            onConfirm       = { handleFormResult(it) },
            onDismiss       = { showForm = false; editingCalendarEvent = null; editingCalendarEventScheduleOnly = false },
        )
    }
    } // end CompositionLocalProvider(LocalSnackbarHostState)
}
