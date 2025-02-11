package com.example.jugcoach.data.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.jugcoach.data.converter.ListConverter
import com.example.jugcoach.data.converter.DateConverter
import com.example.jugcoach.data.converter.RunListConverter
import com.example.jugcoach.data.entity.Coach

@Entity(
    tableName = "patterns",
    foreignKeys = [
        ForeignKey(
            entity = Coach::class,
            parentColumns = ["id"],
            childColumns = ["coachId"],
            onDelete = ForeignKey.Companion.SET_NULL
        )
    ],
    indices = [Index(value = ["coachId"])]
)
@TypeConverters(ListConverter::class, DateConverter::class)
data class Pattern(
    @PrimaryKey
    val id: String,
    val name: String,
    val coachId: Long? = null, // null means it's a shared pattern
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
    val record: Record? = null,
    @Embedded(prefix = "history_")
    val runHistory: RunHistory = RunHistory()
)

data class Record(
    val catches: Int,
    val date: Long // timestamp
)

data class Run(
    val catches: Int? = null,
    val duration: Long? = null, // duration in seconds if time-based
    val isCleanEnd: Boolean,
    val date: Long // timestamp
)

@TypeConverters(RunListConverter::class)
data class RunHistory(
    val runs: List<Run> = emptyList()
)
