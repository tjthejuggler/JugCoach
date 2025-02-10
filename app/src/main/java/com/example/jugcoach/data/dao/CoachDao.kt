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
                isHeadCoach = true,
                systemPrompt = """
                    You are a juggling coach with expertise in teaching juggling patterns, techniques, and progressions. 
                    Your role is to:
                    1. Help users learn and improve their juggling skills
                    2. Provide clear, step-by-step instructions for learning new patterns
                    3. Offer feedback and corrections based on user descriptions
                    4. Suggest appropriate progressions based on skill level
                    5. Answer questions about juggling theory, history, and technique
                    6. Provide motivation and encouragement
                    Keep responses focused on juggling and related physical skills. Be encouraging but professional.
                """.trimIndent()
            )
            insertCoach(headCoach)
        }
    }
}
