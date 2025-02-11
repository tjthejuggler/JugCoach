package com.example.jugcoach.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.entity.Coach
import com.example.jugcoach.data.converter.ProposalStatusConverter

enum class ProposalStatus {
    PENDING,
    APPROVED,
    REJECTED
}

@TypeConverters(ProposalStatusConverter::class)
@Entity(
    tableName = "coach_proposals",
    foreignKeys = [
        ForeignKey(
            entity = Pattern::class,
            parentColumns = ["id"],
            childColumns = ["patternId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Coach::class,
            parentColumns = ["id"],
            childColumns = ["coachId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["patternId"]),
        Index(value = ["coachId"]),
        Index(value = ["status"])
    ]
)
data class CoachProposal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patternId: String,
    val coachId: Long,
    val proposedChanges: String, // JSON string containing changes
    val notes: String? = null,
    val status: ProposalStatus = ProposalStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)
