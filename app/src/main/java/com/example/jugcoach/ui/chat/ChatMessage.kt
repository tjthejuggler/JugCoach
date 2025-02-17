package com.example.jugcoach.ui.chat

import java.time.Instant

data class ChatMessage(
    val id: String,
    val text: String,
    val sender: Sender,
    val timestamp: Instant,
    val isError: Boolean = false,
    val messageType: MessageType = MessageType.TALKING,
    val isInternal: Boolean = false,
    val model: String? = null,
    val apiKeyName: String? = null
) {
    enum class Sender {
        USER, COACH
    }

    enum class MessageType {
        TALKING, THINKING, ACTION, RUN_SUMMARY
    }
}
