package com.example.jugcoach.data.dao

import androidx.room.*
import com.example.jugcoach.data.entity.Session
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE coachId = :coachId ORDER BY startTime DESC")
    fun getAllSessions(coachId: Long): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id AND coachId = :coachId")
    suspend fun getSessionById(id: Long, coachId: Long): Session?

    @Query("""
        SELECT * FROM sessions 
        WHERE patternId = :patternId 
        AND coachId = :coachId 
        ORDER BY startTime DESC
    """)
    fun getSessionsForPattern(patternId: String, coachId: Long): Flow<List<Session>>

    @Insert
    suspend fun insertSession(session: Session): Long

    @Update
    suspend fun updateSession(session: Session)

    @Delete
    suspend fun deleteSession(session: Session)

    @Query("SELECT * FROM sessions WHERE endTime IS NULL AND coachId = :coachId")
    suspend fun getActiveSession(coachId: Long): Session?

    @Query("""
        SELECT AVG(catches) FROM sessions 
        WHERE patternId = :patternId 
        AND coachId = :coachId
        AND startTime >= :since
    """)
    fun getAverageCatchesForPattern(
        patternId: String,
        coachId: Long,
        since: Instant = Instant.now().minusSeconds(30 * 24 * 60 * 60) // Last 30 days
    ): Flow<Float?>

    @Query("""
        SELECT * FROM sessions
        WHERE patternId = :patternId
        AND coachId = :coachId
        AND catches = (
            SELECT MAX(catches)
            FROM sessions
            WHERE patternId = :patternId
            AND coachId = :coachId
        )
        LIMIT 1
    """)
    suspend fun getBestSessionForPattern(patternId: String, coachId: Long): Session?

    @Query("""
        SELECT COUNT(*) FROM sessions
        WHERE coachId = :coachId
        AND startTime >= :since
        AND endTime IS NOT NULL
    """)
    fun getCompletedSessionCount(
        coachId: Long,
        since: Instant = Instant.now().minusSeconds(7 * 24 * 60 * 60) // Last 7 days
    ): Flow<Int>

    @Query("""
        SELECT SUM(
            CASE 
                WHEN endTime IS NOT NULL THEN 
                    CAST((strftime('%s', endTime) - strftime('%s', startTime)) AS INTEGER)
                ELSE 0
            END
        ) as total_duration
        FROM sessions
        WHERE coachId = :coachId
        AND startTime >= :since
    """)
    fun getTotalPracticeTime(
        coachId: Long,
        since: Instant = Instant.now().minusSeconds(7 * 24 * 60 * 60) // Last 7 days
    ): Flow<Long?>
}
