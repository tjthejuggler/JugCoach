package com.example.jugcoach.data.dao

import androidx.room.*
import com.example.jugcoach.data.entity.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ConversationDao {
    @Transaction
    open suspend fun getOrCreateConversation(
        existingConversation: Conversation?,
        coachId: Long,
        title: String
    ): Conversation {
        // If we have an existing conversation, verify it still exists and return it
        if (existingConversation != null) {
            getConversationById(existingConversation.id)?.let { return it }
        }

        // Create a new conversation
        val timestamp = System.currentTimeMillis()
        val newConversation = Conversation(
            coachId = coachId,
            title = title,
            createdAt = timestamp,
            lastMessageAt = timestamp
        )
        val id = insert(newConversation)
        
        // Verify the conversation was created and return it
        return getConversationById(id) ?: throw IllegalStateException("Failed to create conversation")
    }

    @Query("SELECT * FROM conversations WHERE coachId = :coachId ORDER BY lastMessageAt DESC")
    abstract fun getConversationsForCoach(coachId: Long): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    abstract suspend fun getConversationById(id: Long): Conversation?

    @Insert
    abstract suspend fun insert(conversation: Conversation): Long

    @Update
    abstract suspend fun update(conversation: Conversation)

    @Delete
    abstract suspend fun delete(conversation: Conversation)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    abstract suspend fun updateTitle(id: Long, title: String?)

    @Query("UPDATE conversations SET isFavorite = :isFavorite WHERE id = :id")
    abstract suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE conversations SET lastMessageAt = :timestamp WHERE id = :id")
    abstract suspend fun updateLastMessageTime(id: Long, timestamp: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    abstract suspend fun deleteConversation(id: Long)

    @Query("DELETE FROM conversations WHERE id = :id AND NOT EXISTS (SELECT 1 FROM chat_messages WHERE conversationId = :id)")
    abstract suspend fun deleteIfEmpty(id: Long)
}
