package com.example.jugcoach.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.ui.chat.ChatMessage
import com.example.jugcoach.data.entity.Coach
import com.example.jugcoach.data.entity.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import retrofit2.HttpException
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val stateManager: ChatStateManager,
    private val messageRepository: ChatMessageRepository,
    private val apiService: ChatApiService,
    private val toolHandler: ChatToolHandler
) : ViewModel() {

    val uiState: StateFlow<ChatUiState> = stateManager.uiState
    val availableApiKeys: StateFlow<List<String>> = stateManager.availableApiKeys

    fun updateCoachApiKey(apiKeyName: String) {
        viewModelScope.launch {
            stateManager.updateCoachApiKey(apiKeyName)
        }
    }

    fun createNewConversation() {
        viewModelScope.launch {
            stateManager.createNewConversation()
        }
    }

    fun selectConversation(conversation: Conversation) {
        viewModelScope.launch {
            stateManager.selectConversation(conversation)
        }
    }

    fun updateConversationTitle(title: String) {
        viewModelScope.launch {
            stateManager.updateConversationTitle(title)
        }
    }

    fun toggleConversationFavorite() {
        viewModelScope.launch {
            stateManager.toggleConversationFavorite()
        }
    }

    fun selectCoach(coach: Coach) {
        viewModelScope.launch {
            stateManager.selectCoach(coach)
        }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            val currentCoach = stateManager.uiState.value.currentCoach ?: return@launch

            // Get or create conversation
            val conversation = try {
                messageRepository.getOrCreateConversation(
                    existingConversation = stateManager.uiState.value.currentConversation,
                    coachId = currentCoach.id,
                    title = "Chat ${java.time.format.DateTimeFormatter.ISO_INSTANT.format(Instant.now())}"
                )
            } catch (e: Exception) {
                stateManager.setError("Failed to create conversation: ${e.message}")
                return@launch
            }

            // Save user message
            try {
                messageRepository.saveMessage(
                    conversationId = conversation.id,
                    text = text,
                    isFromUser = true
                )
            } catch (e: Exception) {
                stateManager.setError("Failed to save message: ${e.message}")
                return@launch
            }

            stateManager.setLoading(true)

            // Get API key
            val apiKey = apiService.getApiKey(currentCoach.apiKeyName)
            if (apiKey.isNullOrEmpty()) {
                messageRepository.saveMessage(
                    conversationId = conversation.id,
                    text = "Please set up the API key '${currentCoach.apiKeyName}' in Settings to chat with ${currentCoach.name}.",
                    isFromUser = false,
                    isError = true
                )
                stateManager.setLoading(false)
                return@launch
            }

            try {
                // Get message history
                val recentMessages = stateManager.uiState.value.messages.takeLast(10)
                val messageHistory = recentMessages.mapNotNull { msg ->
                    if (msg.text.isNotEmpty()) {
                        Pair(
                            if (msg.sender == ChatMessage.Sender.USER) "user" else "assistant",
                            msg.text
                        )
                    } else null
                }

                // Load system prompt and determine route
                val systemPrompt = apiService.loadSystemPrompt(currentCoach.systemPrompt ?: "system_prompt.txt")
                val route = apiService.determineRoute(text)

                // Get initial response
                val (initialResponse, toolCalls) = if (route == "no_tool") {
                    val request = apiService.createAnthropicRequest(systemPrompt, messageHistory, text)
                    val response = apiService.sendMessage(apiKey, request)
                    Pair(
                        response.content.firstOrNull()?.text ?: "No response from the coach",
                        response.toolCalls
                    )
                } else {
                    Pair(
                        apiService.processWithTool(text, route, systemPrompt, messageHistory),
                        null
                    )
                }

                // Process tool calls if any
                val extractedToolCalls = toolCalls ?: toolHandler.extractToolCallsFromText(initialResponse)
                if (!extractedToolCalls.isNullOrEmpty()) {
                    // Save initial response if not empty
                    if (initialResponse.isNotEmpty()) {
                        messageRepository.saveMessage(
                            conversationId = conversation.id,
                            text = initialResponse,
                            isFromUser = false
                        )
                    }

                    // Process tool calls and get results
                    val toolResults = toolHandler.processToolCalls(extractedToolCalls, currentCoach.id)

                    // Save tool output
                    if (toolResults.isNotEmpty()) {
                        messageRepository.saveMessage(
                            conversationId = conversation.id,
                            text = "Tool Output:\n\n${toolResults.joinToString("\n\n")}",
                            isFromUser = false
                        )

                        // Create follow-up request to analyze tool output
                        val followUpRequest = apiService.createAnthropicRequest(
                            systemPrompt = systemPrompt,
                            messageHistory = messageHistory + listOf(Pair("assistant", toolResults.joinToString("\n\n"))),
                            userMessage = "Please analyze the above tool output and explain its implications for the juggling pattern."
                        )

                        val followUpResponse = apiService.sendMessage(apiKey, followUpRequest)
                        
                        // Save analysis
                        messageRepository.saveMessage(
                            conversationId = conversation.id,
                            text = followUpResponse.content.firstOrNull()?.text ?: "No analysis provided",
                            isFromUser = false
                        )
                    }
                } else {
                    // Save initial response as is
                    messageRepository.saveMessage(
                        conversationId = conversation.id,
                        text = initialResponse,
                        isFromUser = false
                    )
                }
            } catch (e: retrofit2.HttpException) {
                val errorMessage = when (e.code()) {
                    401 -> "Invalid API key. Please check your settings."
                    429 -> "Too many requests. Please try again later."
                    else -> "API error: ${e.message()}"
                }
                messageRepository.saveMessage(
                    conversationId = conversation.id,
                    text = errorMessage,
                    isFromUser = false,
                    isError = true
                )
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("401") == true -> "Invalid API key. Please check your settings."
                    e.message?.contains("timeout") == true -> "Request timed out. Please try again."
                    else -> "Failed to get response: ${e.message}"
                }
                messageRepository.saveMessage(
                    conversationId = conversation.id,
                    text = errorMessage,
                    isFromUser = false,
                    isError = true
                )
            } finally {
                stateManager.setLoading(false)
            }
        }
    }
}
