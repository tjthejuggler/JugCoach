package com.example.jugcoach.data.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.jugcoach.data.converter.ListConverter
import com.example.jugcoach.data.converter.DateConverter

@Entity(tableName = "patterns")
@TypeConverters(ListConverter::class, DateConverter::class)
data class Pattern(
    @PrimaryKey
    val id: String,
    val name: String,
    val difficulty: String? = null, // 1-10
    val siteswap: String? = null,
    val num: String? = null, // number of balls
    val explanation: String? = null,
    val gifUrl: String? = null,
    val video: String? = null,
    val url: String? = null,
    val tags: List<String> = emptyList(),
    val prerequisites: List<String> = emptyList(),
    val dependents: List<String> = emptyList(),
    val related: List<String> = emptyList(),
    @Embedded(prefix = "record_")
    val record: Record? = null
)

data class Record(
    val catches: Int,
    val date: Long // timestamp
)
