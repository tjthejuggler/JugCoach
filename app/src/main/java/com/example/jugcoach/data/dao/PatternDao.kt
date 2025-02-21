package com.example.jugcoach.data.dao

import androidx.room.*
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.entity.Run
import com.example.jugcoach.data.entity.Record
import kotlinx.coroutines.flow.Flow

@Dao
interface PatternDao {
    @Query("""
        SELECT p.* FROM patterns p
        WHERE p.id IN (
            SELECT value FROM json_each(
                (SELECT prerequisites FROM patterns WHERE id = :patternId)
            )
        )
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomPrerequisite(patternId: String): Pattern?

    @Query("""
        SELECT p.* FROM patterns p
        WHERE p.id IN (
            SELECT value FROM json_each(
                (SELECT related FROM patterns WHERE id = :patternId)
            )
        )
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomRelated(patternId: String): Pattern?

    @Query("""
        SELECT p.* FROM patterns p
        WHERE p.id IN (
            SELECT value FROM json_each(
                (SELECT dependents FROM patterns WHERE id = :patternId)
            )
        )
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomDependent(patternId: String): Pattern?

    @Query("""
        SELECT CASE
            WHEN json_array_length(prerequisites) > 0 THEN 1
            ELSE 0
        END
        FROM patterns
        WHERE id = :patternId
    """)
    suspend fun hasPrerequisites(patternId: String): Boolean

    @Query("""
        SELECT CASE
            WHEN json_array_length(related) > 0 THEN 1
            ELSE 0
        END
        FROM patterns
        WHERE id = :patternId
    """)
    suspend fun hasRelated(patternId: String): Boolean

    @Query("""
        SELECT CASE
            WHEN json_array_length(dependents) > 0 THEN 1
            ELSE 0
        END
        FROM patterns
        WHERE id = :patternId
    """)
    suspend fun hasDependents(patternId: String): Boolean

    @Query("""
        SELECT * FROM patterns 
        WHERE coachId = :coachId OR coachId IS NULL
        ORDER BY name ASC
    """)
    fun getAllPatterns(coachId: Long): Flow<List<Pattern>>

    @Query("SELECT * FROM patterns WHERE coachId IS NULL")
    fun getSharedPatterns(): Flow<List<Pattern>>

    @Query("SELECT * FROM patterns WHERE coachId = :coachId")
    fun getCoachPatterns(coachId: Long): Flow<List<Pattern>>

    @Query("SELECT COUNT(*) FROM patterns WHERE coachId = :coachId OR coachId IS NULL")
    fun getCount(coachId: Long): Flow<Int>

    @Query("""
        SELECT *, 
        CASE 
            WHEN name = :query THEN 100
            WHEN name LIKE :query || '%' THEN 90
            WHEN name LIKE '%' || :query || '%' THEN 80
            WHEN name LIKE '%(' || :query || ')%' THEN 80  -- Match exact number in parentheses
            WHEN LOWER(name) LIKE '%' || LOWER(:query) || '%' THEN 70
            WHEN explanation LIKE '%' || :query || '%' THEN 60
            WHEN LOWER(explanation) LIKE '%' || LOWER(:query) || '%' THEN 50
            WHEN tags LIKE '%' || :query || '%' THEN 40
            WHEN LOWER(tags) LIKE '%' || LOWER(:query) || '%' THEN 30
            ELSE 0
        END as relevance
        FROM patterns 
        WHERE (coachId = :coachId OR coachId IS NULL)
        AND (
            name LIKE '%' || :query || '%'
            OR name LIKE '%' || REPLACE(:query, ' ', '%') || '%'
            OR name LIKE '%(' || :query || ')%'  -- Match exact number in parentheses
            OR LOWER(name) LIKE '%' || LOWER(:query) || '%'
            OR explanation LIKE '%' || :query || '%'
            OR LOWER(explanation) LIKE '%' || LOWER(:query) || '%'
            OR tags LIKE '%' || :query || '%'
            OR LOWER(tags) LIKE '%' || LOWER(:query) || '%'
        )
        ORDER BY relevance DESC
    """)
    fun searchPatterns(query: String, coachId: Long): Flow<List<Pattern>>

    @Query("SELECT * FROM patterns WHERE id = :id AND (coachId = :coachId OR coachId IS NULL)")
    suspend fun getPatternById(id: String, coachId: Long): Pattern?

    @Query("""
        SELECT * FROM patterns
        WHERE tags LIKE '%"' || :tag || '"%'
        AND (coachId = :coachId OR coachId IS NULL)
    """)
    fun getPatternsByTag(tag: String, coachId: Long): Flow<List<Pattern>>

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
    suspend fun getAllPatterns(): List<Pattern>

    @Query("SELECT * FROM patterns WHERE coachId = :coachId OR coachId IS NULL")
    suspend fun getAllPatternsSync(coachId: Long): List<Pattern>

    @Query("""
        SELECT * FROM patterns
        WHERE name = :name
        AND (coachId = :coachId OR coachId IS NULL)
    """)
    suspend fun getPatternsByName(name: String, coachId: Long): List<Pattern>

    @Transaction
    @Query("""
        SELECT p.* FROM patterns p
        INNER JOIN sessions s ON p.id = s.patternId
        WHERE (p.coachId = :coachId OR p.coachId IS NULL)
        GROUP BY p.id
        ORDER BY COUNT(s.id) DESC
        LIMIT :limit
    """)
    fun getMostPracticedPatterns(coachId: Long, limit: Int = 5): Flow<List<Pattern>>

    @Query("""
        SELECT * FROM patterns
        WHERE (coachId = :coachId OR coachId IS NULL)
        AND CAST(difficulty AS INTEGER) <= (
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
    fun suggestNextPattern(coachId: Long): Flow<Pattern?>

    @Query("UPDATE patterns SET catchesPerMinute = :rate WHERE id = :patternId")
    suspend fun updateCatchesPerMinute(patternId: String, rate: Double?)

    @Query("SELECT * FROM patterns WHERE id = :patternId")
    suspend fun getPattern(patternId: String): Pattern?

    @Transaction
    suspend fun addRun(patternId: String, catches: Int?, duration: Long?, cleanEnd: Boolean, date: Long) {
        val pattern = getPatternById(patternId, -1) ?: return // -1 to get any pattern regardless of coach
        val catchesPerMinute = com.example.jugcoach.util.RunUtils.calculateCatchesPerMinute(catches, duration)
        val newRun = Run(
            catches = catches,
            duration = duration,
            catchesPerMinute = catchesPerMinute,
            isCleanEnd = cleanEnd,
            date = date
        )
        
        // Create new pattern with updated run history
        val updatedPattern = pattern.copy(
            runHistory = pattern.runHistory.copy(
                runs = pattern.runHistory.runs + newRun
            ),
            // Update record if this run has more catches than current record
            record = if (catches != null && (pattern.record == null || catches > pattern.record.catches)) {
                Record(catches = catches, date = date)
            } else pattern.record
        )
        
        updatePattern(updatedPattern)
    }
}
