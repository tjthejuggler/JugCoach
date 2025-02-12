package com.example.jugcoach.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("conversationId"),
        Index(value = ["conversationId", "timestamp"]), // Compound index for optimized queries
        Index(value = ["timestamp"]) // Index for sorting
    ]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val messageType: MessageType = MessageType.TALKING,
    val isInternal: Boolean = false // NEW: internal messages are not shown to the user
) {
    enum class MessageType {
        ACTION,    // When performing an action/tool use
        TALKING,   // When communicating with the user
        THINKING   // Internal thoughts (shown in different color)
    }
}
