package com.example.jugcoach.data.dao

import androidx.room.*
import com.example.jugcoach.data.entity.Pattern
import kotlinx.coroutines.flow.Flow

@Dao
interface PatternDao {
    @Query("SELECT * FROM patterns")
    fun getAllPatterns(): Flow<List<Pattern>>

    @Query("""
        SELECT * FROM patterns
        WHERE name LIKE '%' || :query || '%'
        OR explanation LIKE '%' || :query || '%'
        OR tags LIKE '%' || :query || '%'
    """)
    fun searchPatterns(query: String): Flow<List<Pattern>>

    @Query("SELECT * FROM patterns WHERE id = :id")
    suspend fun getPatternById(id: String): Pattern?

    @Query("""
        SELECT * FROM patterns
        WHERE tags LIKE '%"' || :tag || '"%'
    """)
    fun getPatternsByTag(tag: String): Flow<List<Pattern>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: Pattern)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatterns(patterns: List<Pattern>)

    @Update
    suspend fun updatePattern(pattern: Pattern)

    @Delete
    suspend fun deletePattern(pattern: Pattern)

    @Query("DELETE FROM patterns")
    suspend fun deleteAllPatterns()

    @Query("SELECT * FROM patterns")
    suspend fun getAllPatternsSync(): List<Pattern>

    @Transaction
    @Query("""
        SELECT p.* FROM patterns p
        INNER JOIN sessions s ON p.id = s.patternId
        GROUP BY p.id
        ORDER BY COUNT(s.id) DESC
        LIMIT :limit
    """)
    fun getMostPracticedPatterns(limit: Int = 5): Flow<List<Pattern>>

    @Query("""
        SELECT * FROM patterns
        WHERE CAST(difficulty AS INTEGER) <= (
            SELECT MAX(CAST(difficulty AS INTEGER)) FROM patterns
            WHERE id IN (
                SELECT patternId FROM sessions
                WHERE catches >= 10
                GROUP BY patternId
                HAVING COUNT(*) >= 3
            )
        ) + 1
        AND id NOT IN (
            SELECT patternId FROM sessions
            GROUP BY patternId
        )
        ORDER BY CAST(difficulty AS INTEGER) ASC
        LIMIT 1
    """)
    fun suggestNextPattern(): Flow<Pattern?>
}
