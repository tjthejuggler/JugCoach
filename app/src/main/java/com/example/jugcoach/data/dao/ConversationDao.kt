package com.example.jugcoach.data.dao

import androidx.room.*
import com.example.jugcoach.data.entity.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE coachId = :coachId ORDER BY lastMessageAt DESC")
    fun getConversationsForCoach(coachId: Long): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): Conversation?

    @Insert
    suspend fun insert(conversation: Conversation): Long

    @Update
    suspend fun update(conversation: Conversation)

    @Delete
    suspend fun delete(conversation: Conversation)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String?)

    @Query("UPDATE conversations SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE conversations SET lastMessageAt = :timestamp WHERE id = :id")
    suspend fun updateLastMessageTime(id: Long, timestamp: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    @Query("DELETE FROM conversations WHERE id = :id AND NOT EXISTS (SELECT 1 FROM chat_messages WHERE conversationId = :id)")
    suspend fun deleteIfEmpty(id: Long)
}
