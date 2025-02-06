package com.example.jugcoach.ui.chat

import java.time.Instant

data class ChatMessage(
    val id: String,
    val text: String,
    val sender: Sender,
    val timestamp: Instant,
    val isError: Boolean = false
) {
    enum class Sender {
        USER,
        COACH
    }
}
