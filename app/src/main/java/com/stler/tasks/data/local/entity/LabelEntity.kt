package com.stler.tasks.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stler.tasks.domain.model.Label

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,    // hex, e.g. "#3b82f6"
)

fun LabelEntity.toDomain() = Label(
    id = id,
    name = name,
    color = color,
)

fun Label.toEntity() = LabelEntity(
    id = id,
    name = name,
    color = color,
)
