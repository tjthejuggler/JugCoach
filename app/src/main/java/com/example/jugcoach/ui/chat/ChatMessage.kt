package com.example.jugcoach.ui.chat

import java.time.Instant

data class ChatMessage(
    val id: String,
    val text: String,
    val sender: Sender,
    val timestamp: Instant,
    val isError: Boolean = false,
    val messageType: MessageType = MessageType.TALKING,
    val isInternal: Boolean = false // NEW: internal messages are not shown to the user
) {
    enum class Sender {
        USER,
        COACH
    }

    enum class MessageType {
        ACTION,    // When performing an action/tool use
        TALKING,   // When communicating with the user
        THINKING   // Internal thoughts (shown in different color)
    }
}
