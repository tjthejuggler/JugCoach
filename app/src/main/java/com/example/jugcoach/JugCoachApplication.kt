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

    private fun initializeData() {
        applicationScope.launch {
            val database = JugCoachDatabase.getDatabase(this@JugCoachApplication)
            val settingsDao = database.settingsDao()
            val patternsImported = settingsDao.getSettingValue("patterns_imported") == "true"

            if (!patternsImported) {
                // Import patterns
                val patternImporter = PatternImporter(this@JugCoachApplication, database.patternDao())
                try {
                    val count = patternImporter.importLegacyPatterns()
                    // Update settings to mark patterns as imported
                    settingsDao.setSettingValue(
                        key = "patterns_imported",
                        value = "true",
                        type = SettingType.BOOLEAN,
                        category = SettingCategory.SYSTEM,
                        description = "Flag indicating if patterns have been imported"
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Handle import failure
                }
            }
        }
    }
}
