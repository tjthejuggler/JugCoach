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
                val recentMessages = stateManager.uiState.value.messages.takeLast(10)
                val messageHistory = recentMessages.mapNotNull { msg ->
                    if (msg.text.isNotEmpty()) {
                        Pair(
                            if (msg.sender == ChatMessage.Sender.USER) "user" else "assistant",
                            msg.text
                        )
                    } else null
                }

                val systemPrompt = apiService.loadSystemPrompt(currentCoach.systemPrompt ?: "system_prompt.txt")
                val route = apiService.determineRoute(text)

                if (route == "no_tool") {
                    val request = apiService.createAnthropicRequest(systemPrompt, messageHistory, text)
                    val response = apiService.sendMessage(apiKey, request)
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
                    val toolUseModelName = settingsDao.getSettingValue(SettingsConstants.TOOL_USE_MODEL_NAME_KEY)
                    val toolResponse = apiService.processWithTool(
                        query = text,
                        toolName = route,
                        systemPrompt = systemPrompt,
                        messageHistory = messageHistory,
                        coachId = currentCoach.id
                    )

                    messageRepository.saveMessage(
                        conversationId = conversation.id,
                        text = toolResponse,
                        isFromUser = false,
                        model = toolUseModelName ?: "Tool-use LLM",
                        apiKeyName = currentCoach.apiKeyName
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
            stateManager.updatePatternFilters(filters)
            getNewPatternRecommendation()
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

                currentPattern?.let { current ->
                    add { pattern: Pattern ->
                        pattern.id != current.id
                    }
                }
            }

            val currentCoach = uiState.value.currentCoach
            val recommendedPattern = patternDao.getAllPatternsSync(currentCoach?.id ?: -1)
                .filter { pattern -> query.all { it(pattern) } }
                .randomOrNull()

            stateManager.updateRecommendedPattern(recommendedPattern)
        }
    }

    suspend fun findPatternByName(patternName: String): Pattern? {
        return patternDao.getAllPatternsSync(uiState.value.currentCoach?.id ?: -1)
            .find { it.name == patternName }
    }

    suspend fun findPatternById(patternId: String): Pattern? {
        return patternDao.getPattern(patternId)
    }

    suspend fun getRandomPatternFromRelationship(patternId: String, relationshipType: String): Pattern? {
        return when (relationshipType) {
            PatternRelationships.PREREQS -> patternDao.getRandomPrerequisite(patternId)
            PatternRelationships.RELATED -> patternDao.getRandomRelated(patternId)
            PatternRelationships.DEPENDENTS -> patternDao.getRandomDependent(patternId)
            else -> null
        }
    }

    fun startPatternRun(pattern: Pattern) {
        android.util.Log.d("TimerDebug", "ChatViewModel.startPatternRun() called with pattern: ${pattern.name}")
        viewModelScope.launch {
            android.util.Log.d("TimerDebug", "Calling stateManager.startPatternRun()")
            stateManager.startPatternRun(pattern)
        }
    }

    fun startRunFromMessage(message: ChatMessage) {
        if (message.messageType == ChatMessage.MessageType.RUN_SUMMARY) {
            val patternName = message.text.lines().first()
            viewModelScope.launch {
                findPatternByName(patternName)?.let { pattern ->
                    startPatternRun(pattern)
                    showPatternRecommendation()
                }
            }
        }
    }

    private var timerJob: kotlinx.coroutines.Job? = null

    fun stopTimer() {
        timerJob?.cancel()
    }

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
                kotlinx.coroutines.delay(1000) // Update every 1000ms
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

    fun getElapsedSeconds(): Long {
        return (uiState.value.patternRun?.elapsedTime ?: 0L) / 1000L
    }

    fun endPatternRun(wasCatch: Boolean, catches: Int?, duration: Int? = null) {
        timerJob?.cancel()
        viewModelScope.launch {
            val currentRun = uiState.value.patternRun
            if (currentRun != null) {
                val pattern = currentRun.pattern
                val finalDuration = duration?.toLong() ?: if (currentRun.elapsedTime > 0) {
                    currentRun.elapsedTime / 1000L
                } else null

                patternDao.addRun(
                    patternId = pattern.id,
                    catches = catches,
                    duration = finalDuration,
                    cleanEnd = wasCatch,
                    date = System.currentTimeMillis()
                )

                val context = stateManager.getContext()
                val summaryParts = mutableListOf<String>()
                
                summaryParts.add(pattern.name)
                duration?.let {
                    val minutes = it / 60
                    val seconds = it % 60
                    summaryParts.add(context.getString(R.string.run_time_format, minutes, seconds))
                }
                catches?.let {
                    summaryParts.add(context.getString(R.string.run_summary_catches, it))
                }
                summaryParts.add(context.getString(
                    if (wasCatch) R.string.run_summary_end_clean else R.string.run_summary_end_drop
                ))

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
