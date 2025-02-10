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
            android.util.Log.d("ChatViewModel", "Updated coach ${currentCoach.name} with API key: $apiKeyName")
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
                android.util.Log.d("ChatViewModel", "Loaded coaches: ${coaches.map { it.name }}")
                android.util.Log.d("ChatViewModel", "Current coach: ${currentCoach?.name}")

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
                        android.util.Log.d("ChatViewModel", "Loaded ${messages.size} messages for coach ${coach.name}")
                    }
                }
            }
        }
    }

    private fun ensureHeadCoach() {
        viewModelScope.launch {
            coachDao.createHeadCoach()
            android.util.Log.d("ChatViewModel", "Ensured head coach exists")
        }
    }

    fun selectCoach(coach: Coach) {
        _uiState.update { it.copy(currentCoach = coach) }
        android.util.Log.d("ChatViewModel", "Selected coach: ${coach.name}")
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
                android.util.Log.d("ChatViewModel", "Loaded ${messages.size} messages for selected coach ${coach.name}")
            }
        }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            val currentCoach = _uiState.value.currentCoach ?: return@launch
            android.util.Log.d("ChatViewModel", "Starting sendMessage with text: $text")
            android.util.Log.d("ChatViewModel", "Current coach: ${currentCoach.name}")
            android.util.Log.d("ChatViewModel", "API key name: ${currentCoach.apiKeyName}")

            // Add user message
            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = text,
                sender = ChatMessage.Sender.USER,
                timestamp = Instant.now()
            )
            addMessage(userMessage)

            // Save user message to database
            val userNoteId = noteDao.insertNote(
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
            android.util.Log.d("ChatViewModel", "Saved user message with note ID: $userNoteId")

            // Get API key for current coach
            val apiKeyName = currentCoach.apiKeyName
            android.util.Log.d("ChatViewModel", "Fetching API key with name: $apiKeyName")
            android.util.Log.d("ChatViewModel", "Current coach details - Name: ${currentCoach.name}, ID: ${currentCoach.id}, isHeadCoach: ${currentCoach.isHeadCoach}")
            val apiKey = settingsDao.getSettingValue(apiKeyName)
            if (apiKey.isNullOrEmpty()) {
                android.util.Log.e("ChatViewModel", "No API key found for $apiKeyName")
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

            android.util.Log.d("ChatViewModel", "Found API key: ${apiKey.take(4)}...${apiKey.takeLast(4)}")
            android.util.Log.d("ChatViewModel", "API key length: ${apiKey.length}")
            android.util.Log.d("ChatViewModel", "API key format check - starts with 'sk-': ${apiKey.startsWith("sk-")}")

            // Show loading state
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Get last few messages for context (reversed to get correct chronological order)
                val recentMessages = _uiState.value.messages.takeLast(10)
                android.util.Log.d("ChatViewModel", "Using ${recentMessages.size} recent messages for context")

                val messageHistory = recentMessages.map { msg ->
                    AnthropicRequest.Message(
                        role = if (msg.sender == ChatMessage.Sender.USER) "user" else "assistant",
                        content = listOf(AnthropicRequest.Content(text = msg.text))
                    )
                }

                val request = AnthropicRequest(
                    messages = messageHistory + AnthropicRequest.Message(
                        role = "user",
                        content = listOf(AnthropicRequest.Content(text = text))
                    ),
                    system = currentCoach.systemPrompt
                )
                android.util.Log.d("ChatViewModel", "Making API request with message history")
                android.util.Log.d("ChatViewModel", "System prompt length: ${currentCoach.systemPrompt?.length ?: 0}")
                android.util.Log.d("ChatViewModel", "Message history size: ${messageHistory.size}")
                android.util.Log.d("ChatViewModel", "Request details:")
                android.util.Log.d("ChatViewModel", "- Model: ${request.model}")
                android.util.Log.d("ChatViewModel", "- Max tokens: ${request.maxTokens}")
                android.util.Log.d("ChatViewModel", "- Temperature: ${request.temperature}")
                android.util.Log.d("ChatViewModel", "- Message count: ${request.messages.size}")
                android.util.Log.d("ChatViewModel", "- System prompt present: ${!request.system.isNullOrEmpty()}")
                request.messages.forEachIndexed { index, msg ->
                    android.util.Log.d("ChatViewModel", "Message $index - Role: ${msg.role}, Content length: ${msg.content.firstOrNull()?.text?.length ?: 0}")
                }

                // Send message to Anthropic API
                android.util.Log.d("ChatViewModel", "Making API request to Anthropic")
                android.util.Log.d("ChatViewModel", "=== Request Details ===")
                android.util.Log.d("ChatViewModel", "API Key being used: ${apiKey.take(10)}...${apiKey.takeLast(4)}")
                android.util.Log.d("ChatViewModel", "Request object:")
                android.util.Log.d("ChatViewModel", "- Model: ${request.model}")
                android.util.Log.d("ChatViewModel", "- Max tokens: ${request.maxTokens}")
                android.util.Log.d("ChatViewModel", "- Temperature: ${request.temperature}")
                android.util.Log.d("ChatViewModel", "- System prompt: ${request.system}")
                android.util.Log.d("ChatViewModel", "- Messages:")
                request.messages.forEachIndexed { index, msg ->
                    android.util.Log.d("ChatViewModel", "  Message $index:")
                    android.util.Log.d("ChatViewModel", "    Role: ${msg.role}")
                    android.util.Log.d("ChatViewModel", "    Content: ${msg.content.firstOrNull()?.text}")
                }
                android.util.Log.d("ChatViewModel", "=====================")

                val response = anthropicService.sendMessage(
                    apiKey = apiKey,
                    request = request
                )
                android.util.Log.d("ChatViewModel", "Got API response:")
                android.util.Log.d("ChatViewModel", "- Response ID: ${response.id}")
                android.util.Log.d("ChatViewModel", "- Model: ${response.model}")
                android.util.Log.d("ChatViewModel", "- Type: ${response.type}")
                android.util.Log.d("ChatViewModel", "- Stop reason: ${response.stopReason}")
                android.util.Log.d("ChatViewModel", "- Usage stats:")
                android.util.Log.d("ChatViewModel", "  * Input tokens: ${response.usage.inputTokens}")
                android.util.Log.d("ChatViewModel", "  * Output tokens: ${response.usage.outputTokens}")
                android.util.Log.d("ChatViewModel", "  * Total tokens: ${response.usage.inputTokens + response.usage.outputTokens}")

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
                val coachNoteId = noteDao.insertNote(
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
                android.util.Log.d("ChatViewModel", "Saved coach message with note ID: $coachNoteId")

            } catch (e: retrofit2.HttpException) {
                android.util.Log.e("ChatViewModel", "HTTP error code: ${e.code()}")
                android.util.Log.e("ChatViewModel", "HTTP error message: ${e.message()}")
                android.util.Log.e("ChatViewModel", "HTTP error response: ${e.response()?.errorBody()?.string()}")
                android.util.Log.e("ChatViewModel", "Raw response headers: ${e.response()?.headers()}")
                android.util.Log.e("ChatViewModel", "Request URL: ${e.response()?.raw()?.request?.url}")
                
                val errorMessage = when (e.code()) {
                    401 -> "Invalid API key. Please check your settings."
                    429 -> "Too many requests. Please try again later."
                    else -> "API error: ${e.message()}"
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
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error class: ${e.javaClass.name}")
                android.util.Log.e("ChatViewModel", "Error in sendMessage", e)
                android.util.Log.e("ChatViewModel", "Error message: ${e.message}")
                android.util.Log.e("ChatViewModel", "Error cause: ${e.cause}")
                e.printStackTrace()

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
        android.util.Log.d("ChatViewModel", "Added message: ${message.sender} - ${message.text}")
    }
}
