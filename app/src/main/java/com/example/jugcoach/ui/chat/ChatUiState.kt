package com.example.jugcoach.ui.chat

import com.example.jugcoach.data.entity.Coach
import com.example.jugcoach.data.entity.Conversation

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentCoach: Coach? = null,
    val availableCoaches: List<Coach> = emptyList(),
    val currentConversation: Conversation? = null,
    val availableConversations: List<Conversation> = emptyList()
)
