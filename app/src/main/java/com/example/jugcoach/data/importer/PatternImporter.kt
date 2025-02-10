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
                        if (replaceExisting) {
                            patternDao.insertPatterns(entities)
                        } else {
                            // Only insert patterns that don't exist
                            entities.forEach { pattern ->
                                if (patternDao.getPatternById(pattern.id, coachId ?: -1) == null) {
                                    patternDao.insertPattern(pattern)
                                }
                            }
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
                        if (replaceExisting) {
                            patternDao.insertPatterns(entities)
                        } else {
                            entities.forEach { pattern ->
                                if (patternDao.getPatternById(pattern.id, coachId ?: -1) == null) {
                                    patternDao.insertPattern(pattern)
                                }
                            }
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
        // Clear existing patterns before import
        patternDao.deleteAllPatterns()
        var patternsImported = 0

        try {
            context.assets.open(ASSET_FILE_PATH).use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = StringBuilder()
                var line: String?
                var openBrackets = 0
                var isInArray = false
                val patterns = mutableListOf<PatternDTO>()

                // Process the file character by character to handle large JSON
                while (reader.read().also { char: Int -> line = char.toChar().toString() } != -1) {
                    when (line) {
                        "[" -> {
                            isInArray = true
                            continue
                        }
                        "]" -> {
                            // Process any remaining content
                            if (content.isNotEmpty()) {
                                processPattern(content.toString(), patterns)
                            }
                            break
                        }
                        "{" -> openBrackets++
                        "}" -> {
                            openBrackets--
                            if (openBrackets == 0 && isInArray) {
                                content.append(line)
                                // Process complete pattern object
                                processPattern(content.toString(), patterns)
                                content.clear()

                                // Batch insert if we've reached the batch size
                                if (patterns.size >= BATCH_SIZE) {
                                    patternDao.insertPatterns(patterns.map { dto ->
                                        PatternConverter.toEntity(dto).copy(coachId = null) // Import as shared patterns
                                    })
                                    patternsImported += patterns.size
                                    patterns.clear()
                                }
                                continue
                            }
                        }
                    }
                    content.append(line)
                }

                // Insert any remaining patterns
                if (patterns.isNotEmpty()) {
                    patternDao.insertPatterns(patterns.map { dto ->
                        PatternConverter.toEntity(dto).copy(coachId = null) // Import as shared patterns
                    })
                    patternsImported += patterns.size
                }
            }
        } catch (e: Exception) {
            throw ImportException("Failed to import patterns: ${e.message}", e)
        }

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
