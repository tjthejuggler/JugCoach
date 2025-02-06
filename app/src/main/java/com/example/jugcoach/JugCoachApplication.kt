package com.example.jugcoach

import android.app.Application
import com.example.jugcoach.data.JugCoachDatabase
import com.example.jugcoach.data.entity.Settings
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

    private fun initializeData() {
        applicationScope.launch {
            val database = JugCoachDatabase.getDatabase(this@JugCoachApplication)
            val settingsDao = database.settingsDao()
            val settings = settingsDao.getSettings()

            if (settings == null || !settings.patternsImported) {
                // Import patterns
                val patternImporter = PatternImporter(this@JugCoachApplication, database.patternDao())
                try {
                    val count = patternImporter.importLegacyPatterns()
                    // Update settings to mark patterns as imported
                    settingsDao.insertSettings(
                        Settings(
                            id = 1, // Single settings instance
                            patternsImported = true,
                            llmApiKey = null,
                            lastSync = null
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Handle import failure
                }
            }
        }
    }
}
