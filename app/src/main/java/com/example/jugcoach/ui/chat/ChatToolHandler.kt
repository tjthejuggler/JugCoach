package com.example.jugcoach.ui.chat

import android.util.Log
import com.example.jugcoach.data.api.AnthropicResponse
import com.example.jugcoach.data.query.PatternQueryParser
import com.example.jugcoach.data.dao.PatternDao
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

private const val TAG = "ChatToolHandler"

class ChatToolHandler @Inject constructor(
    private val patternDao: PatternDao,
    private val gson: Gson
) {
    private val queryParser = PatternQueryParser(patternDao)

    fun extractJsonFromText(text: String): String? {
        val jsonRegex = "\\{.*\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
        val matchResult = jsonRegex.find(text)
        return matchResult?.value
    }

    fun extractToolCallsFromText(text: String): List<AnthropicResponse.ToolCall>? {
        try {
            Log.d(TAG, "Starting tool call extraction from text")
            
            val jsonText = if (text.trim().startsWith("{") && text.trim().endsWith("}")) {
                text
            } else {
                extractJsonFromText(text) ?: return null
            }

            try {
                val jsonObject = JsonParser.parseString(jsonText).asJsonObject
                if (jsonObject.has("tool") && jsonObject.has("arguments")) {
                    return listOf(
                        AnthropicResponse.ToolCall(
                            id = UUID.randomUUID().toString(),
                            type = "function",
                            name = jsonObject.get("tool").asString,
                            arguments = jsonObject.get("arguments").toString()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Not a single tool call JSON: ${e.message}")
            }

            val pattern = """\{(?:[^{}]|"[^"]*")*"tool"\s*:\s*"[^"]*"(?:[^{}]|"[^"]*")*"arguments"\s*:\s*\{[^{}]*\}(?:[^{}]|"[^"]*")*\}""".toRegex()
            val matches = pattern.findAll(jsonText)
            
            val toolCalls = matches.mapNotNull { match ->
                try {
                    val json = match.value
                    Log.d(TAG, "Found potential tool call JSON: $json")
                    
                    val jsonObject = gson.fromJson(json, Map::class.java)
                    val tool = jsonObject["tool"]
                    val arguments = jsonObject["arguments"]
                    
                    if (tool != null && arguments != null) {
                        AnthropicResponse.ToolCall(
                            id = UUID.randomUUID().toString(),
                            type = "function",
                            name = tool as String,
                            arguments = gson.toJson(arguments)
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to parse tool call JSON: ${e.message}")
                    null
                }
            }.toList()

            return toolCalls.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to extract tool calls from text: ${e.message}")
            return null
        }
    }

    suspend fun processToolCalls(toolCalls: List<AnthropicResponse.ToolCall>, coachId: Long): List<String> {
        val results = mutableListOf<String>()
        
        for (toolCall in toolCalls) {
            when (toolCall.name) {
                "lookupPattern" -> {
                    val jsonObject = JsonParser.parseString(toolCall.arguments).asJsonObject
                    val patternName = jsonObject.get("pattern_id")?.asString
                    
                    if (patternName != null) {
                        val allPatterns = patternDao.getAllPatternsSync(coachId)
                        val pattern = allPatterns.find { it.name == patternName }
                        if (pattern != null) {
                            results.add(queryParser.formatPatternResponse(pattern))
                        } else {
                            results.add("Pattern not found: $patternName")
                        }
                    }
                }
                "searchPatterns" -> {
                    val jsonObject = JsonParser.parseString(toolCall.arguments).asJsonObject
                    val criteria = jsonObject.get("criteria")?.asString
                    
                    if (criteria != null) {
                        try {
                            val patterns = queryParser.parseSearchCommand(criteria, coachId).first()
                            val formattedPatterns = patterns.map { pattern ->
                                queryParser.formatPatternResponse(pattern, concise = true)
                            }
                            
                            results.add("""
                                Found ${patterns.size} patterns:
                                
                                ${formattedPatterns.joinToString("\n\n")}
                            """.trimIndent())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error searching patterns", e)
                            results.add("Error searching patterns: ${e.message}")
                        }
                    }
                }
            }
        }
        
        return results
    }
}
