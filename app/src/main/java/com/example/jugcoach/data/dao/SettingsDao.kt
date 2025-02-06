package com.example.jugcoach.data.dao

import androidx.room.*
import com.example.jugcoach.data.entity.Settings
import com.example.jugcoach.data.entity.SettingCategory
import com.example.jugcoach.data.entity.SettingType
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings ORDER BY key")
    fun getAllSettings(): Flow<List<Settings>>

    @Query("SELECT * FROM settings WHERE category = :category")
    fun getSettingsByCategory(category: SettingCategory): Flow<List<Settings>>

    @Query("SELECT * FROM settings WHERE key = :key")
    suspend fun getSetting(key: String): Settings?

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun getSettingValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: Settings)

    @Update
    suspend fun updateSetting(setting: Settings)

    @Delete
    suspend fun deleteSetting(setting: Settings)

    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun deleteSettingByKey(key: String)

    @Transaction
    suspend fun setSettingValue(
        key: String,
        value: String,
        type: SettingType,
        category: SettingCategory,
        description: String? = null,
        isEncrypted: Boolean = false
    ) {
        val setting = Settings(
            key = key,
            value = value,
            type = type,
            category = category,
            description = description,
            isEncrypted = isEncrypted
        )
        insertSetting(setting)
    }

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM settings 
            WHERE category = 'API_KEY' 
            AND key = 'llm_api_key' 
            AND value IS NOT NULL 
            AND value != ''
        )
    """)
    suspend fun hasValidLlmApiKey(): Boolean
}
