package com.example.jugcoach.data.converter

import androidx.room.TypeConverter
import com.example.jugcoach.data.entity.ChatMessage

class MessageTypeConverter {
    @TypeConverter
    fun fromMessageType(messageType: ChatMessage.MessageType): String {
        return messageType.name
    }

    @TypeConverter
    fun toMessageType(value: String): ChatMessage.MessageType {
        return ChatMessage.MessageType.valueOf(value)
    }
}
