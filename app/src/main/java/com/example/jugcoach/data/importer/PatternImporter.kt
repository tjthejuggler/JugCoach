package com.example.jugcoach.data.importer

import android.content.Context
import android.net.Uri
import com.example.jugcoach.data.converter.PatternConverter
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.dto.PatternDTO
import com.example.jugcoach.data.entity.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Handles importing legacy pattern data from JSON assets
 */
class PatternImporter(
    private val context: Context,
    private val patternDao: PatternDao
) {
    companion object {
        private const val BATCH_SIZE = 50
        private const val ASSET_FILE_PATH = "juggling_data.json"
        private const val TAG = "PatternImporter"
    }

    /**
     * Imports patterns from a JSON file
     * @param uri The URI of the JSON file to import
     * @param coachId Optional coach ID. If null, patterns will be imported as shared patterns
     * @param replaceExisting If true, will replace existing patterns with the same ID
     * @return Number of patterns imported
     */
    suspend fun importFromUri(
        uri: Uri,
        coachId: Long? = null,
        replaceExisting: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        var patternsImported = 0

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                try {
                    // Try to parse as tricks wrapper first
                    val patterns = PatternConverter.fromJsonTricksWrapper(content)
                    
                    // Insert patterns in batches
                    patterns.chunked(BATCH_SIZE).forEach { batch ->
                        val entities = batch.map { dto ->
                            PatternConverter.toEntity(dto).copy(coachId = coachId)
                        }
                        entities.forEach { pattern ->
                            // Delete any existing patterns with the same name
                            patternDao.getPatternsByName(pattern.name, coachId ?: -1).forEach { existingPattern ->
                                patternDao.deletePattern(existingPattern)
                            }
                            patternDao.insertPattern(pattern)
                        }
                        patternsImported += batch.size
                    }
                } catch (e: Exception) {
                    // If that fails, try parsing as array
                    val patterns = PatternConverter.fromJsonArray(content)
                    patterns.chunked(BATCH_SIZE).forEach { batch ->
                        val entities = batch.map { dto ->
                            PatternConverter.toEntity(dto).copy(coachId = coachId)
                        }
                        entities.forEach { pattern ->
                            // Delete any existing patterns with the same name
                            patternDao.getPatternsByName(pattern.name, coachId ?: -1).forEach { existingPattern ->
                                patternDao.deletePattern(existingPattern)
                            }
                            patternDao.insertPattern(pattern)
                        }
                        patternsImported += batch.size
                    }
                }
            } ?: throw ImportException("Failed to open input stream")
        } catch (e: Exception) {
            throw ImportException("Failed to import patterns: ${e.message}", e)
        }

        patternsImported
    }

    /**
     * Imports patterns from the legacy JSON file in assets as shared patterns
     * @return Number of patterns imported
     */

    suspend fun importLegacyPatterns(): Int = withContext(Dispatchers.IO) {
        android.util.Log.d(TAG, "Starting legacy pattern import")
        
        // Clear existing patterns before import
        android.util.Log.d(TAG, "Clearing existing patterns")
        patternDao.deleteAllPatterns()
        var patternsImported = 0

        try {
            // First read the entire file to see what we're working with
            val fullContent = context.assets.open(ASSET_FILE_PATH).use { inputStream ->
                inputStream.bufferedReader().readText()
            }
            android.util.Log.d(TAG, "Read ${fullContent.length} bytes from $ASSET_FILE_PATH")

            // Try parsing as tricks wrapper first
            try {
                android.util.Log.d(TAG, "Attempting to parse as tricks wrapper")
                val patterns = PatternConverter.fromJsonTricksWrapper(fullContent)
                android.util.Log.d(TAG, "Successfully parsed ${patterns.size} patterns from tricks wrapper")
                
                // Insert patterns in batches
                patterns.chunked(BATCH_SIZE).forEach { batch ->
                    val entities = batch.map { dto ->
                        PatternConverter.toEntity(dto).copy(coachId = null)
                    }
                    android.util.Log.d(TAG, "Inserting batch of ${entities.size} patterns")
                    android.util.Log.d(TAG, "First pattern in batch: id=${entities.first().id}, name=${entities.first().name}")
                    patternDao.insertPatterns(entities)
                    patternsImported += batch.size
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to parse as tricks wrapper, trying array format", e)
                // If that fails, try parsing as array
                val patterns = PatternConverter.fromJsonArray(fullContent)
                android.util.Log.d(TAG, "Successfully parsed ${patterns.size} patterns from array")
                
                patterns.chunked(BATCH_SIZE).forEach { batch ->
                    val entities = batch.map { dto ->
                        PatternConverter.toEntity(dto).copy(coachId = null)
                    }
                    android.util.Log.d(TAG, "Inserting batch of ${entities.size} patterns")
                    android.util.Log.d(TAG, "First pattern in batch: id=${entities.first().id}, name=${entities.first().name}")
                    patternDao.insertPatterns(entities)
                    patternsImported += batch.size
                }
            }

            // Verify imported patterns
            val allPatterns = patternDao.getAllPatternsSync(-1)
            android.util.Log.d(TAG, "Verification: Found ${allPatterns.size} patterns in database")
            allPatterns.take(5).forEach { pattern ->
                android.util.Log.d(TAG, "Sample pattern: id=${pattern.id}, name=${pattern.name}")
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to import patterns", e)
            throw ImportException("Failed to import patterns: ${e.message}", e)
        }

        android.util.Log.d(TAG, "Import completed. Total patterns imported: $patternsImported")
        patternsImported
    }

    private fun processPattern(json: String, patterns: MutableList<PatternDTO>) {
        try {
            patterns.add(PatternConverter.fromJson(json))
        } catch (e: Exception) {
            // Log error but continue processing
            e.printStackTrace()
        }
    }

    class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
