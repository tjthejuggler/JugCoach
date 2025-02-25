package com.example.jugcoach.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.jugcoach.data.entity.HistoryEntry

@Dao
interface HistoryEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<HistoryEntry>)

    @Query("SELECT * FROM history_entries ORDER BY timestamp DESC")
    fun getAllHistoryEntries(): LiveData<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistoryEntries(limit: Int): LiveData<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries WHERE relatedPatternId = :patternId ORDER BY timestamp DESC")
    fun getHistoryEntriesForPattern(patternId: String): LiveData<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries WHERE relatedCoachId = :coachId ORDER BY timestamp DESC")
    fun getHistoryEntriesForCoach(coachId: Long): LiveData<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries WHERE type = :type ORDER BY timestamp DESC")
    fun getHistoryEntriesByType(type: String): LiveData<List<HistoryEntry>>

    @Query("DELETE FROM history_entries")
    suspend fun deleteAllHistoryEntries()
    
    @Query("DELETE FROM history_entries WHERE type = :type")
    suspend fun deleteEntriesByType(type: String)

    @Query("SELECT COUNT(*) FROM history_entries")
    suspend fun getHistoryEntryCount(): Int
    
    // This method will be used to create entries for existing runs
    @Transaction
    suspend fun generateEntriesForExistingRuns(entries: List<HistoryEntry>) {
        insertAll(entries)
    }
}