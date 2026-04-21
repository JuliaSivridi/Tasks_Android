package com.stler.tasks.domain.model

data class Folder(
    val id: String,
    val name: String,
    val color: String,    // hex string, e.g. "#f97316"
    val sortOrder: Int = 0,
) {
    val isInbox: Boolean get() = id == "fld-inbox"
}
