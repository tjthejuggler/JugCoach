package com.example.jugcoach.data.entity

import androidx.room.Embedded
import androidx.room.Relation

data class ProposalWithCoach(
    @Embedded val proposal: CoachProposal,
    @Relation(
        parentColumn = "coachId",
        entityColumn = "id"
    )
    val coach: Coach
)
