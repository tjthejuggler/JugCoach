package com.example.jugcoach.data.importer

import android.content.Context
import android.net.Uri
import com.example.jugcoach.data.converter.PatternConverter
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.dto.PatternDTO
import com.example.jugcoach.data.dto.TricksWrapper
import com.example.jugcoach.data.entity.Pattern
import com.google.gson.Gson
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
    }

    private val gson = Gson()

    /**
     * Imports patterns from a JSON file selected by the user
     * @param uri The URI of the JSON file to import
     * @param coachId Optional coach ID. If null, patterns will be imported as shared patterns
     * @return Number of patterns imported
     */
    suspend fun importFromUri(
        uri: Uri,
        coachId: Long? = null,
        replaceExisting: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        var patternsImported = 0

        try {
            // Clear any existing name-ID mappings
            PatternConverter.clearNameIdMappings()
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                try {
                    // Try to parse as tricks wrapper first
                    val wrapper = gson.fromJson(content, TricksWrapper::class.java)
                    val patterns = wrapper.tricks.map { (key, dto) -> key to dto }
                    
                    // First, populate the name to ID map for all patterns
                    patterns.forEach { (key, dto) ->
                        PatternConverter.addNameIdMapping(dto.name, key)
                    }
                    
                    patterns.take(1).forEach { (key, dto) ->
                        android.util.Log.d("VideoTimeDebug", "Import - Key: $key, Name: ${dto.name}, Start: ${dto.videoStartTime}, End: ${dto.videoEndTime}")
                    }
                    
                    // Insert patterns in batches
                    patterns.chunked(BATCH_SIZE).forEach { batch ->
                        val entities = batch.map { (key, dto) ->
                            PatternConverter.toEntity(dto, key).copy(coachId = coachId)
                        }
                        entities.forEach { pattern ->
                            android.util.Log.d("VideoTimeDebug", "Before Insert - Pattern: ${pattern.name}, ID: ${pattern.id}, Start: ${pattern.videoStartTime}, End: ${pattern.videoEndTime}")
                            // Handle existing patterns based on replaceExisting parameter
                            val existingPattern = patternDao.getPatternById(pattern.id)
                            if (existingPattern != null) {
                                android.util.Log.d("VideoTimeDebug", "Found existing - Pattern: ${existingPattern.name}, ID: ${existingPattern.id}, Start: ${existingPattern.videoStartTime}, End: ${existingPattern.videoEndTime}")
                                if (replaceExisting) {
                                    patternDao.deletePattern(existingPattern)
                                    patternDao.insertPattern(pattern)
                                } else {
                                    android.util.Log.d("VideoTimeDebug", "Skipping existing pattern (replaceExisting=false)")
                                }
                            } else {
                                patternDao.insertPattern(pattern)
                            }
                            // Verify the insert
                            val verifyPattern = patternDao.getPatternById(pattern.id)
                            android.util.Log.d("VideoTimeDebug", "After Insert - Pattern: ${verifyPattern?.name}, ID: ${verifyPattern?.id}, Start: ${verifyPattern?.videoStartTime}, End: ${verifyPattern?.videoEndTime}")
                        }
                        patternsImported += batch.size
                    }
                } catch (e: Exception) {
                    // If tricks wrapper format fails, try array format
                    try {
                        val patterns = gson.fromJson(content, Array<PatternDTO>::class.java).toList()
                        
                        // First, populate the name to ID map for all patterns
                        patterns.forEach { dto ->
                            PatternConverter.addNameIdMapping(dto.name, dto.name) // In array format, name is used as ID
                        }
                        
                        patterns.take(1).forEach { dto ->
                            android.util.Log.d("VideoTimeDebug", "Import (Array) - Name: ${dto.name}, Start: ${dto.videoStartTime}, End: ${dto.videoEndTime}")
                        }
                        patterns.chunked(BATCH_SIZE).forEach { batch ->
                            val entities = batch.map { dto ->
                                // For array format, use name as ID since we don't have explicit keys
                                PatternConverter.toEntity(dto, dto.name).copy(coachId = coachId)
                            }
                            entities.forEach { pattern ->
                                android.util.Log.d("VideoTimeDebug", "Before Insert (Array) - Pattern: ${pattern.name}, ID: ${pattern.id}, Start: ${pattern.videoStartTime}, End: ${pattern.videoEndTime}")
                                // Handle existing patterns based on replaceExisting parameter
                                val existingPattern = patternDao.getPatternById(pattern.id)
                                if (existingPattern != null) {
                                    android.util.Log.d("VideoTimeDebug", "Found existing (Array) - Pattern: ${existingPattern.name}, ID: ${existingPattern.id}, Start: ${existingPattern.videoStartTime}, End: ${existingPattern.videoEndTime}")
                                    if (replaceExisting) {
                                        patternDao.deletePattern(existingPattern)
                                        patternDao.insertPattern(pattern)
                                    } else {
                                        android.util.Log.d("VideoTimeDebug", "Skipping existing pattern (replaceExisting=false)")
                                    }
                                } else {
                                    patternDao.insertPattern(pattern)
                                }
                                // Verify the insert
                                val verifyPattern = patternDao.getPatternById(pattern.id)
                                android.util.Log.d("VideoTimeDebug", "After Insert (Array) - Pattern: ${verifyPattern?.name}, ID: ${verifyPattern?.id}, Start: ${verifyPattern?.videoStartTime}, End: ${verifyPattern?.videoEndTime}")
                            }
                            patternsImported += batch.size
                        }
                    } catch (e2: Exception) {
                        throw ImportException("Invalid JSON format. File must be either a tricks wrapper or array format.", e2)
                    }
                }
            } ?: throw ImportException("Failed to open input stream")
        } catch (e: Exception) {
            when (e) {
                is ImportException -> throw e
                else -> throw ImportException("Failed to import patterns: ${e.message}", e)
            }
        }

        patternsImported
    }

    class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
