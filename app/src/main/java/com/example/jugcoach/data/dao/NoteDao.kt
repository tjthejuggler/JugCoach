package com.example.jugcoach.data.dao

import androidx.room.*
import com.example.jugcoach.data.entity.Note
import com.example.jugcoach.data.entity.NoteType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE coachId = :coachId ORDER BY createdAt DESC")
    fun getAllNotes(coachId: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id AND coachId = :coachId")
    suspend fun getNoteById(id: Long, coachId: Long): Note?

    @Query("SELECT * FROM notes WHERE type = :type AND coachId = :coachId ORDER BY createdAt DESC")
    fun getNotesByType(type: NoteType, coachId: Long): Flow<List<Note>>

    @Query("""
        SELECT * FROM notes 
        WHERE relatedPatternId = :patternId 
        AND coachId = :coachId
        ORDER BY createdAt DESC
    """)
    fun getNotesForPattern(patternId: String, coachId: Long): Flow<List<Note>>

    @Query("""
        SELECT * FROM notes 
        WHERE relatedSessionId = :sessionId 
        AND coachId = :coachId
        ORDER BY createdAt DESC
    """)
    fun getNotesForSession(sessionId: Long, coachId: Long): Flow<List<Note>>

    @Query("""
        SELECT * FROM notes 
        WHERE type = :type 
        AND coachId = :coachId
        AND createdAt >= :since 
        ORDER BY createdAt DESC
    """)
    fun getRecentNotes(
        type: NoteType,
        coachId: Long,
        since: Instant = Instant.now().minusSeconds(7 * 24 * 60 * 60) // Last 7 days
    ): Flow<List<Note>>

    @Query("""
        SELECT n.* FROM notes n
        JOIN json_each(json_array(n.tags)) t
        WHERE t.value = :tag
        AND n.coachId = :coachId
        ORDER BY n.createdAt DESC
    """)
    fun getNotesByTag(tag: String, coachId: Long): Flow<List<Note>>

    @Insert
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("""
        SELECT n.* FROM notes n
        JOIN json_each(json_array(n.tags)) t
        WHERE n.type = 'TAG_DEFINITION'
        AND n.coachId = :coachId
        AND t.value = :tag
        ORDER BY n.updatedAt DESC
        LIMIT 1
    """)
    suspend fun getTagDefinition(tag: String, coachId: Long): Note?

    @Query("""
        SELECT DISTINCT t.value
        FROM notes n
        CROSS JOIN json_each(json_array(n.tags)) as t
        WHERE n.coachId = :coachId
        ORDER BY t.value
    """)
    fun getAllTags(coachId: Long): Flow<List<String>>
}
