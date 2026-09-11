package com.stler.tasks.ui.main

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.stler.tasks.domain.model.CalendarEvent
import com.stler.tasks.domain.model.Task
import com.stler.tasks.ui.alltasks.ActiveTasksScreen
import com.stler.tasks.ui.alltasks.AllTasksViewModel
import com.stler.tasks.ui.alltasks.TaskFilterSheet
import com.stler.tasks.ui.alltasks.TaskFilterState
import com.stler.tasks.ui.calendar.CalendarScreen
import com.stler.tasks.ui.completed.CompletedViewModel
import com.stler.tasks.ui.feedback.FeedbackScreen
import com.stler.tasks.ui.folder.FolderScreen
import com.stler.tasks.ui.help.HelpScreen
import com.stler.tasks.ui.navigation.Screen
import com.stler.tasks.ui.settings.SettingsScreen
import com.stler.tasks.ui.task.TaskFormResult
import com.stler.tasks.ui.task.TaskFormSheet
import com.stler.tasks.ui.task.TaskFormViewModel
import com.stler.tasks.ui.upcoming.UpcomingScreen
import com.stler.tasks.ui.upcoming.UpcomingViewModel
import com.stler.tasks.ui.util.LocalSnackbarHostState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull


@Composable
fun MainScreen(
    onSignOut: () -> Unit = {},
    initialDeepLinkUri: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
    formViewModel: TaskFormViewModel = hiltViewModel(),
) {
    // ── Overlay screens (stack-based so Help/Feedback can back to Settings) ─
    var overlayStack by remember { mutableStateOf(emptyList<String>()) }
    val currentOverlay = overlayStack.lastOrNull()

    fun pushOverlay(vararg screens: String) { overlayStack = overlayStack + screens.toList() }
    fun popOverlay() { overlayStack = overlayStack.dropLast(1) }

    val navController     = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Filter sheet ──────────────────────────────────────────────────────
    var showFilterSheet by remember { mutableStateOf(false) }

    val folders           by viewModel.folders.collectAsStateWithLifecycle()
    val labels            by viewModel.labels.collectAsStateWithLifecycle()
    val syncState         by viewModel.syncState.collectAsStateWithLifecycle()
    val authData          by viewModel.authData.collectAsStateWithLifecycle()
    val selectedCalendars by viewModel.selectedCalendars.collectAsStateWithLifecycle()
    val featureFlags      by viewModel.featureFlags.collectAsStateWithLifecycle()

    val backStackEntry    by navController.currentBackStackEntryAsState()
    val currentRoute      = backStackEntry?.destination?.route
    val currentFolderId   = backStackEntry?.arguments?.getString("folderId")
    val currentCalendarId = backStackEntry?.arguments?.getString("calendarId")

    // Sub-screen ViewModels for the filter sheet (same instances as the screens use).
    // Conditional calls mirror Money's MainScreen pattern.
    val upcomingVm  : UpcomingViewModel?  =
        if (currentRoute == Screen.UPCOMING   && backStackEntry != null) hiltViewModel(backStackEntry!!) else null
    val allTasksVm  : AllTasksViewModel?  =
        if (currentRoute == Screen.ALL_TASKS  && backStackEntry != null) hiltViewModel(backStackEntry!!) else null
    val completedVm : CompletedViewModel? =
        if (currentRoute == Screen.ALL_TASKS  && backStackEntry != null) hiltViewModel(backStackEntry!!) else null

    val upcomingFilterState  by remember(upcomingVm)  { upcomingVm?.filterState  ?: kotlinx.coroutines.flow.MutableStateFlow(TaskFilterState()) }.collectAsStateWithLifecycle()
    val allTasksFilterState  by remember(allTasksVm)  { allTasksVm?.filterState  ?: kotlinx.coroutines.flow.MutableStateFlow(TaskFilterState()) }.collectAsStateWithLifecycle()

    val currentFilterState = when (currentRoute) {
        Screen.UPCOMING  -> upcomingFilterState
        Screen.ALL_TASKS -> allTasksFilterState
        else             -> TaskFilterState()
    }
    val filterActive = currentFilterState.hasFilters

    val screenTitle = when {
        currentRoute == Screen.UPCOMING       -> "Upcoming"
        currentRoute == Screen.ALL_TASKS      -> "All Tasks"
        currentRoute == Screen.FOLDERS_LIST   -> "Folders"
        currentRoute == Screen.CALENDARS_LIST -> "Calendars"
        currentRoute == Screen.MENU_SCREEN    -> "Menu"
        currentRoute == Screen.FOLDER  -> folders.find { it.id == currentFolderId }?.name ?: "Folder"
        currentRoute == Screen.CALENDAR -> selectedCalendars.find { it.id == currentCalendarId }?.summary ?: "Calendar"
        else -> "Stler Tasks"
    }

    // ── Task form state ───────────────────────────────────────────────────────
    var showForm                         by remember { mutableStateOf(false) }
    var editingTask                      by remember { mutableStateOf<Task?>(null) }
    var editingCalendarEvent             by remember { mutableStateOf<CalendarEvent?>(null) }
    var editingCalendarEventScheduleOnly by remember { mutableStateOf(false) }
    var formFolderId                     by remember { mutableStateOf("fld-inbox") }
    var formParentId                     by remember { mutableStateOf("") }
    var formInitialCalendarId            by remember { mutableStateOf<String?>(null) }

    fun openCreate(folderId: String = "fld-inbox", parentId: String = "") {
        editingTask = null; editingCalendarEvent = null; editingCalendarEventScheduleOnly = false
        formFolderId = folderId; formParentId = parentId; formInitialCalendarId = null
        showForm = true
    }
    fun openCreateEvent(calendarId: String) {
        editingTask = null; editingCalendarEvent = null; editingCalendarEventScheduleOnly = false
        formInitialCalendarId = calendarId; showForm = true
    }
    fun openEdit(task: Task) {
        editingTask = task; editingCalendarEvent = null; editingCalendarEventScheduleOnly = false
        formFolderId = task.folderId; formParentId = task.parentId; showForm = true
    }
    fun openEditEvent(event: CalendarEvent) {
        editingTask = null; editingCalendarEvent = event; editingCalendarEventScheduleOnly = false
        showForm = true
    }
    fun openEditEventSchedule(event: CalendarEvent) {
        editingTask = null; editingCalendarEvent = event; editingCalendarEventScheduleOnly = true
        showForm = true
    }
    fun openAddSubtask(parent: Task) {
        editingTask = null; editingCalendarEvent = null; editingCalendarEventScheduleOnly = false
        formFolderId = parent.folderId; formParentId = parent.id; showForm = true
    }

    // ── Deeplink handling ─────────────────────────────────────────────────────
    LaunchedEffect(initialDeepLinkUri) {
        val uri = initialDeepLinkUri ?: return@LaunchedEffect
        navController.currentBackStackEntryFlow.first()
        when {
            uri.startsWith("stlertasks://task/") -> {
                val taskId = uri.removePrefix("stlertasks://task/").trimEnd('/')
                if (taskId.isNotBlank()) {
                    val task = viewModel.allTasksForDeepLink.first().find { it.id == taskId }
                    if (task != null) openEdit(task)
                }
                onDeepLinkConsumed()
            }
            uri.startsWith("stlertasks://event/") -> {
                val path = uri.removePrefix("stlertasks://event/")
                val slash = path.indexOf('/')
                if (slash > 0) {
                    val eventId = path.substring(slash + 1).trimEnd('/')
                    if (eventId.isNotBlank()) {
                        val event = withTimeoutOrNull(2_000L) {
                            viewModel.allEventsForDeepLink.first()
                        }?.find { it.id == eventId }
                        if (event != null) openEditEvent(event)
                    }
                }
                onDeepLinkConsumed()
            }
            uri.startsWith("stlertasks://create") -> {
                openCreate(Uri.parse(uri).getQueryParameter("folderId") ?: "fld-inbox")
                onDeepLinkConsumed()
            }
            uri == "stlertasks://upcoming" -> {
                val cr = navController.currentBackStackEntry?.destination?.route
                if (cr != Screen.UPCOMING) {
                    val popped = navController.popBackStack(Screen.UPCOMING, inclusive = false)
                    if (!popped) navController.navigate(Screen.UPCOMING) {
                        popUpTo(navController.graph.startDestinationId); launchSingleTop = true
                    }
                }
                onDeepLinkConsumed()
            }
            uri == "stlertasks://all_tasks" -> {
                navController.navigate(Screen.ALL_TASKS) {
                    popUpTo(navController.graph.startDestinationId); launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
            uri.startsWith("stlertasks://folder/") -> {
                val folderId = uri.removePrefix("stlertasks://folder/").trimEnd('/')
                if (folderId.isNotBlank()) navController.navigate(Screen.folderRoute(folderId)) {
                    popUpTo(navController.graph.startDestinationId); launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
        }
    }

    val fabFolderContext = if (currentRoute == Screen.FOLDER) currentFolderId ?: "fld-inbox"
                          else "fld-inbox"

    fun handleFormResult(result: TaskFormResult) {
        val et = editingTask
        if (et != null) formViewModel.updateTask(et, result) else formViewModel.createTask(result)
        showForm = false
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState    = true
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
            topBar = {
                TasksTopAppBar(
                    title         = screenTitle,
                    syncState     = syncState,
                    onSyncClick   = viewModel::triggerSync,
                    onBackClick   = if (currentRoute == Screen.FOLDER || currentRoute == Screen.CALENDAR) {
                        { navController.popBackStack() }
                    } else null,
                    filterActive  = filterActive,
                    onFilterClick = if (currentRoute == Screen.UPCOMING || currentRoute == Screen.ALL_TASKS) {
                        { showFilterSheet = true }
                    } else null,
                )
            },
            bottomBar = {
                NavigationBar {
                        NavigationBarItem(
                            icon     = { Icon(Icons.Outlined.CalendarToday, null) },
                            label    = null,
                            selected = currentRoute == Screen.UPCOMING,
                            onClick  = { navigateTo(Screen.UPCOMING) },
                        )
                        NavigationBarItem(
                            icon     = { Icon(Icons.Outlined.FormatListBulleted, null) },
                            label    = null,
                            selected = currentRoute == Screen.ALL_TASKS,
                            onClick  = { navigateTo(Screen.ALL_TASKS) },
                        )
                        if (featureFlags.foldersEnabled) {
                            NavigationBarItem(
                                icon     = { Icon(Icons.Outlined.Folder, null) },
                                label    = null,
                                selected = currentRoute == Screen.FOLDERS_LIST || currentRoute == Screen.FOLDER,
                                onClick  = { navigateTo(Screen.FOLDERS_LIST) },
                            )
                        }
                        if (featureFlags.calendarsEnabled && selectedCalendars.isNotEmpty()) {
                            NavigationBarItem(
                                icon     = { Icon(Icons.Outlined.CalendarMonth, null) },
                                label    = null,
                                selected = currentRoute == Screen.CALENDARS_LIST || currentRoute == Screen.CALENDAR,
                                onClick  = { navigateTo(Screen.CALENDARS_LIST) },
                            )
                        }
                        NavigationBarItem(
                            icon = {
                                if (authData.userAvatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model              = authData.userAvatarUrl,
                                        contentDescription = null,
                                        modifier           = Modifier.size(26.dp).clip(CircleShape),
                                    )
                                } else {
                                    Icon(Icons.Outlined.AccountCircle, null)
                                }
                            },
                            label    = null,
                            selected = currentRoute == Screen.MENU_SCREEN,
                            onClick  = { navigateTo(Screen.MENU_SCREEN) },
                        )
                    }
            },
            floatingActionButton = {
                if (currentRoute in setOf(Screen.UPCOMING, Screen.ALL_TASKS, Screen.FOLDER, Screen.CALENDAR)) {
                    FloatingActionButton(
                        onClick = {
                            if (currentRoute == Screen.CALENDAR && currentCalendarId != null) {
                                val cal = selectedCalendars.find { it.id == currentCalendarId }
                                if (cal != null && cal.accessRole in listOf("owner", "writer")) {
                                    openCreateEvent(currentCalendarId)
                                } else {
                                    openCreate(fabFolderContext)
                                }
                            } else {
                                openCreate(fabFolderContext)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add task")
                    }
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
                        onEditTask          = { openEdit(it) },
                        onAddSubtask        = { openAddSubtask(it) },
                        onEditEvent         = { openEditEvent(it) },
                        onEditEventSchedule = { openEditEventSchedule(it) },
                    )
                }
                composable(Screen.ALL_TASKS) {
                    ActiveTasksScreen(
                        onEditTask          = { openEdit(it) },
                        onAddSubtask        = { openAddSubtask(it) },
                        onEditEvent         = { openEditEvent(it) },
                        onEditEventSchedule = { openEditEventSchedule(it) },
                    )
                }
                composable(Screen.FOLDERS_LIST) {
                    FoldersListScreen(
                        folders            = folders,
                        featureFlags       = featureFlags,
                        onNavigateToFolder = { navController.navigate(Screen.folderRoute(it)) },
                    )
                }
                composable(Screen.CALENDARS_LIST) {
                    CalendarsListScreen(
                        calendars            = selectedCalendars,
                        onNavigateToCalendar = { navController.navigate(Screen.calendarRoute(it)) },
                    )
                }
                composable(Screen.MENU_SCREEN) {
                    MenuScreen(
                        authData             = authData,
                        onNavigateToSettings = { pushOverlay("settings") },
                        onNavigateToHelp     = { pushOverlay("help") },
                        onNavigateToFeedback = { pushOverlay("feedback") },
                        onNavigateToAbout    = { pushOverlay("about") },
                        onSignOut            = onSignOut,
                    )
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

            when (currentOverlay) {
                "settings" -> SettingsScreen(onNavigateBack = ::popOverlay)
                "help"     -> HelpScreen    (onNavigateBack = ::popOverlay)
                "feedback" -> FeedbackScreen(onNavigateBack = ::popOverlay)
                "about"    -> AboutScreen   (onNavigateBack = ::popOverlay)
            }
        }

        if (showFilterSheet) {
            TaskFilterSheet(
                filterState      = currentFilterState,
                labels           = labels,
                folders          = folders,
                calendars        = selectedCalendars,
                featureFlags     = featureFlags,
                showCalendars    = currentRoute != Screen.ALL_TASKS || selectedCalendars.isNotEmpty(),
                onTogglePriority = { p ->
                    upcomingVm?.togglePriorityFilter(p)
                    allTasksVm?.togglePriorityFilter(p)
                    completedVm?.togglePriorityFilter(p)
                },
                onToggleLabel    = { id ->
                    upcomingVm?.toggleLabelFilter(id)
                    allTasksVm?.toggleLabelFilter(id)
                    completedVm?.toggleLabelFilter(id)
                },
                onToggleFolder   = { id ->
                    upcomingVm?.toggleFolderFilter(id)
                    allTasksVm?.toggleFolderFilter(id)
                    completedVm?.toggleFolderFilter(id)
                },
                onToggleCalendar = { id ->
                    upcomingVm?.toggleCalendarFilter(id)
                    allTasksVm?.toggleCalendarFilter(id)
                },
                onClearAll       = {
                    upcomingVm?.clearAllFilters()
                    allTasksVm?.clearAllFilters()
                    completedVm?.clearAllFilters()
                },
                onDismiss        = { showFilterSheet = false },
            )
        }

        if (showForm) {
            TaskFormSheet(
                task              = editingTask,
                calendarEvent     = editingCalendarEvent,
                scheduleOnly      = editingCalendarEventScheduleOnly,
                initialFolderId   = formFolderId,
                initialParentId   = formParentId,
                initialCalendarId = formInitialCalendarId,
                labels            = labels,
                folders           = folders,
                onConfirm         = { handleFormResult(it) },
                onDismiss         = {
                    showForm = false
                    editingCalendarEvent             = null
                    editingCalendarEventScheduleOnly = false
                    formInitialCalendarId            = null
                },
            )
        }
    }
}
