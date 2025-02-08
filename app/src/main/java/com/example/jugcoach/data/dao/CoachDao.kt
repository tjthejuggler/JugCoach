package com.example.jugcoach.data.dao

import androidx.room.*
import com.example.jugcoach.data.entity.Coach
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachDao {
    @Query("SELECT * FROM coaches ORDER BY createdAt DESC")
    fun getAllCoaches(): Flow<List<Coach>>

    @Query("SELECT * FROM coaches WHERE isHeadCoach = 1 LIMIT 1")
    suspend fun getHeadCoach(): Coach?

    @Query("SELECT * FROM coaches WHERE id = :id")
    suspend fun getCoachById(id: Long): Coach?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoach(coach: Coach): Long

    @Update
    suspend fun updateCoach(coach: Coach)

    @Delete
    suspend fun deleteCoach(coach: Coach)

    @Query("SELECT EXISTS(SELECT 1 FROM coaches WHERE isHeadCoach = 1)")
    suspend fun hasHeadCoach(): Boolean

    @Transaction
    suspend fun createHeadCoach() {
        if (!hasHeadCoach()) {
            val headCoach = Coach(
                name = "Head Coach",
                apiKeyName = "llm_api_key", // Default API key
                description = "Your primary juggling coach",
                isHeadCoach = true
            )
            insertCoach(headCoach)
        }
    }
}
