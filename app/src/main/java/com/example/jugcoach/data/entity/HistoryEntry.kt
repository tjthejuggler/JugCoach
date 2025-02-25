package com.example.jugcoach.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an entry in the app history log
 */
@Entity(
    tableName = "history_entries",
    foreignKeys = [
        ForeignKey(
            entity = Pattern::class,
            parentColumns = ["id"],
            childColumns = ["relatedPatternId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Coach::class,
            parentColumns = ["id"],
            childColumns = ["relatedCoachId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["relatedPatternId"]),
        Index(value = ["relatedCoachId"]),
        Index(value = ["timestamp"])
    ]
)
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // RUN_ADDED, PATTERN_CREATED, COACH_CREATED, etc.
    val timestamp: Long = System.currentTimeMillis(),
    val description: String,
    val relatedPatternId: String? = null,
    val relatedCoachId: Long? = null,
    val isFromUser: Boolean = true // true if user action, false if from LLM coach
) {
    companion object {
        const val TYPE_RUN_ADDED = "RUN_ADDED"
        const val TYPE_PATTERN_CREATED = "PATTERN_CREATED"
        const val TYPE_COACH_CREATED = "COACH_CREATED"
    }
}