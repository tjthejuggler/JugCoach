package com.example.jugcoach.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.jugcoach.data.entity.CoachProposal
import com.example.jugcoach.data.entity.ProposalStatus
import com.example.jugcoach.data.entity.ProposalWithCoach
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachProposalDao {
    @Transaction
    @Query("SELECT * FROM coach_proposals WHERE patternId = :patternId AND status = :status")
    fun getProposalsForPatternWithStatus(patternId: String, status: ProposalStatus): Flow<List<ProposalWithCoach>>

    @Query("SELECT * FROM coach_proposals WHERE coachId = :coachId")
    fun getProposalsForCoach(coachId: Long): Flow<List<CoachProposal>>

    @Insert
    suspend fun insertProposal(proposal: CoachProposal): Long

    @Query("UPDATE coach_proposals SET status = :newStatus WHERE id = :proposalId")
    suspend fun updateProposalStatus(proposalId: Long, newStatus: ProposalStatus)

    @Query("DELETE FROM coach_proposals WHERE id = :proposalId")
    suspend fun deleteProposal(proposalId: Long)

    @Query("SELECT * FROM coach_proposals WHERE id = :proposalId")
    suspend fun getProposalById(proposalId: Long): CoachProposal?

    @Transaction
    @Query("SELECT * FROM coach_proposals WHERE patternId = :patternId ORDER BY createdAt DESC")
    fun getProposalsForPattern(patternId: String): Flow<List<CoachProposal>>
}
