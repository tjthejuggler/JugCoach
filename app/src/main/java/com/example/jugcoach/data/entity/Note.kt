package com.example.jugcoach.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.jugcoach.data.converter.DateConverter
import com.example.jugcoach.data.converter.ListConverter
import java.time.Instant

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = Pattern::class,
            parentColumns = ["id"],
            childColumns = ["relatedPatternId"],
            onDelete = ForeignKey.Companion.SET_NULL
        ),
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["relatedSessionId"],
            onDelete = ForeignKey.Companion.SET_NULL
        ),
        ForeignKey(
            entity = Coach::class,
            parentColumns = ["id"],
            childColumns = ["coachId"],
            onDelete = ForeignKey.Companion.CASCADE
        )
    ],
    indices = [
        Index(value = ["relatedPatternId"]),
        Index(value = ["relatedSessionId"]),
        Index(value = ["coachId"])
    ]
)
@TypeConverters(DateConverter::class, ListConverter::class)
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val coachId: Long,
    val title: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val type: NoteType,
    val relatedPatternId: String? = null,
    val relatedSessionId: Long? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    // Additional metadata stored as JSON string
    val metadata: String = "{}"
)

enum class NoteType {
    COACHING_NOTE,
    CONVERSATION,
    TAG_DEFINITION,
    EXPERIMENT,
    GENERAL
}
