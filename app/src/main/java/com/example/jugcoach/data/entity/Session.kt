package com.example.jugcoach.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.jugcoach.data.converter.DateConverter
import java.time.Instant

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = Pattern::class,
            parentColumns = ["id"],
            childColumns = ["patternId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["patternId"])
    ]
)
@TypeConverters(DateConverter::class)
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patternId: String,
    val startTime: Instant,
    val endTime: Instant? = null,
    val catches: Int = 0,
    val duration: Long? = null, // Duration in seconds
    val mood: Int? = null, // Scale 1-5
    val fatigue: Int? = null, // Scale 1-5
    val location: String? = null,
    val notes: String? = null,
    // Additional metrics stored as JSON string
    val metrics: String = "{}"
)
