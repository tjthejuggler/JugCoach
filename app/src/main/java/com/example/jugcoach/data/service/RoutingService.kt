package com.example.jugcoach.data.service

import android.util.Log
import com.example.jugcoach.data.dao.SettingsDao
import com.example.jugcoach.data.api.GroqService
import com.example.jugcoach.util.SettingsConstants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutingService @Inject constructor(
    private val groqService: GroqService,
    private val settingsDao: SettingsDao
) {
    suspend fun routeQuery(query: String): String {
        val modelName = settingsDao.getSettingValue(SettingsConstants.ROUTING_MODEL_NAME_KEY)
        val apiKey = settingsDao.getSettingValue(SettingsConstants.ROUTING_MODEL_KEY_KEY)

        Log.d("TOOL_DEBUG", """
            === [RoutingService] Starting Route Query ===
            Query: $query
            Model Name Setting: ${modelName ?: "null"}
            Has API Key: ${!apiKey.isNullOrEmpty()}
            Settings Key Used: routing_model_name
        """.trimIndent())

        if (modelName.isNullOrEmpty() || apiKey.isNullOrEmpty()) {
            Log.e("TOOL_DEBUG", """
                === [RoutingService] Configuration Error ===
                Model Name: ${modelName ?: "null"}
                Has API Key: ${!apiKey.isNullOrEmpty()}
                Error: Missing required configuration
            """.trimIndent())
            return "no_tool"
        }

        // Fallback pre-check: if query clearly mentions a 3-digit number, assume it's a lookup request.
        val patternNumberRegex = Regex("""\b\d{3}\b""")
        if (patternNumberRegex.containsMatchIn(query)) {
            Log.d("TOOL_DEBUG", "Detected a pattern number in the query. Returning lookupPattern immediately.")
            return "lookupPattern"
        }

        try {
            val response = groqService.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = com.example.jugcoach.data.api.GroqChatRequest(
                    model = modelName,
                    messages = listOf(
                        com.example.jugcoach.data.api.GroqMessage(
                            role = "system",
                            content = """
                                You are a routing assistant for a juggling pattern database. Your sole responsibility is to decide when to use tools for pattern-related queries.
                                
                                IMPORTANT: Users may ask in natural language—phrases like "look up information on pattern 441" or "tell me about Mills Mess" should be interpreted as a request to fetch detailed information about a specific pattern.
                                
                                RULES:
                                1. If the query mentions any specific pattern number (e.g., "423", "441", "534", etc.) or a specific pattern name (e.g., "Mills Mess", "Cascade", "Shower"), respond with exactly: lookupPattern
                                2. If the query indicates searching for multiple patterns (e.g., "find 3 ball patterns", "show intermediate patterns"), respond with exactly: searchPatterns
                                3. If there is no reference to a specific pattern, respond with exactly: no_tool
                                
                                Your response must be exactly one of these three strings (lookupPattern, searchPatterns, or no_tool) with no additional text.
                            """.trimIndent()
                        ),
                        com.example.jugcoach.data.api.GroqMessage(
                            role = "user",
                            content = query
                        )
                    ),
                    max_tokens = 30
                )
            )

            if (!response.isSuccessful) {
                Log.e("RoutingService", "API error: ${response.code()} - ${response.message()}")
                return "no_tool"
            }

            val routingDecision = response.body()?.choices?.firstOrNull()?.message?.content?.trim() ?: "no_tool"
            Log.d("TOOL_DEBUG", """
                === [RoutingService] API Response ===
                Query: $query
                Response Code: ${response.code()}
                Response Message: ${response.message()}
                Response Body: ${response.body()}
                Raw Decision: $routingDecision
                Model Used: $modelName
                Has API Key: ${!apiKey.isNullOrEmpty()}
            """.trimIndent())

            val finalDecision = when {
                routingDecision.lowercase().contains("lookuppattern") -> "lookupPattern"
                routingDecision.lowercase().contains("searchpatterns") -> "searchPatterns"
                else -> "no_tool"
            }

            Log.d("TOOL_DEBUG", "Final routing decision: $finalDecision")
            return finalDecision
        } catch (e: Exception) {
            Log.e("RoutingService", "Failed to route query", e)
            return "no_tool"
        }
    }
}
