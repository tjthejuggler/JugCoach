package com.example.jugcoach.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.api.AnthropicRequest
import com.example.jugcoach.data.api.AnthropicService
import com.example.jugcoach.data.dao.CoachDao
import com.example.jugcoach.data.dao.NoteDao
import com.example.jugcoach.data.dao.SettingsDao
import com.example.jugcoach.data.entity.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentCoach: Coach? = null,
    val availableCoaches: List<Coach> = emptyList()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    private val noteDao: NoteDao,
    private val coachDao: CoachDao,
    private val anthropicService: AnthropicService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _availableApiKeys = MutableStateFlow<List<String>>(emptyList())
    val availableApiKeys: StateFlow<List<String>> = _availableApiKeys.asStateFlow()

    init {
        loadData()
        ensureHeadCoach()
        loadApiKeys()
    }

    private fun loadApiKeys() {
        viewModelScope.launch {
            settingsDao.getSettingsByCategory(SettingCategory.API_KEY)
                .collect { settings ->
                    // Filter out empty values and map to key names
                    val validKeys = settings
                        .filter { it.value.isNotBlank() }
                        .map { it.key }
                    _availableApiKeys.value = validKeys
                    
                    // Log for debugging
                    android.util.Log.d("ChatViewModel", "Loaded API keys: $validKeys")
                }
        }
    }

    fun updateCoachApiKey(apiKeyName: String) {
        viewModelScope.launch {
            val currentCoach = _uiState.value.currentCoach ?: return@launch
            val updatedCoach = currentCoach.copy(apiKeyName = apiKeyName)
            coachDao.updateCoach(updatedCoach)
            _uiState.update { it.copy(currentCoach = updatedCoach) }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            // Load coaches first
            coachDao.getAllCoaches().collectLatest { coaches ->
                val currentCoach = coaches.find { it.isHeadCoach } ?: coaches.firstOrNull()
                _uiState.update { state ->
                    state.copy(
                        availableCoaches = coaches,
                        currentCoach = currentCoach
                    )
                }

                // Only load messages if we have a current coach
                currentCoach?.let { coach ->
                    // Load messages for current coach
                    noteDao.getAllNotes(coach.id).collectLatest { notes ->
                        val messages = notes.map { note ->
                            ChatMessage(
                                id = note.id.toString(),
                                text = note.content,
                                sender = if (note.type == NoteType.COACHING_NOTE) ChatMessage.Sender.COACH else ChatMessage.Sender.USER,
                                timestamp = note.createdAt
                            )
                        }
                        _uiState.update { it.copy(messages = messages) }
                    }
                }
            }
        }
    }

    private fun ensureHeadCoach() {
        viewModelScope.launch {
            coachDao.createHeadCoach()
        }
    }

    fun selectCoach(coach: Coach) {
        _uiState.update { it.copy(currentCoach = coach) }
        // Reload messages for new coach
        viewModelScope.launch {
            noteDao.getAllNotes(coach.id).collectLatest { notes ->
                val messages = notes.map { note ->
                    ChatMessage(
                        id = note.id.toString(),
                        text = note.content,
                        sender = if (note.type == NoteType.COACHING_NOTE) ChatMessage.Sender.COACH else ChatMessage.Sender.USER,
                        timestamp = note.createdAt
                    )
                }
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            val currentCoach = _uiState.value.currentCoach ?: return@launch

            // Add user message
            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = text,
                sender = ChatMessage.Sender.USER,
                timestamp = Instant.now()
            )
            addMessage(userMessage)

            // Save user message to database
            noteDao.insertNote(
                Note(
                    id = 0,
                    coachId = currentCoach.id,
                    title = "Chat Message",
                    content = userMessage.text,
                    type = NoteType.CONVERSATION,
                    tags = listOf("chat", "user"),
                    metadata = "{\"messageId\": \"${userMessage.id}\", \"coachId\": \"${currentCoach.id}\"}"
                )
            )

            // Get API key for current coach
            val apiKey = settingsDao.getSettingValue(currentCoach.apiKeyName)
            android.util.Log.d("ChatViewModel", "Using API key: ${apiKey?.take(4)}...${apiKey?.takeLast(4)}")
            if (apiKey.isNullOrEmpty()) {
                addMessage(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = "Please set up the API key '${currentCoach.apiKeyName}' in Settings to chat with ${currentCoach.name}.",
                        sender = ChatMessage.Sender.COACH,
                        timestamp = Instant.now(),
                        isError = true
                    )
                )
                return@launch
            }

            // Show loading state
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Get last few messages for context (reversed to get correct chronological order)
                val recentMessages = _uiState.value.messages.reversed().takeLast(10)
                val messageHistory = recentMessages.map { msg ->
                    AnthropicRequest.Message(
                        role = if (msg.sender == ChatMessage.Sender.USER) "user" else "assistant",
                        text = msg.text
                    )
                }

                android.util.Log.d("ChatViewModel", "Sending message to API with history size: ${messageHistory.size}")
                android.util.Log.d("ChatViewModel", "API Key name: ${currentCoach.apiKeyName}")
                android.util.Log.d("ChatViewModel", "System prompt: ${currentCoach.systemPrompt}")

                val request = AnthropicRequest(
                    messages = messageHistory + AnthropicRequest.Message(
                        role = "user",
                        text = text
                    ),
                    system = currentCoach.systemPrompt
                )
                android.util.Log.d("ChatViewModel", "Request: $request")

                // Send message to Anthropic API
                val response = anthropicService.sendMessage(
                    apiKey = apiKey,
                    request = request
                )
                
                android.util.Log.d("ChatViewModel", "Got API response: $response")
                val responseText = response.content.firstOrNull()?.text ?: "No response from the coach"
                android.util.Log.d("ChatViewModel", "Response text: $responseText")

                // Add coach response
                val coachMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = responseText,
                    sender = ChatMessage.Sender.COACH,
                    timestamp = Instant.now()
                )
                addMessage(coachMessage)

                // Save coach message to database
                noteDao.insertNote(
                    Note(
                        id = 0,
                        coachId = currentCoach.id,
                        title = "Coach Response",
                        content = coachMessage.text,
                        type = NoteType.COACHING_NOTE,
                        tags = listOf("chat", "coach"),
                        metadata = "{\"messageId\": \"${coachMessage.id}\", \"coachId\": \"${currentCoach.id}\"}"
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "API call failed", e)
                val errorMessage = when {
                    e.message?.contains("401") == true -> "Invalid API key. Please check your settings."
                    e.message?.contains("timeout") == true -> "Request timed out. Please try again."
                    else -> "Failed to get response: ${e.message}"
                }
                addMessage(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = errorMessage,
                        sender = ChatMessage.Sender.COACH,
                        timestamp = Instant.now(),
                        isError = true
                    )
                )
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.update { it.copy(messages = it.messages + message) }
    }
}
