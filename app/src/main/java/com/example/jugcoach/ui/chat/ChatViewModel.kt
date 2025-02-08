package com.example.jugcoach.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.dao.CoachDao
import com.example.jugcoach.data.dao.NoteDao
import com.example.jugcoach.data.dao.SettingsDao
import com.example.jugcoach.data.entity.Coach
import com.example.jugcoach.data.entity.Note
import com.example.jugcoach.data.entity.NoteType
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
    private val coachDao: CoachDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadData()
        ensureHeadCoach()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Load messages
            noteDao.getAllNotes().collectLatest { notes ->
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

        viewModelScope.launch {
            // Load coaches
            coachDao.getAllCoaches().collectLatest { coaches ->
                _uiState.update { state ->
                    state.copy(
                        availableCoaches = coaches,
                        currentCoach = state.currentCoach ?: coaches.find { it.isHeadCoach } ?: coaches.firstOrNull()
                    )
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
                    title = "Chat Message",
                    content = userMessage.text,
                    type = NoteType.CONVERSATION,
                    tags = listOf("chat", "user"),
                    metadata = "{\"messageId\": \"${userMessage.id}\", \"coachId\": \"${currentCoach.id}\"}"
                )
            )

            // Get API key for current coach
            val apiKey = settingsDao.getSettingValue(currentCoach.apiKeyName)
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
                // TODO: Implement LLM API call with coach-specific configuration
                val response = "This is a placeholder response from ${currentCoach.name}. LLM integration coming soon!"
                
                // Add coach response
                val coachMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = response,
                    sender = ChatMessage.Sender.COACH,
                    timestamp = Instant.now()
                )
                addMessage(coachMessage)

                // Save coach message to database
                noteDao.insertNote(
                    Note(
                        id = 0,
                        title = "Coach Response",
                        content = coachMessage.text,
                        type = NoteType.COACHING_NOTE,
                        tags = listOf("chat", "coach"),
                        metadata = "{\"messageId\": \"${coachMessage.id}\", \"coachId\": \"${currentCoach.id}\"}"
                    )
                )
            } catch (e: Exception) {
                addMessage(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = "Failed to get response: ${e.message}",
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
