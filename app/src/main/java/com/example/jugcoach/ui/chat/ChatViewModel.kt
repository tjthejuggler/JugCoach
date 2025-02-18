package com.example.jugcoach.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.R
import com.example.jugcoach.data.entity.ChatMessage as DbChatMessage
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
import com.example.jugcoach.ui.gallery.CreatePatternViewModel

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
        // Only get a new recommendation if we don't already have one
        if (uiState.value.patternRecommendation.recommendedPattern == null) {
            getNewPatternRecommendation()
        }
    }

    fun hidePatternRecommendation() {
        stateManager.hidePatternRecommendation()
    }

    fun updatePatternFilters(filters: PatternFilters) {
        viewModelScope.launch {
            val currentFilters = uiState.value.patternRecommendation.filters
            // Only update and get new recommendation if filters actually changed
            if (currentFilters != filters) {
                stateManager.updatePatternFilters(filters)
                getNewPatternRecommendation()
            }
        }
    }

    fun getNewPatternRecommendation() {
        viewModelScope.launch {
            val filters = uiState.value.patternRecommendation.filters
            val currentPattern = uiState.value.patternRecommendation.recommendedPattern
            
            // Build query based on filters
            val query = buildList {
                if (filters.nameFilter.isNotEmpty()) {
                    add { pattern: Pattern ->
                        pattern.name.contains(filters.nameFilter, ignoreCase = true)
                    }
                }

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

                // Exclude current pattern if one exists
                currentPattern?.let { current ->
                    add { pattern: Pattern ->
                        pattern.id != current.id
                    }
                }
            }

            // Get all patterns matching filters
            val currentCoach = uiState.value.currentCoach
            val matchingPatterns = patternDao.getAllPatternsSync(currentCoach?.id ?: -1)
                .filter { pattern ->
                    query.all { it(pattern) }
                }

            // Get random pattern from matches, falling back to including current pattern if no other matches
            val recommendedPattern = if (matchingPatterns.isNotEmpty()) {
                matchingPatterns.random()
            } else {
                // If no patterns match without current pattern, try including it
                patternDao.getAllPatternsSync(currentCoach?.id ?: -1)
                    .filter { pattern ->
                        query.dropLast(1).all { it(pattern) } // Drop the "exclude current" predicate
                    }
                    .randomOrNull()
            }
            
            stateManager.updateRecommendedPattern(recommendedPattern)
        }
    }

    fun selectPattern(pattern: Pattern?) {
        viewModelScope.launch {
            stateManager.selectPattern(pattern)
        }
    }

    fun startRunFromMessage(message: ChatMessage) {
        if (message.messageType == ChatMessage.MessageType.RUN_SUMMARY) {
            // Extract pattern name from first line of message
            val patternName = message.text.lines().first()
            viewModelScope.launch {
                // Find pattern by name
                val pattern = patternDao.getAllPatternsSync(uiState.value.currentCoach?.id ?: -1)
                    .find { it.name == patternName }
                
                pattern?.let {
                    // Start new run with this pattern
                    stateManager.startPatternRun(it)
                    showPatternRecommendation()
                }
            }
        }
    }

    suspend fun findPatternByName(patternName: String): Pattern? {
        return patternDao.getAllPatternsSync(uiState.value.currentCoach?.id ?: -1)
            .find { it.name == patternName }
    }

    suspend fun getRandomPatternFromRelationship(patternId: String, relationshipType: String): Pattern? {
        return when (relationshipType) {
            CreatePatternViewModel.RELATIONSHIP_PREREQUISITE -> patternDao.getRandomPrerequisite(patternId)
            CreatePatternViewModel.RELATIONSHIP_RELATED -> patternDao.getRandomRelated(patternId)
            CreatePatternViewModel.RELATIONSHIP_DEPENDENT -> patternDao.getRandomDependent(patternId)
            else -> null
        }
    }

    fun startPatternRun(pattern: Pattern) {
        viewModelScope.launch {
            stateManager.startPatternRun(pattern)
        }
    }

    suspend fun hasPatternRelationship(patternId: String, relationshipType: String): Boolean {
        return when (relationshipType) {
            CreatePatternViewModel.RELATIONSHIP_PREREQUISITE -> patternDao.hasPrerequisites(patternId)
            CreatePatternViewModel.RELATIONSHIP_RELATED -> patternDao.hasRelated(patternId)
            CreatePatternViewModel.RELATIONSHIP_DEPENDENT -> patternDao.hasDependents(patternId)
            else -> false
        }
    }

    private var timerJob: kotlinx.coroutines.Job? = null

    fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            var lastUpdateTime = startTime
            while (true) {
                val currentTime = System.currentTimeMillis()
                val timeDiff = currentTime - lastUpdateTime
                lastUpdateTime = currentTime
                stateManager.updateTimer(timeDiff)
                kotlinx.coroutines.delay(100) // Update every 100ms
            }
        }
        stateManager.startTimer()
    }

    fun cancelPatternRun() {
        timerJob?.cancel()
        viewModelScope.launch {
            stateManager.cancelPatternRun()
        }
    }

    fun endPatternRun(wasCatch: Boolean, catches: Int?) {
        timerJob?.cancel()
        viewModelScope.launch {
            val currentRun = uiState.value.patternRun
            if (currentRun != null) {
                val pattern = currentRun.pattern
                val duration = if (currentRun.elapsedTime > 0) currentRun.elapsedTime / 1000 else null

                // Save run to pattern history
                patternDao.addRun(
                    patternId = pattern.id,
                    catches = catches,
                    duration = duration,
                    cleanEnd = wasCatch,
                    date = System.currentTimeMillis()
                )

                // Create summary message
                val context = stateManager.getContext()
                val summaryParts = mutableListOf<String>()
                
                // Add pattern name
                summaryParts.add(pattern.name)

                // Add duration if timer was used
                duration?.let {
                    val minutes = it / 60
                    val seconds = it % 60
                    summaryParts.add(context.getString(R.string.run_summary_duration, minutes, seconds))
                }

                // Add catches if provided
                catches?.let {
                    summaryParts.add(context.getString(R.string.run_summary_catches, it))
                }

                // Add completion status
                summaryParts.add(context.getString(
                    if (wasCatch) R.string.run_summary_end_clean else R.string.run_summary_end_drop
                ))

                // Save summary message
                messageRepository.saveMessage(
                    conversationId = uiState.value.currentConversation?.id ?: return@launch,
                    text = summaryParts.joinToString("\n"),
                    isFromUser = true,
                    messageType = DbChatMessage.MessageType.RUN_SUMMARY
                )
            }
            stateManager.endPatternRun()
        }
    }
}
