package com.example.jugcoach.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.JugCoachDatabase
import com.example.jugcoach.data.entity.Note
import com.example.jugcoach.data.entity.NoteType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = JugCoachDatabase.getDatabase(application)
    private val settingsDao = database.settingsDao()
    private val noteDao = database.noteDao()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            noteDao.getAllNotes().collect { notes ->
                val messages = notes.map { note ->
                    ChatMessage(
                        id = note.id.toString(), // Keep toString() for ChatMessage
                        text = note.content,
                        sender = if (note.type == NoteType.COACHING_NOTE) ChatMessage.Sender.COACH else ChatMessage.Sender.USER,
                        timestamp = note.createdAt
                    )
                }
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
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
                    id = 0, // Let Room auto-generate
                    title = "Chat Message",
                    content = userMessage.text,
                    type = NoteType.CONVERSATION,
                    tags = listOf("chat", "user"),
                    metadata = "{\"messageId\": \"${userMessage.id}\"}"
                )
            )

            // Get API key
            val apiKey = settingsDao.getSettingValue("llm_api_key")
            if (apiKey.isNullOrEmpty()) {
                addMessage(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = "Please set up your API key in Settings to chat with the coach.",
                        sender = ChatMessage.Sender.COACH,
                        timestamp = Instant.now(),
                        isError = true
                    )
                )
                return@launch
            }

            // Show loading state
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // TODO: Implement LLM API call
                val response = "This is a placeholder response. LLM integration coming soon!"
                
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
                    id = 0, // Let Room auto-generate
                    title = "Coach Response",
                    content = coachMessage.text,
                    type = NoteType.COACHING_NOTE,
                    tags = listOf("chat", "coach"),
                    metadata = "{\"messageId\": \"${coachMessage.id}\"}"
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
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + message
        )
    }
}
