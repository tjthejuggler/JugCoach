package com.example.jugcoach.data.service

import android.util.Log
import com.example.jugcoach.data.dao.SettingsDao
import com.example.jugcoach.data.api.GroqService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolUseService @Inject constructor(
    private val groqService: GroqService,
    private val settingsDao: SettingsDao
) {
    suspend fun processWithTool(
        query: String,
        toolName: String,
        systemPrompt: String,
        messageHistory: List<Pair<String, String>>
    ): String {
        val modelName = settingsDao.getSettingValue("tool_use_model_name")
        val apiKey = settingsDao.getSettingValue("tool_use_model_key")

        if (modelName.isNullOrEmpty() || apiKey.isNullOrEmpty()) {
            Log.w("ToolUseService", "Model name or API key not set")
            return "Tool use model not configured. Please set up the model name and API key in settings."
        }

        try {
            val response = groqService.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = com.example.jugcoach.data.api.GroqChatRequest(
                    model = modelName,
                    messages = messageHistory.map { (role, content) ->
                        com.example.jugcoach.data.api.GroqMessage(
                            role = role,
                            content = content
                        )
                    } + listOf(
                        com.example.jugcoach.data.api.GroqMessage(
                            role = "system",
                            content = """
                                $systemPrompt
                                
                                You are a tool use assistant. Based on the user's query, you should use the appropriate tool.
                                For pattern lookups, use the 'lookupPattern' tool with the pattern ID.
                                For pattern searches, use the 'searchPatterns' tool with search criteria.
                                
                                Current tool to use: $toolName
                            """.trimIndent()
                        ),
                        com.example.jugcoach.data.api.GroqMessage(
                            role = "user",
                            content = query
                        )
                    ),
                    tools = listOf(
                        com.example.jugcoach.data.api.GroqTool(
                            type = "function",
                            function = com.example.jugcoach.data.api.GroqFunction(
                                name = "lookupPattern",
                                description = "Get full details of a specific pattern given its pattern_id.",
                                parameters = mapOf(
                                    "type" to "object",
                                    "properties" to mapOf(
                                        "pattern_id" to mapOf(
                                            "type" to "string",
                                            "description" to "The unique identifier for the pattern."
                                        )
                                    ),
                                    "required" to listOf("pattern_id")
                                )
                            )
                        ),
                        com.example.jugcoach.data.api.GroqTool(
                            type = "function",
                            function = com.example.jugcoach.data.api.GroqFunction(
                                name = "searchPatterns",
                                description = "Search for patterns using criteria like difficulty, number of balls, and tags.",
                                parameters = mapOf(
                                    "type" to "object",
                                    "properties" to mapOf(
                                        "criteria" to mapOf(
                                            "type" to "string",
                                            "description" to "Search criteria in format: difficulty:>=5, balls:3, tags:[\"cascade\", \"syncopated\"]"
                                        )
                                    ),
                                    "required" to listOf("criteria")
                                )
                            )
                        )
                    ),
                    toolChoice = "auto"
                )
            )

            if (!response.isSuccessful) {
                Log.e("ToolUseService", "API error: ${response.code()} - ${response.message()}")
                return "Failed to process tool request: ${response.message()}"
            }

            val result = response.body()?.choices?.firstOrNull()?.message?.content
            return result ?: "No response from tool use model"
        } catch (e: Exception) {
            Log.e("ToolUseService", "Failed to process with tool", e)
            return "Error processing tool request: ${e.message}"
        }
    }
}
