package com.example.jugcoach

import android.app.Application
import com.example.jugcoach.data.JugCoachDatabase
import com.example.jugcoach.data.entity.SettingType
import com.example.jugcoach.data.entity.SettingCategory
import com.example.jugcoach.data.importer.PatternImporter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class JugCoachApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        initializeData()
    }

    companion object {
        private const val TAG = "JugCoachApplication"
    }

    private fun initializeData() {
        applicationScope.launch {
            val database = JugCoachDatabase.getDatabase(this@JugCoachApplication)
            val settingsDao = database.settingsDao()
            val coachDao = database.coachDao()
            val patternDao = database.patternDao()
            
            // Create head coach if not exists
            coachDao.createHeadCoach()

            // Set up default API key if not exists
            android.util.Log.d(TAG, "Checking for default API key")
            val defaultApiKey = settingsDao.getSettingValue("llm_api_key")
            if (defaultApiKey == null) {
                android.util.Log.d(TAG, "Creating default API key setting")
                settingsDao.setSettingValue(
                    key = "llm_api_key",
                    value = "",
                    type = SettingType.STRING,
                    category = SettingCategory.API_KEY,
                    description = "Default LLM API Key",
                    isEncrypted = true
                )
                android.util.Log.d(TAG, "Default API key setting created")
            } else {
                android.util.Log.d(TAG, "Default API key already exists")
            }

            // Check if we need to import patterns
            val patternsImported = settingsDao.getSettingValue("patterns_imported") == "true"
            val patternCount = patternDao.getAllPatternsSync(-1).size

            android.util.Log.d(TAG, "Patterns imported flag: $patternsImported")
            android.util.Log.d(TAG, "Current pattern count: $patternCount")

            // Import patterns if not imported or if database is empty
            if (!patternsImported || patternCount == 0) {
                // Remove automatic pattern import since we only want user-supplied patterns
                android.util.Log.d(TAG, "No automatic pattern import - waiting for user to import patterns")
                settingsDao.setSettingValue(
                    key = "patterns_imported",
                    value = "true", // Set to true to prevent further import attempts
                    type = SettingType.BOOLEAN,
                    category = SettingCategory.SYSTEM,
                    description = "Flag indicating if patterns have been imported"
                )
            }

            // Verify pattern count after import
            val finalPatternCount = patternDao.getAllPatternsSync(-1).size
            android.util.Log.d(TAG, "Final pattern count: $finalPatternCount")
        }
    }
}
