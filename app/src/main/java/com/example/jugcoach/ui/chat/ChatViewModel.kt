package com.example.jugcoach.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.ui.chat.ChatMessage
import com.example.jugcoach.data.entity.Coach
import com.example.jugcoach.data.entity.Conversation
import com.example.jugcoach.data.entity.Pattern
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import retrofit2.HttpException
import javax.inject.Inject
import com.example.jugcoach.data.api.AnthropicResponse
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.dao.SettingsDao
import com.example.jugcoach.util.SettingsConstants

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val stateManager: ChatStateManager,
    private val messageRepository: ChatMessageRepository,
    private val apiService: ChatApiService,
    private val toolHandler: ChatToolHandler,
    private val settingsDao: SettingsDao,
    private val patternDao: PatternDao
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

            var response: com.example.jugcoach.data.api.AnthropicResponse? = null
            
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

                Log.d("TOOL_DEBUG", """
                    === [ChatViewModel] Starting Message Processing ===
                    Message: $text
                    Coach: ${currentCoach.name}
                    Message History Size: ${messageHistory.size}
                """.trimIndent())

                // Load system prompt and determine route
                val systemPrompt = apiService.loadSystemPrompt(currentCoach.systemPrompt ?: "system_prompt.txt")
                Log.d("TOOL_DEBUG", """
                    === [ChatViewModel] Loaded System Prompt ===
                    Prompt Source: ${currentCoach.systemPrompt ?: "system_prompt.txt"}
                    Prompt Preview: ${systemPrompt.take(100)}...
                """.trimIndent())

                val route = apiService.determineRoute(text)
                Log.d("TOOL_DEBUG", """
                    === [ChatViewModel] Route Determination ===
                    Message: $text
                    Determined Route: $route
                    Will Use Tools: ${route != "no_tool"}
                """.trimIndent())

                // Handle response based on route
                if (route == "no_tool") {
                    Log.d("TOOL_DEBUG", "=== [ChatViewModel] Using Main LLM (No Tool) ===")
                    // For non-tool routes, use Claude
                    val request = apiService.createAnthropicRequest(systemPrompt, messageHistory, text)
                    response = apiService.sendMessage(apiKey, request)
                    val responseText = response.content.firstOrNull()?.text ?: "No response from the coach"
                    if (responseText.isNotEmpty()) {
                        messageRepository.saveMessage(
                            conversationId = conversation.id,
                            text = responseText,
                            isFromUser = false,
                            model = response.model,
                            apiKeyName = currentCoach.apiKeyName
                        )
                    }
                } else {
                    Log.d("TOOL_DEBUG", """
                        === [ChatViewModel] Starting Tool Use Flow ===
                        Route: $route
                        Tool to Use: $route
                        Message: $text
                        Coach ID: ${currentCoach.id}
                    """.trimIndent())
                    
                    // Get tool-use model name and settings
                    val toolUseModelName = settingsDao.getSettingValue(SettingsConstants.TOOL_USE_MODEL_NAME_KEY)
                    val toolUseModelKey = settingsDao.getSettingValue(SettingsConstants.TOOL_USE_MODEL_KEY_KEY)
                    
                    Log.d("TOOL_DEBUG", """
                        === [ChatViewModel] Tool Use Configuration ===
                        Model Name: $toolUseModelName
                        Has Model Key: ${!toolUseModelKey.isNullOrEmpty()}
                    """.trimIndent())
                    
                    // Process with tool-use LLM
                    val toolResponse = apiService.processWithTool(
                        query = text,
                        toolName = route,
                        systemPrompt = systemPrompt,
                        messageHistory = messageHistory,
                        coachId = currentCoach.id
                    )

                    Log.d("TOOL_DEBUG", """
                        === [ChatViewModel] Tool Response Received ===
                        Response Length: ${toolResponse.length}
                        Response Preview: ${toolResponse.take(100)}...
                    """.trimIndent())
                    
                    Log.d("TOOL_DEBUG", """
                        === [ChatViewModel] Saving Tool Response ===
                        Conversation ID: ${conversation.id}
                        Response Length: ${toolResponse.length}
                        Model: ${toolUseModelName ?: "Tool-use LLM"}
                        Is Error Response: ${toolResponse.startsWith("Error")}
                    """.trimIndent())

                    messageRepository.saveMessage(
                        conversationId = conversation.id,
                        text = toolResponse,
                        isFromUser = false,
                        model = toolUseModelName ?: "Tool-use LLM",
                        apiKeyName = currentCoach.apiKeyName
                    )

                    Log.d("TOOL_DEBUG", "=== [ChatViewModel] Tool Response Saved Successfully ===")
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
                    isError = true,
                    model = response?.model,
                    apiKeyName = currentCoach.apiKeyName
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
                    isError = true,
                    model = response?.model,
                    apiKeyName = currentCoach.apiKeyName
                )
            } finally {
                stateManager.setLoading(false)
            }
        }
    }

    fun showPatternRecommendation() {
        stateManager.showPatternRecommendation()
        getNewPatternRecommendation() // Get initial recommendation
    }

    fun hidePatternRecommendation() {
        stateManager.hidePatternRecommendation()
    }

    fun updatePatternFilters(filters: PatternFilters) {
        viewModelScope.launch {
            stateManager.updatePatternFilters(filters)
            getNewPatternRecommendation() // Get new recommendation whenever filters change
        }
    }

    fun getNewPatternRecommendation() {
        viewModelScope.launch {
            val filters = uiState.value.patternRecommendation.filters
            
            // Build query based on filters
            val query = buildList {
                if (filters.numBalls.isNotEmpty()) {
                    add { pattern: Pattern ->
                        pattern.num in filters.numBalls
                    }
                }
                
                add { pattern: Pattern ->
                    val difficulty = pattern.difficulty?.toFloatOrNull() ?: return@add false
                    difficulty in filters.difficultyRange
                }
                
                if (filters.tags.isNotEmpty()) {
                    add { pattern: Pattern ->
                        pattern.tags.any { it in filters.tags }
                    }
                }

                filters.minCatches?.let { min ->
                    add { pattern: Pattern ->
                        pattern.record?.catches?.let { it >= min } ?: false
                    }
                }

                filters.maxCatches?.let { max ->
                    add { pattern: Pattern ->
                        pattern.record?.catches?.let { it <= max } ?: true
                    }
                }
            }

            // Get all patterns matching filters
            val currentCoach = uiState.value.currentCoach
            val matchingPatterns = patternDao.getAllPatternsSync(currentCoach?.id ?: -1)
                .filter { pattern ->
                    query.all { it(pattern) }
                }

            // Get random pattern from matches
            val recommendedPattern = matchingPatterns.randomOrNull()
            stateManager.updateRecommendedPattern(recommendedPattern)
        }
    }

    fun selectPattern(pattern: Pattern?) {
        viewModelScope.launch {
            stateManager.selectPattern(pattern)
        }
    }
}
