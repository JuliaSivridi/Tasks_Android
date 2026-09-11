package com.stler.tasks.ui.alltasks

import com.stler.tasks.domain.model.Priority

data class TaskFilterState(
    val priorityFilter : Set<Priority> = emptySet(),
    val labelFilter    : Set<String>   = emptySet(),
    val folderFilter   : Set<String>   = emptySet(),
    val calendarFilter : Set<String>   = emptySet(),
) {
    val hasFilters: Boolean get() =
        priorityFilter.isNotEmpty() || labelFilter.isNotEmpty() ||
        folderFilter.isNotEmpty()   || calendarFilter.isNotEmpty()
}
