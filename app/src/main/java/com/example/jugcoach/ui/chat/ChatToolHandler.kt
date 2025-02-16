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
            Log.d("TOOL_DEBUG", """
                === [ChatToolHandler] Starting Tool Call Extraction ===
                Text to Extract From: $text
            """.trimIndent())
            
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
                    Log.d("TOOL_DEBUG", """
                        === [ChatToolHandler] Found Potential Tool Call ===
                        JSON: $json
                        Matches Pattern: true
                        Is Valid JSON: ${try { gson.fromJson(json, Map::class.java); true } catch (e: Exception) { false }}
                    """.trimIndent())
                    
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
        Log.d("TOOL_DEBUG", """
            === [ChatToolHandler] Processing Tool Calls ===
            Number of Tool Calls: ${toolCalls.size}
            Tool Names: ${toolCalls.map { it.name }}
            Tool Arguments: ${toolCalls.map { it.arguments }}
            Coach ID: $coachId
        """.trimIndent())
        
        val results = mutableListOf<String>()
        
        for (toolCall in toolCalls) {
            Log.d("TOOL_DEBUG", """
                === [ChatToolHandler] Processing Individual Tool Call ===
                Tool Name: ${toolCall.name}
                Tool Type: ${toolCall.type}
                Tool ID: ${toolCall.id}
                Arguments: ${toolCall.arguments}
            """.trimIndent())
            
            when (toolCall.name) {
                "lookupPattern" -> {
                    Log.d("TOOL_DEBUG", "=== [ChatToolHandler] Executing lookupPattern ===")
                    val jsonObject = JsonParser.parseString(toolCall.arguments).asJsonObject
                    val patternName = jsonObject.get("pattern_id")?.asString
                    Log.d("TOOL_DEBUG", "Parsed pattern_id: $patternName")
                    
                    if (patternName != null) {
                        val allPatterns = patternDao.getAllPatternsSync(coachId)
                        Log.d("TOOL_DEBUG", "Retrieved ${allPatterns.size} patterns from database")
                        
                        val pattern = allPatterns.find { it.name == patternName }
                        if (pattern != null) {
                            Log.d("TOOL_DEBUG", "Found matching pattern: ${pattern.name}")
                            val response = queryParser.formatPatternResponse(pattern)
                            results.add(response)
                            Log.d("TOOL_DEBUG", "Added formatted response for pattern: ${pattern.name}")
                        } else {
                            Log.d("TOOL_DEBUG", "No pattern found matching name: $patternName")
                            results.add("Pattern not found: $patternName")
                        }
                    } else {
                        Log.e("TOOL_DEBUG", "Failed to extract pattern_id from arguments: ${toolCall.arguments}")
                    }
                }
                "searchPatterns" -> {
                    Log.d("TOOL_DEBUG", "=== [ChatToolHandler] Executing searchPatterns ===")
                    val jsonObject = JsonParser.parseString(toolCall.arguments).asJsonObject
                    val criteria = jsonObject.get("criteria")?.asString
                    Log.d("TOOL_DEBUG", "Parsed search criteria: $criteria")
                    
                    if (criteria != null) {
                        try {
                            Log.d("TOOL_DEBUG", "Executing search with criteria: $criteria")
                            val patterns = queryParser.parseSearchCommand(criteria, coachId).first()
                            Log.d("TOOL_DEBUG", "Search returned ${patterns.size} patterns")
                            
                            val formattedPatterns = patterns.map { pattern ->
                                queryParser.formatPatternResponse(pattern, concise = true)
                            }
                            Log.d("TOOL_DEBUG", "Formatted ${formattedPatterns.size} pattern responses")
                            
                            val result = """
                                Found ${patterns.size} patterns:
                                
                                ${formattedPatterns.joinToString("\n\n")}
                            """.trimIndent()
                            results.add(result)
                            Log.d("TOOL_DEBUG", "Added search results to response")
                        } catch (e: Exception) {
                            Log.e("TOOL_DEBUG", "Error searching patterns", e)
                            results.add("Error searching patterns: ${e.message}")
                        }
                    } else {
                        Log.e("TOOL_DEBUG", "Failed to extract criteria from arguments: ${toolCall.arguments}")
                    }
                }
            }
        }
        
        return results
    }
}
