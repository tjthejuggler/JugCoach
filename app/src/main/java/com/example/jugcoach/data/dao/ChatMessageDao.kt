package com.example.jugcoach.data.dao

import androidx.room.*
import com.example.jugcoach.data.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("""
        SELECT * FROM chat_messages 
        WHERE conversationId = :conversationId 
        ORDER BY timestamp ASC 
        LIMIT 100
    """)
    fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessage>>

    @Query("""
        SELECT COUNT(*) 
        FROM chat_messages 
        WHERE conversationId = :conversationId
    """)
    suspend fun getMessageCount(conversationId: Long): Int

    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Insert
    suspend fun insertAll(messages: List<ChatMessage>)

    @Update
    suspend fun update(message: ChatMessage)

    @Delete
    suspend fun delete(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteAllMessagesFromConversation(conversationId: Long)

    @Transaction
    @Query("""
        SELECT chat_messages.* FROM chat_messages 
        INNER JOIN conversations ON chat_messages.conversationId = conversations.id 
        WHERE conversations.coachId = :coachId
        ORDER BY chat_messages.timestamp DESC
        LIMIT 100
    """)
    suspend fun getAllMessagesForCoach(coachId: Long): List<ChatMessage>
}
