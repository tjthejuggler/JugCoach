package com.example.jugcoach.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.jugcoach.data.converter.ListConverter

@Entity(tableName = "patterns")
@TypeConverters(ListConverter::class)
data class Pattern(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String? = null,
    val difficulty: Int? = null,
    val tags: List<String> = emptyList(),
    val prerequisites: List<String> = emptyList(),
    val dependents: List<String> = emptyList(),
    // Dynamic properties stored as JSON string
    val properties: String = "{}"
)
