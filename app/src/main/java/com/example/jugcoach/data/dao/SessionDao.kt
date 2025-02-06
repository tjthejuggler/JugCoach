package com.example.jugcoach.data.dao

import androidx.room.*
import com.example.jugcoach.data.entity.Session
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): Session?

    @Query("SELECT * FROM sessions WHERE patternId = :patternId ORDER BY startTime DESC")
    fun getSessionsForPattern(patternId: String): Flow<List<Session>>

    @Insert
    suspend fun insertSession(session: Session): Long

    @Update
    suspend fun updateSession(session: Session)

    @Delete
    suspend fun deleteSession(session: Session)

    @Query("SELECT * FROM sessions WHERE endTime IS NULL")
    suspend fun getActiveSession(): Session?

    @Query("""
        SELECT AVG(catches) FROM sessions 
        WHERE patternId = :patternId 
        AND startTime >= :since
    """)
    fun getAverageCatchesForPattern(
        patternId: String,
        since: Instant = Instant.now().minusSeconds(30 * 24 * 60 * 60) // Last 30 days
    ): Flow<Float?>

    @Query("""
        SELECT * FROM sessions
        WHERE patternId = :patternId
        AND catches = (
            SELECT MAX(catches)
            FROM sessions
            WHERE patternId = :patternId
        )
        LIMIT 1
    """)
    suspend fun getBestSessionForPattern(patternId: String): Session?

    @Query("""
        SELECT COUNT(*) FROM sessions
        WHERE startTime >= :since
        AND endTime IS NOT NULL
    """)
    fun getCompletedSessionCount(
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
        WHERE startTime >= :since
    """)
    fun getTotalPracticeTime(
        since: Instant = Instant.now().minusSeconds(7 * 24 * 60 * 60) // Last 7 days
    ): Flow<Long?>
}
