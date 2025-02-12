package com.example.jugcoach.data.service

import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.query.PatternQueryParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service class that handles pattern database operations and provides a high-level
 * interface for LLM coaches to query and interact with the pattern database.
 */
@Singleton
class PatternDatabaseService @Inject constructor(
    private val patternDao: PatternDao
) {
    private val queryParser = PatternQueryParser(patternDao)

    /**
     * Look up a specific pattern by name
     * Command: lookupPattern <pattern_name>
     * Example: lookupPattern 55500
     */
    suspend fun lookupPattern(patternName: String, coachId: Long): String {
        // First try to find by name
        var pattern = patternDao.getAllPatternsSync(coachId).find { it.name == patternName }
        
        // If not found by name, try to find by ID (as fallback)
        if (pattern == null) {
            pattern = patternDao.getPatternById(patternName, coachId)
        }
        
        return pattern?.let { queryParser.formatPatternResponse(it) }
            ?: """{"error": "Pattern not found with name: $patternName"}"""
    }

    /**
     * Search patterns using multiple criteria
     * Command: searchPatterns <criteria>
     * Example: searchPatterns difficulty:>=5, balls:3, tags:["cascade", "syncopated"]
     */
    suspend fun searchPatterns(criteria: String, coachId: Long): String {
        val patterns = queryParser.parseSearchCommand(criteria, coachId).first()
        return if (patterns.isEmpty()) {
            """{"results": [], "message": "No patterns found matching the criteria"}"""
        } else {
            buildJsonArray("results") {
                patterns.forEach { pattern ->
                    append(queryParser.formatPatternResponse(pattern))
                }
            }
        }
    }

    /**
     * Get patterns related to a specific pattern
     * Command: getRelatedPatterns <pattern_name>
     * Example: getRelatedPatterns 55500
     */
    suspend fun getRelatedPatterns(patternId: String, coachId: Long): String {
        val patterns = queryParser.getRelatedPatterns(patternId, coachId)
        return if (patterns.isEmpty()) {
            """{"results": [], "message": "No related patterns found"}"""
        } else {
            buildJsonArray("results") {
                patterns.forEach { pattern ->
                    append(queryParser.formatPatternResponse(pattern))
                }
            }
        }
    }

    /**
     * Get pattern suggestions based on user's skill level and practice history
     * Command: suggestPatterns [count]
     */
    suspend fun suggestPatterns(coachId: Long, count: Int = 3): String {
        val patterns = queryParser.suggestPatterns(coachId, count).first()
        return if (patterns.isEmpty()) {
            """{"results": [], "message": "No pattern suggestions available"}"""
        } else {
            buildJsonArray("results") {
                patterns.forEach { pattern ->
                    append(queryParser.formatPatternResponse(pattern))
                }
            }
        }
    }

    /**
     * Get most practiced patterns within an optional difficulty range
     * Command: getMostPracticedPatterns [minDifficulty] [maxDifficulty] [limit]
     */
    suspend fun getMostPracticedPatterns(
        coachId: Long,
        minDifficulty: Int? = null,
        maxDifficulty: Int? = null,
        limit: Int = 5
    ): String {
        val patterns = queryParser.getMostPracticedPatterns(
            coachId,
            minDifficulty,
            maxDifficulty,
            limit
        ).first()

        return if (patterns.isEmpty()) {
            """{"results": [], "message": "No practice history found"}"""
        } else {
            buildJsonArray("results") {
                patterns.forEach { pattern ->
                    append(queryParser.formatPatternResponse(pattern))
                }
            }
        }
    }

    /**
     * Helper function to build JSON arrays with proper formatting
     */
    private fun buildJsonArray(
        arrayName: String,
        builder: StringBuilder.() -> Unit
    ): String {
        return buildString {
            append("""{"$arrayName": [""")
            val content = StringBuilder().apply(builder).toString()
            if (content.isNotEmpty()) {
                append(content.removeSuffix(","))
            }
            append("]}")
        }
    }

    private fun StringBuilder.append(jsonString: String) {
        append(jsonString)
        append(",")
    }
}
