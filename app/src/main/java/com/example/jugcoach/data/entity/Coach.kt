package com.example.jugcoach.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "coaches",
    indices = [Index(value = ["name"], unique = true)]
)
data class Coach(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val apiKeyName: String, // References the key in settings table
    val description: String? = null,
    val specialties: String? = null, // Stored as JSON string
    val isHeadCoach: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val systemPrompt: String? = null
)
