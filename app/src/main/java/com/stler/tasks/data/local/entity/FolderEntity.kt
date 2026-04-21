package com.stler.tasks.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stler.tasks.domain.model.Folder

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,    // hex, e.g. "#f97316"
    val sortOrder: Int = 0,
)

fun FolderEntity.toDomain() = Folder(
    id = id,
    name = name,
    color = color,
    sortOrder = sortOrder,
)

fun Folder.toEntity() = FolderEntity(
    id = id,
    name = name,
    color = color,
    sortOrder = sortOrder,
)
