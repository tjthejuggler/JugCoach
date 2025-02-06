package com.example.jugcoach.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey
    val key: String,
    val value: String,
    val type: SettingType,
    val category: SettingCategory,
    val description: String? = null,
    // For sensitive data like API keys
    val isEncrypted: Boolean = false
)

enum class SettingType {
    STRING,
    BOOLEAN,
    INTEGER,
    FLOAT,
    JSON
}

enum class SettingCategory {
    API_KEY,
    USER_PREFERENCE,
    TRACKING_CONFIG,
    COACHING_CONFIG,
    SYSTEM
}
