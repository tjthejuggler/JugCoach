package com.example.jugcoach.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = Coach::class,
            parentColumns = ["id"],
            childColumns = ["coachId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("coachId")]
)
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val coachId: Long,
    val title: String? = null, // Custom title if set by user, otherwise null
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val lastMessageAt: Long = System.currentTimeMillis()
)
