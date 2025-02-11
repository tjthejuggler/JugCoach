package com.example.jugcoach.data.dao

import androidx.room.*
import com.example.jugcoach.data.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChatMessageDao {
    @Query("SELECT EXISTS(SELECT 1 FROM conversations WHERE id = :conversationId)")
    protected abstract suspend fun conversationExists(conversationId: Long): Boolean

    @Query("""
        UPDATE conversations 
        SET lastMessageAt = :timestamp 
        WHERE id = :conversationId
    """)
    protected abstract suspend fun updateConversationLastMessageTime(conversationId: Long, timestamp: Long)

    @Transaction
    open suspend fun insertAndUpdateConversation(message: ChatMessage, conversationId: Long, timestamp: Long): Long {
        // Verify conversation exists before inserting message
        if (!conversationExists(conversationId)) {
            throw IllegalStateException("Conversation with id $conversationId does not exist")
        }
        
        val messageId = insert(message)
        updateConversationLastMessageTime(conversationId, timestamp)
        return messageId
    }

    @Query("""
        SELECT * FROM chat_messages 
        WHERE conversationId = :conversationId 
        ORDER BY timestamp ASC 
        LIMIT 100
    """)
    abstract fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessage>>

    @Query("""
        SELECT COUNT(*) 
        FROM chat_messages 
        WHERE conversationId = :conversationId
    """)
    abstract suspend fun getMessageCount(conversationId: Long): Int

    @Insert
    abstract suspend fun insert(message: ChatMessage): Long

    @Insert
    abstract suspend fun insertAll(messages: List<ChatMessage>)

    @Update
    abstract suspend fun update(message: ChatMessage)

    @Delete
    abstract suspend fun delete(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    abstract suspend fun deleteAllMessagesFromConversation(conversationId: Long)

    @Transaction
    @Query("""
        SELECT chat_messages.* FROM chat_messages 
        INNER JOIN conversations ON chat_messages.conversationId = conversations.id 
        WHERE conversations.coachId = :coachId
        ORDER BY chat_messages.timestamp DESC
        LIMIT 100
    """)
    abstract suspend fun getAllMessagesForCoach(coachId: Long): List<ChatMessage>
}
