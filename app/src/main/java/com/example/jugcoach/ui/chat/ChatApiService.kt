package com.example.jugcoach.ui.chat

import android.content.Context
import android.util.Log
import com.example.jugcoach.data.api.AnthropicRequest
import com.example.jugcoach.data.api.AnthropicResponse
import com.example.jugcoach.data.api.AnthropicService
import com.example.jugcoach.data.dao.SettingsDao
import com.example.jugcoach.data.service.RoutingService
import com.example.jugcoach.data.service.ToolUseService
import com.example.jugcoach.util.PromptLogger
import com.example.jugcoach.util.SystemPromptLoader
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private const val TAG = "ChatApiService"

class ChatApiService @Inject constructor(
    private val settingsDao: SettingsDao,
    private val anthropicService: AnthropicService,
    private val routingService: RoutingService,
    private val toolUseService: ToolUseService,
    private val context: Context,
    private val chatToolHandler: ChatToolHandler
) {
    suspend fun getApiKey(apiKeyName: String): String? {
        return settingsDao.getSettingValue(apiKeyName)
    }

    suspend fun determineRoute(query: String): String {
        Log.d(TAG, """
            === Starting Route Determination ===
            Query: $query
        """.trimIndent())

        val route = try {
            val decision = routingService.routeQuery(query)
            Log.d(TAG, """
                === Route Decision ===
                Query: $query
                Decision: $decision
                Using Tool: ${decision != "no_tool"}
            """.trimIndent())
            decision
        } catch (e: Exception) {
            Log.e(TAG, "Routing failed, falling back to no tool", e)
            "no_tool"
        }

        // Validate route is one of the expected values
        return when (route) {
            "lookupPattern", "searchPatterns", "no_tool" -> route
            else -> {
                Log.e(TAG, "Invalid route returned: $route, falling back to no_tool")
                "no_tool"
            }
        }
    }

    fun createAnthropicRequest(
        systemPrompt: String,
        messageHistory: List<Pair<String, String>>,
        userMessage: String
    ): AnthropicRequest {
        return AnthropicRequest(
            system = systemPrompt,
            messages = messageHistory.map { (role, content) ->
                AnthropicRequest.Message(
                    role = role,
                    content = listOf(AnthropicRequest.Content(text = content))
                )
            } + AnthropicRequest.Message(
                role = "user",
                content = listOf(AnthropicRequest.Content(text = userMessage))
            ),
            tools = null // Claude should not have tool capabilities
        )
    }

    suspend fun processWithTool(
        query: String,
        toolName: String,
        systemPrompt: String,
        messageHistory: List<Pair<String, String>>,
        coachId: Long
    ): String {
        // Validate tool name
        if (toolName !in listOf("lookupPattern", "searchPatterns")) {
            Log.e(TAG, "Invalid tool name: $toolName")
            return "Error: Invalid tool specified"
        }

        Log.d(TAG, """
            === Starting Tool Processing ===
            Query: $query
            Tool: $toolName
            Message History Size: ${messageHistory.size}
            Coach ID: $coachId
        """.trimIndent())

        val result = toolUseService.processWithTool(
            query = query,
            toolName = toolName,
            systemPrompt = systemPrompt,
            messageHistory = messageHistory,
            coachId = coachId
        )

        Log.d(TAG, """
            === Tool Processing Complete ===
            Tool: $toolName
            Result Preview: ${result.take(100)}...
            Success: ${!result.startsWith("Error") && !result.startsWith("Failed")}
        """.trimIndent())

        return result
    }

    suspend fun sendMessage(
        apiKey: String,
        request: AnthropicRequest
    ): AnthropicResponse {
        // Log the request for debugging
        PromptLogger.logInteraction(
            context = context,
            systemPrompt = """
                === Complete Request ===
                System Prompt: ${request.system}
                
                === Message History ===
                ${request.messages.joinToString("\n\n") { msg ->
                    """
                    Role: ${msg.role}
                    Content: ${msg.content.firstOrNull()?.text ?: ""}
                    """.trimIndent()
                }}
            """.trimIndent(),
            userMessage = request.messages.lastOrNull()?.content?.firstOrNull()?.text ?: ""
        )

        val response = anthropicService.sendMessage(
            apiKey = apiKey,
            request = request
        )

        // Log the response for debugging
        Log.d(TAG, """
            === Claude Response ===
            Content: ${response.content.firstOrNull()?.text?.take(100)}...
            Model: ${response.model}
        """.trimIndent())

        return response
    }

    fun loadSystemPrompt(coachSystemPrompt: String): String {
        return SystemPromptLoader.loadSystemPrompt(context, coachSystemPrompt)
    }
}
