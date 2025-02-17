package com.example.jugcoach.ui.chat

import com.example.jugcoach.data.dao.ChatMessageDao
import com.example.jugcoach.data.dao.ConversationDao
import com.example.jugcoach.data.entity.ChatMessage as DbChatMessage
import com.example.jugcoach.ui.chat.ChatMessage
import com.example.jugcoach.data.entity.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class ChatMessageRepository @Inject constructor(
    private val chatMessageDao: ChatMessageDao,
    private val conversationDao: ConversationDao
) {
    suspend fun getOrCreateConversation(
        existingConversation: Conversation?,
        coachId: Long,
        title: String
    ): Conversation {
        return conversationDao.getOrCreateConversation(existingConversation, coachId, title)
    }

    fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesForConversation(conversationId)
            .distinctUntilChanged()
            .map { messages ->
                messages.map { msg ->
                    ChatMessage(
                        id = msg.id.toString(),
                        text = msg.text,
                        sender = if (msg.isFromUser) ChatMessage.Sender.USER else ChatMessage.Sender.COACH,
                        timestamp = Instant.ofEpochMilli(msg.timestamp),
                        isError = msg.isError,
                        messageType = when (msg.messageType) {
                            DbChatMessage.MessageType.RUN_SUMMARY -> ChatMessage.MessageType.RUN_SUMMARY
                            DbChatMessage.MessageType.ACTION -> ChatMessage.MessageType.ACTION
                            DbChatMessage.MessageType.THINKING -> ChatMessage.MessageType.THINKING
                            DbChatMessage.MessageType.TALKING -> ChatMessage.MessageType.TALKING
                        },
                        isInternal = msg.isInternal,
                        model = msg.model,
                        apiKeyName = msg.apiKeyName
                    )
                }
            }
    }

    suspend fun saveMessage(
        conversationId: Long,
        text: String,
        isFromUser: Boolean,
        isError: Boolean = false,
        isInternal: Boolean = false,
        model: String? = null,
        apiKeyName: String? = null,
        messageType: DbChatMessage.MessageType = DbChatMessage.MessageType.TALKING
    ) {
        val timestamp = System.currentTimeMillis()
        val message = DbChatMessage(
            conversationId = conversationId,
            text = text,
            isFromUser = isFromUser,
            timestamp = timestamp,
            isError = isError,
            isInternal = isInternal,
            model = model,
            apiKeyName = apiKeyName,
            messageType = messageType
        )
        chatMessageDao.insertAndUpdateConversation(message, conversationId, timestamp)
    }

    suspend fun getConversationsForCoach(coachId: Long): Flow<List<Conversation>> {
        return conversationDao.getConversationsForCoach(coachId)
            .distinctUntilChanged()
    }

    suspend fun createNewConversation(coachId: Long, title: String): Conversation {
        val timestamp = System.currentTimeMillis()
        val conversation = Conversation(
            coachId = coachId,
            title = title,
            createdAt = timestamp,
            lastMessageAt = timestamp
        )
        val id = conversationDao.insert(conversation)
        return conversation.copy(id = id)
    }

    suspend fun updateConversationTitle(conversationId: Long, title: String) {
        conversationDao.updateTitle(conversationId, title)
    }

    suspend fun toggleConversationFavorite(conversationId: Long, currentFavorite: Boolean) {
        conversationDao.updateFavorite(conversationId, !currentFavorite)
    }

    suspend fun deleteEmptyConversations(coachId: Long) {
        val conversations = conversationDao.getConversationsForCoach(coachId).distinctUntilChanged().first()
        for (conversation in conversations) {
            conversationDao.deleteIfEmpty(conversation.id)
        }
    }
}
