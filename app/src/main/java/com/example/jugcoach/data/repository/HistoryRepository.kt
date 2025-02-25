package com.example.jugcoach.data.repository

import androidx.lifecycle.LiveData
import com.example.jugcoach.data.dao.HistoryEntryDao
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.entity.HistoryEntry
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.entity.Run
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val historyEntryDao: HistoryEntryDao,
    private val patternDao: PatternDao
) {
    // Get all history entries sorted by timestamp (newest first)
    fun getAllHistoryEntries(): LiveData<List<HistoryEntry>> {
        return historyEntryDao.getAllHistoryEntries()
    }

    // Get recent history entries (limited number)
    fun getRecentHistoryEntries(limit: Int = 20): LiveData<List<HistoryEntry>> {
        return historyEntryDao.getRecentHistoryEntries(limit)
    }
    
    // Get the total count of history entries
    suspend fun getHistoryEntryCount(): Int {
        return withContext(Dispatchers.IO) {
            historyEntryDao.getHistoryEntryCount()
        }
    }

    // Add a new history entry
    suspend fun addHistoryEntry(entry: HistoryEntry): Long {
        return withContext(Dispatchers.IO) {
            historyEntryDao.insert(entry)
        }
    }

    // Log when a run is added to a pattern
    suspend fun logRunAdded(patternId: String, patternName: String, run: Run, isFromUser: Boolean) {
        val catches = run.catches?.toString() ?: "unknown"
        val description = "Added a run of $catches catches for pattern: $patternName"
        
        val entry = HistoryEntry(
            type = HistoryEntry.TYPE_RUN_ADDED,
            description = description,
            relatedPatternId = patternId,
            isFromUser = isFromUser
        )
        
        addHistoryEntry(entry)
    }

    // Log when a pattern is created
    suspend fun logPatternCreated(patternId: String, patternName: String, isFromUser: Boolean) {
        val description = "Created new pattern: $patternName"
        
        val entry = HistoryEntry(
            type = HistoryEntry.TYPE_PATTERN_CREATED,
            description = description,
            relatedPatternId = patternId,
            isFromUser = isFromUser
        )
        
        addHistoryEntry(entry)
    }

    // Log when a coach is created
    suspend fun logCoachCreated(coachId: Long, coachName: String, isFromUser: Boolean) {
        val description = "Created new coach: $coachName"
        
        val entry = HistoryEntry(
            type = HistoryEntry.TYPE_COACH_CREATED,
            description = description,
            relatedCoachId = coachId,
            isFromUser = isFromUser
        )
        
        addHistoryEntry(entry)
    }

    /**
     * Direct implementation to extract runs from the database
     * This bypasses the entity model and directly works with the database raw data
     */
    private suspend fun extractRunsDirectlyFromDatabase(): List<HistoryEntry> {
        return withContext(Dispatchers.IO) {
            val historyEntries = mutableListOf<HistoryEntry>()
            val gson = com.google.gson.Gson()
            
            try {
                android.util.Log.d("DEBUG_HISTORY", "Attempting direct database access through DAO")
                
                // Get patterns with run history directly using our DAO
                val patternsWithRuns = patternDao.getPatternsWithRunsRaw()
                
                android.util.Log.d("DEBUG_HISTORY", "Direct query returned ${patternsWithRuns.size} patterns with runs")
                
                // Process each pattern with runs
                for (patternData in patternsWithRuns) {
                    val id = patternData.id
                    val name = patternData.name
                    val runsJson = patternData.history_runs
                    val coachId = patternData.coachId
                    
                    android.util.Log.d("DEBUG_HISTORY", "Pattern $name has runs JSON: $runsJson, coachId: $coachId")
                    
                    try {
                        // Parse the JSON array of runs
                        val jsonArray = gson.fromJson(runsJson, com.google.gson.JsonArray::class.java)
                        
                        // Track created entries for this pattern
                        var entriesForPattern = 0
                        
                        for (i in 0 until jsonArray.size()) {
                            val runJson = jsonArray.get(i).asJsonObject
                            
                            // Extract run data
                            val catches = if (runJson.has("catches") && !runJson.get("catches").isJsonNull) {
                                runJson.get("catches").asInt.toString()
                            } else {
                                "unknown"
                            }
                            
                            val date = if (runJson.has("date") && !runJson.get("date").isJsonNull) {
                                runJson.get("date").asLong
                            } else {
                                System.currentTimeMillis()
                            }
                            
                            // Check if the run has a fromUser field
                            val isFromUser = if (runJson.has("isFromUser")) {
                                runJson.get("isFromUser").asBoolean
                            } else {
                                true // Default to true if not specified
                            }
                            
                            val description = "Added a run of $catches catches for pattern: $name"
                            
                            // Create history entry
                            val entry = HistoryEntry(
                                type = HistoryEntry.TYPE_RUN_ADDED,
                                timestamp = date,
                                description = description,
                                relatedPatternId = id,
                                relatedCoachId = if (!isFromUser) coachId else null, // Associate with coach if not from user
                                isFromUser = isFromUser
                            )
                            
                            historyEntries.add(entry)
                            entriesForPattern++
                        }
                        
                        android.util.Log.d("DEBUG_HISTORY", "Created $entriesForPattern entries for pattern $name")
                    } catch (e: Exception) {
                        android.util.Log.e("DEBUG_HISTORY", "Error parsing runs for pattern $name: ${e.message}", e)
                    }
                }
                
                android.util.Log.d("DEBUG_HISTORY", "Direct database extraction created ${historyEntries.size} entries")
                
                historyEntries
            } catch (e: Exception) {
                android.util.Log.e("DEBUG_HISTORY", "Error in direct database extraction", e)
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    // Generate history entries for all existing runs (used in developer mode)
    // Returns whether direct database access was used
    suspend fun generateHistoryEntriesForExistingRuns(): Boolean {
        return withContext(Dispatchers.IO) {
            var usedDirectAccess = false
            try {
                // Use DEBUG_HISTORY tag for all logs for easy filtering
                android.util.Log.d("DEBUG_HISTORY", "Starting history entry generation")
                
                // First get all patterns (passing -1 to get patterns from all coaches)
                val patterns = patternDao.getAllPatternsSync(-1)
                android.util.Log.d("DEBUG_HISTORY", "Found ${patterns.size} patterns to process")
                
                // DEBUG: Dump detailed pattern information to see what we're getting
                patterns.forEachIndexed { index, pattern ->
                    android.util.Log.d("DEBUG_HISTORY", "Pattern[$index]: id=${pattern.id}, name=${pattern.name}")
                    
                    // Examine runHistory field
                    val runHistoryString = com.google.gson.Gson().toJson(pattern.runHistory)
                    android.util.Log.d("DEBUG_HISTORY", "RunHistory for ${pattern.name}: $runHistoryString")
                    
                    // Directly examine runs list
                    val runsSize = pattern.runHistory.runs.size
                    android.util.Log.d("DEBUG_HISTORY", "Runs size for ${pattern.name}: $runsSize")
                    
                    // Dump each run if any exist
                    if (runsSize > 0) {
                        pattern.runHistory.runs.forEachIndexed { runIndex, run ->
                            android.util.Log.d("DEBUG_HISTORY", "  Run[$runIndex]: catches=${run.catches}, date=${run.date}")
                        }
                    }
                }
                
                // Direct database query to check for patterns with runs
                val patternsWithRunsCount = patternDao.countPatternsWithRuns()
                android.util.Log.d("DEBUG_HISTORY", "Direct DB query shows $patternsWithRunsCount patterns with runs")
                
                // Count patterns with runs based on our loaded objects
                val patternsWithRuns = patterns.count { it.runHistory.runs.isNotEmpty() }
                android.util.Log.d("DEBUG_HISTORY", "Loaded objects show $patternsWithRuns patterns have run history")
                
                // Create history entries based on pattern objects
                var historyEntries = patterns.flatMap { pattern ->
                    pattern.runHistory.runs.map { run ->
                        val catches = run.catches?.toString() ?: "unknown"
                        val description = "Added a run of $catches catches for pattern: ${pattern.name}"
                        
                        // Create entry with proper coach association
                        // Note: Run class doesn't have isFromUser field, so we assume all runs are from the user
                        HistoryEntry(
                            type = HistoryEntry.TYPE_RUN_ADDED,
                            timestamp = run.date,
                            description = description,
                            relatedPatternId = pattern.id,
                            relatedCoachId = null, // Assume user runs have no coach association
                            isFromUser = true      // Assume all existing runs are from the user
                        )
                    }
                }
                
                // If no entries found through the entity model, try direct database extraction
                if (historyEntries.isEmpty() && patternsWithRunsCount > 0) {
                    android.util.Log.d("DEBUG_HISTORY", "No entries found through entity model but database shows runs. Trying direct extraction...")
                    historyEntries = extractRunsDirectlyFromDatabase()
                    usedDirectAccess = true
                    android.util.Log.d("DEBUG_HISTORY", "Used direct database access: $usedDirectAccess")
                }
                
                android.util.Log.d("DEBUG_HISTORY", "Created ${historyEntries.size} history entries to insert")
                
                // Check if we have anything to insert
                if (historyEntries.isEmpty()) {
                    android.util.Log.w("DEBUG_HISTORY", "No history entries to insert - no runs found in any patterns")
                    return@withContext false
                }
                
                // Delete existing TYPE_RUN_ADDED entries to prevent duplicates
                val entriesBeforeCount = historyEntryDao.getHistoryEntryCount()
                historyEntryDao.deleteEntriesByType(HistoryEntry.TYPE_RUN_ADDED)
                
                // Insert all created entries
                historyEntryDao.generateEntriesForExistingRuns(historyEntries)
                
                // Verify the entries were added
                val entriesAfterCount = historyEntryDao.getHistoryEntryCount()
                android.util.Log.d("DEBUG_HISTORY", "Entries before: $entriesBeforeCount, Entries after: $entriesAfterCount")
                
                usedDirectAccess
            } catch (e: Exception) {
                // Log any errors with full stack trace
                android.util.Log.e("DEBUG_HISTORY", "Error generating history entries", e)
                e.printStackTrace()
                false
            }
        }
    }
}