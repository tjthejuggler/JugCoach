package com.example.jugcoach.data.query

import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.entity.Pattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Middleware layer that parses LLM commands and translates them into Room DAO calls.
 * This class handles the interpretation of structured queries and maintains context
 * for complex operations.
 */
class PatternQueryParser(private val patternDao: PatternDao) {

    /**
     * Parse and execute a search command with multiple criteria
     * Example: searchPatterns difficulty:>=5, balls:3, tags:["cascade", "syncopated"]
     */
    suspend fun parseSearchCommand(criteria: String, coachId: Long): Flow<List<Pattern>> = flow {
        val parsedCriteria = parseCriteriaString(criteria)
        val patterns = patternDao.getAllPatternsSync(coachId).filter { pattern ->
            parsedCriteria.all { (key, value) ->
                when (key) {
                    "difficulty" -> {
                        val op = value.substring(0, 2)
                        val threshold = value.substring(2).toIntOrNull() ?: return@all false
                        val patternDifficulty = pattern.difficulty?.toIntOrNull() ?: return@all false
                        when (op) {
                            ">=" -> patternDifficulty >= threshold
                            "<=" -> patternDifficulty <= threshold
                            else -> false
                        }
                    }
                    "balls" -> pattern.num == value
                    "tags" -> {
                        val requiredTags = value.trim('[', ']').split(",").map { it.trim('"') }
                        pattern.tags.containsAll(requiredTags)
                    }
                    else -> true
                }
            }
        }
        emit(patterns)
    }

    /**
     * Parse a criteria string into a map of key-value pairs
     * Example: "difficulty:>=5, balls:3" -> {"difficulty" to ">=5", "balls" to "3"}
     */
    private fun parseCriteriaString(criteria: String): Map<String, String> {
        return criteria.split(",")
            .map { it.trim() }
            .filter { it.contains(":") }
            .associate { criterion ->
                val (key, value) = criterion.split(":", limit = 2)
                key.trim() to value.trim()
            }
    }

    /**
     * Format a pattern into a JSON response suitable for LLM consumption
     */
    fun formatPatternResponse(pattern: Pattern, concise: Boolean = false): String {
        return if (concise) {
            JSONObject().apply {
                put("name", pattern.name)
                put("difficulty", pattern.difficulty)
                put("numberOfBalls", pattern.num)
                put("tags", pattern.tags)
                // Include record if available
                pattern.record?.let { record ->
                    put("record", JSONObject().apply {
                        put("catches", record.catches)
                        put("date", record.date)
                    })
                }
            }.toString(2)
        } else {
            JSONObject().apply {
                put("id", pattern.id)
                put("name", pattern.name)
                put("difficulty", pattern.difficulty)
                put("siteswap", pattern.siteswap)
                put("numberOfBalls", pattern.num)
                put("explanation", pattern.explanation)
                put("tags", pattern.tags)
                put("prerequisites", pattern.prerequisites)
                put("dependents", pattern.dependents)
                put("related", pattern.related)
                // Include URLs if available
                pattern.gifUrl?.let { put("gifUrl", it) }
                pattern.video?.let { put("videoUrl", it) }
                pattern.url?.let { put("externalUrl", it) }
                // Include record if available
                pattern.record?.let { record ->
                    put("record", JSONObject().apply {
                        put("catches", record.catches)
                        put("date", record.date)
                    })
                }
            }.toString(2)
        }
    }

    /**
     * Get patterns related to a specific pattern
     */
    suspend fun getRelatedPatterns(patternName: String, coachId: Long): List<Pattern> {
        // First try to find by name
        val allPatterns = patternDao.getAllPatternsSync(coachId)
        var pattern = allPatterns.find { it.name == patternName }
        
        // If not found by name, try to find by ID (as fallback)
        if (pattern == null) {
            pattern = patternDao.getPatternById(patternName, coachId)
        }
        
        if (pattern == null) return emptyList()
        
        val relatedIds = pattern.related + pattern.prerequisites + pattern.dependents
        return allPatterns.filter { it.id in relatedIds }
    }

    /**
     * Suggest patterns based on user's current skill level and practice history
     */
    fun suggestPatterns(coachId: Long, count: Int = 3): Flow<List<Pattern>> {
        return patternDao.suggestNextPattern(coachId).map { nextPattern ->
            nextPattern?.let { listOf(it) } ?: emptyList()
        }
    }

    /**
     * Get most practiced patterns with optional difficulty range
     */
    fun getMostPracticedPatterns(
        coachId: Long,
        minDifficulty: Int? = null,
        maxDifficulty: Int? = null,
        limit: Int = 5
    ): Flow<List<Pattern>> {
        return patternDao.getMostPracticedPatterns(coachId, limit).map { patterns ->
            patterns.filter { pattern ->
                val difficulty = pattern.difficulty?.toIntOrNull() ?: return@filter false
                (minDifficulty == null || difficulty >= minDifficulty) &&
                (maxDifficulty == null || difficulty <= maxDifficulty)
            }
        }
    }
}
