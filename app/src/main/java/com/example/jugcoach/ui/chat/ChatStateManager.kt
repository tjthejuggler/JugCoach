package com.example.jugcoach.ui.chat

import com.example.jugcoach.ui.chat.ChatMessage
import com.example.jugcoach.ui.chat.ChatUiState
import com.example.jugcoach.ui.chat.PatternFilters
import com.example.jugcoach.data.dao.CoachDao
import com.example.jugcoach.data.dao.ConversationDao
import com.example.jugcoach.data.dao.SettingsDao
import com.example.jugcoach.data.entity.Coach
import com.example.jugcoach.data.entity.Conversation
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.entity.SettingCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatStateManager @Inject constructor(
    private val settingsDao: SettingsDao,
    private val coachDao: CoachDao,
    private val conversationDao: ConversationDao,
    private val messageRepository: ChatMessageRepository,
    @ApplicationContext private val context: Context
) {

    fun getContext(): Context = context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _availableApiKeys = MutableStateFlow<List<String>>(emptyList())
    val availableApiKeys: StateFlow<List<String>> = _availableApiKeys.asStateFlow()

    init {
        scope.launch {
            ensureHeadCoach()
        }
        loadData()
        loadApiKeys()
    }

    private fun loadApiKeys() {
        scope.launch {
            _availableApiKeys.update { emptyList() }
            settingsDao.getSettingsByCategory(SettingCategory.API_KEY)
                .collect { settings ->
                    val validKeys = settings
                        .filter { it.value.isNotBlank() }
                        .map { it.key }
                    _availableApiKeys.value = validKeys
                }
        }
    }

    suspend fun updateCoachApiKey(apiKeyName: String) {
        val currentCoach = _uiState.value.currentCoach ?: return
        val updatedCoach = currentCoach.copy(apiKeyName = apiKeyName)
        coachDao.updateCoach(updatedCoach)
        _uiState.update { it.copy(currentCoach = updatedCoach) }
    }

    private fun loadData() {
        scope.launch {
            // Load coaches first
            coachDao.getAllCoaches()
                .distinctUntilChanged()
                .collect { coaches ->
                    val currentCoach = coaches.find { it.isHeadCoach } ?: coaches.firstOrNull()
                    _uiState.update { state ->
                        state.copy(
                            availableCoaches = coaches,
                            currentCoach = currentCoach
                        )
                    }

                    // Only load conversations if we have a current coach
                    currentCoach?.let { coach ->
                        conversationDao.getConversationsForCoach(coach.id)
                            .distinctUntilChanged()
                            .collect { conversations ->
                                val currentConversation = conversations.firstOrNull()
                                _uiState.update { state ->
                                    state.copy(
                                        availableConversations = conversations,
                                        currentConversation = currentConversation
                                    )
                                }

                                // Load messages for current conversation
                                currentConversation?.let { conversation ->
                                    loadMessagesForConversation(conversation)
                                }
                            }
                    }
                }
        }
    }

    private fun loadMessagesForConversation(conversation: Conversation) {
        scope.launch {
            messageRepository.getMessagesForConversation(conversation.id)
                .collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                }
        }
    }

    suspend fun createNewConversation() {
        val currentCoach = _uiState.value.currentCoach ?: return
        
        // Delete any blank conversations for this coach
        messageRepository.deleteEmptyConversations(currentCoach.id)
        
        val newConversation = messageRepository.createNewConversation(
            coachId = currentCoach.id,
            title = "Chat ${java.time.format.DateTimeFormatter.ISO_INSTANT.format(Instant.now())}"
        )
        
        // Clear messages and select the new conversation
        _uiState.update { it.copy(
            messages = emptyList(),
            currentConversation = newConversation
        )}
    }

    suspend fun selectConversation(conversation: Conversation) {
        if (_uiState.value.currentConversation?.id == conversation.id) return
        _uiState.update { it.copy(
            currentConversation = conversation,
            messages = emptyList() // Clear messages first
        )}
        
        // Load messages for the selected conversation
        loadMessagesForConversation(conversation)
    }

    suspend fun updateConversationTitle(title: String) {
        val conversation = _uiState.value.currentConversation ?: return
        messageRepository.updateConversationTitle(conversation.id, title)
    }

    suspend fun toggleConversationFavorite() {
        val conversation = _uiState.value.currentConversation ?: return
        messageRepository.toggleConversationFavorite(conversation.id, conversation.isFavorite)
    }

    private suspend fun ensureHeadCoach() {
        coachDao.createHeadCoach()
    }

    suspend fun selectCoach(coach: Coach) {
        if (_uiState.value.currentCoach?.id == coach.id) return
        _uiState.update { it.copy(currentCoach = coach) }
        
        // Check if coach has any conversations
        val conversations = conversationDao.getConversationsForCoach(coach.id).first()
        if (conversations.isEmpty()) {
            // Create a default conversation for new coach
            val newConversation = messageRepository.createNewConversation(
                coachId = coach.id,
                title = "First Chat with ${coach.name}"
            )
            
            // Select the new conversation
            selectConversation(newConversation)
        }
    }

    fun setLoading(isLoading: Boolean) {
        _uiState.update { it.copy(isLoading = isLoading) }
    }

    fun setError(error: String?) {
        _uiState.update { it.copy(error = error) }
    }

    fun showPatternRecommendation() {
        _uiState.update {
            it.copy(patternRecommendation = it.patternRecommendation.copy(isVisible = true))
        }
    }

    fun hidePatternRecommendation() {
        _uiState.update {
            it.copy(patternRecommendation = it.patternRecommendation.copy(isVisible = false))
        }
    }

    fun updatePatternFilters(filters: PatternFilters) {
        val currentFilters = _uiState.value.patternRecommendation.filters
        if (filters == currentFilters) {
            android.util.Log.d("PatternDebug", "Skipping filter update in state manager - filters unchanged")
            return
        }
        android.util.Log.d("PatternDebug", "Updating filters in state manager")
        _uiState.update {
            it.copy(patternRecommendation = it.patternRecommendation.copy(filters = filters))
        }
    }

    fun updateRecommendedPattern(pattern: Pattern?) {
        val currentPattern = _uiState.value.patternRecommendation.recommendedPattern
        if (pattern?.id == currentPattern?.id) {
            android.util.Log.d("PatternDebug", "Skipping pattern update in state manager - same pattern")
            return
        }
        android.util.Log.d("PatternDebug", "Updating recommended pattern in state manager from ${currentPattern?.name} to ${pattern?.name}")
        _uiState.update {
            it.copy(patternRecommendation = it.patternRecommendation.copy(recommendedPattern = pattern))
        }
    }

    fun selectPattern(pattern: Pattern?) {
        _uiState.update {
            it.copy(patternRecommendation = it.patternRecommendation.copy(selectedPattern = pattern))
        }
    }

    fun startPatternRun(pattern: Pattern) {
        _uiState.update { state ->
            state.copy(
                patternRun = PatternRunState(pattern = pattern),
                patternRecommendation = state.patternRecommendation.copy(isVisible = false)
            )
        }
    }

    fun startTimer() {
        _uiState.update { state ->
            state.copy(
                patternRun = state.patternRun?.copy(
                    isTimerRunning = true,
                    showEndButtons = true,
                    isCountingDown = true,
                    countdownTime = -5000L // Start with 5 second countdown
                )
            )
        }
    }

    fun updateTimer(elapsedTime: Long) {
        _uiState.value.patternRun?.let { currentRun ->
            if (currentRun.isCountingDown) {
                val newCountdownTime = currentRun.countdownTime + elapsedTime
                if (newCountdownTime >= 0) {
                    // Countdown finished, start the actual timer
                    _uiState.value = _uiState.value.copy(
                        patternRun = currentRun.copy(
                            isCountingDown = false,
                            countdownTime = 0L,
                            elapsedTime = 0L
                        )
                    )
                } else {
                    // Still counting down
                    _uiState.value = _uiState.value.copy(
                        patternRun = currentRun.copy(
                            countdownTime = newCountdownTime
                        )
                    )
                }
            } else {
                // Normal timer update
                _uiState.value = _uiState.value.copy(
                    patternRun = currentRun.copy(
                        elapsedTime = currentRun.elapsedTime + elapsedTime
                    )
                )
            }
        }
    }

    fun endPatternRun() {
        _uiState.update {
            it.copy(patternRun = null)
        }
    }

    fun cancelPatternRun() {
        endPatternRun()
    }
}
