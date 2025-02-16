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
        return try {
            routingService.routeQuery(query)
        } catch (e: Exception) {
            Log.e(TAG, "Routing failed, falling back to no tool", e)
            "no_tool"
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
            tools = listOf(
                AnthropicRequest.Tool(
                    name = "lookupPattern",
                    description = "Get full details of a specific pattern given its pattern_id.",
                    inputSchema = AnthropicRequest.InputSchema(
                        properties = mapOf(
                            "pattern_id" to AnthropicRequest.Property(
                                type = "string",
                                description = "The unique identifier for the pattern."
                            )
                        ),
                        required = listOf("pattern_id")
                    )
                ),
                AnthropicRequest.Tool(
                    name = "searchPatterns",
                    description = "Search for patterns using criteria like difficulty, number of balls, and tags.",
                    inputSchema = AnthropicRequest.InputSchema(
                        properties = mapOf(
                            "criteria" to AnthropicRequest.Property(
                                type = "string",
                                description = "Search criteria in format: difficulty:>=5, balls:3, tags:[\"cascade\", \"syncopated\"]"
                            )
                        ),
                        required = listOf("criteria")
                    )
                )
            )
        )
    }

    suspend fun processWithTool(
        query: String,
        toolName: String,
        systemPrompt: String,
        messageHistory: List<Pair<String, String>>
    ): String {
        return toolUseService.processWithTool(
            query = query,
            toolName = toolName,
            systemPrompt = systemPrompt,
            messageHistory = messageHistory
        )
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

        // Log any tool calls in the response
        response.content.firstOrNull()?.text?.let { text ->
            val extractedToolCalls = chatToolHandler.extractToolCallsFromText(text)
            extractedToolCalls?.forEach { toolCall ->
                PromptLogger.logToolCall(
                    llmName = "claude",
                    toolName = toolCall.name,
                    arguments = toolCall.arguments,
                    context = context
                )
            }
        }

        return response
    }

    fun loadSystemPrompt(coachSystemPrompt: String): String {
        return SystemPromptLoader.loadSystemPrompt(context, coachSystemPrompt)
    }
}
