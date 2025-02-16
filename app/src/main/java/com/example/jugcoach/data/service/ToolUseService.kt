package com.example.jugcoach.data.service

import android.content.Context
import android.util.Log
import com.example.jugcoach.data.dao.SettingsDao
import com.example.jugcoach.data.api.GroqService
import com.example.jugcoach.util.PromptLogger
import com.example.jugcoach.util.SettingsConstants
import com.example.jugcoach.ui.chat.ChatToolHandler
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolUseService @Inject constructor(
    private val groqService: GroqService,
    private val settingsDao: SettingsDao,
    private val context: Context,
    private val toolHandler: ChatToolHandler
) {
    private val gson = Gson() // Create Gson instance for logging
    suspend fun processWithTool(
        query: String,
        toolName: String,
        systemPrompt: String,
        messageHistory: List<Pair<String, String>>,
        coachId: Long
    ): String {
        val modelName = settingsDao.getSettingValue(SettingsConstants.TOOL_USE_MODEL_NAME_KEY)
        val apiKey = settingsDao.getSettingValue(SettingsConstants.TOOL_USE_MODEL_KEY_KEY)

        Log.d("ToolUseService", """
            === Tool Use Service Configuration ===
            Model Name: $modelName
            Has API Key: ${!apiKey.isNullOrEmpty()}
            Tool Name: $toolName
            Message History Size: ${messageHistory.size}
            Coach ID: $coachId
        """.trimIndent())

        if (modelName.isNullOrEmpty() || apiKey.isNullOrEmpty()) {
            Log.w("ToolUseService", "Model name or API key not set")
            return "Tool use model not configured. Please set up the model name and API key in settings."
        }

        try {
            // Enhanced logging for tool use
            Log.d("ToolUseService", """
                === Tool Use Request ===
                Model: $modelName
                Tool: $toolName
                Query: $query
                Message History Length: ${messageHistory.size}
            """.trimIndent())

            PromptLogger.logToolCall(
                llmName = modelName,
                toolName = toolName,
                arguments = query,
                context = context
            )

            val request = createToolRequest(
                modelName = modelName,
                query = query,
                toolName = toolName
            )

            // Convert request to JSON for logging
            val gson = Gson()
            val requestJson = gson.toJson(request)

            // Log the complete request details including raw JSON
            Log.d("TOOL_DEBUG", """
                === [ToolUseService] Tool Use Request Details ===
                Model: ${request.model}
                System Message: ${request.messages.find { it.role == "system" }?.content?.take(200)}...
                User Message: ${request.messages.last().content}
                Tool Configuration:
                - Name: lookupPattern
                - Description: ${request.tools?.find { it.function.name == "lookupPattern" }?.function?.description}
                - Parameters: ${request.tools?.find { it.function.name == "lookupPattern" }?.function?.parameters}
                Tool Choice: ${request.tool_choice}
                Temperature: ${request.temperature}
                
                Raw Request JSON:
                $requestJson
            """.trimIndent())

            val response = groqService.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (!response.isSuccessful) {
                // Get error body as string
                val errorBody = response.errorBody()?.string()
                
                // Log detailed error information
                Log.e("TOOL_DEBUG", """
                    === [ToolUseService] API Error Details ===
                    HTTP Status: ${response.code()}
                    Error Message: ${response.message()}
                    Error Body: $errorBody
                    
                    Request Details:
                    Model: ${request.model}
                    Temperature: ${request.temperature}
                    
                    Raw Request JSON:
                    ${gson.toJson(request)}
                    
                    Raw Response Headers:
                    ${response.headers().joinToString("\n") { "${it.first}: ${it.second}" }}
                """.trimIndent())
                return "Failed to process tool request: ${response.message()} (Status: ${response.code()})"
            }

            val message = response.body()?.choices?.firstOrNull()?.message
            if (message == null) {
                Log.e("ToolUseService", "No response received from tool use model")
                return "No response from tool use model"
            }

            Log.d("ToolUseService", """
                === Tool Use Model Response ===
                Model Used: $modelName
                Has Tool Calls: ${!message.toolCalls.isNullOrEmpty()}
                Tool Calls Count: ${message.toolCalls?.size ?: 0}
                Raw Content: ${message.content?.take(100)}...
            """.trimIndent())

            // First check if the content contains our custom JSON tool call format
            val content = message.content
            if (content != null) {
                Log.d("ToolUseService", """
                    === Checking Content for Tool Calls ===
                    Content Preview: ${content.take(100)}...
                """.trimIndent())

                val extractedToolCalls = toolHandler.extractToolCallsFromText(content)
                if (!extractedToolCalls.isNullOrEmpty()) {
                    Log.d("ToolUseService", """
                        === Processing Extracted Tool Calls ===
                        Number of Tool Calls: ${extractedToolCalls.size}
                        Tools: ${extractedToolCalls.joinToString(", ") { it.name }}
                    """.trimIndent())

                    val toolResults = toolHandler.processToolCalls(extractedToolCalls, coachId)
                    return toolResults.joinToString("\n\n")
                }
            }

            // If we still haven't found any tool calls, return an error
            Log.e("ToolUseService", "No tool calls found in response")
            return "Error: The model did not make a proper tool call. Response: $content"
        } catch (e: Exception) {
            Log.e("ToolUseService", "Failed to process with tool", e)
            return "Error processing tool request: ${e.message}"
        }
    }

    private fun createToolRequest(
        modelName: String,
        query: String,
        toolName: String
    ): com.example.jugcoach.data.api.GroqChatRequest {
        // Load the full system prompt that contains JSON format instructions
        val systemPrompt = context.assets.open("LLM_coach/system_prompt.txt").bufferedReader().use { it.readText() }
        
        return com.example.jugcoach.data.api.GroqChatRequest(
            model = modelName,
            messages = listOf(
                com.example.jugcoach.data.api.GroqMessage(
                    role = "system",
                    content = systemPrompt
                ),
                com.example.jugcoach.data.api.GroqMessage(
                    role = "user",
                    content = query
                )
            ),
            // Remove tools array since we want the model to use our custom JSON format
            tools = null,
            tool_choice = null,
            temperature = 0.7
        )
    }
}
