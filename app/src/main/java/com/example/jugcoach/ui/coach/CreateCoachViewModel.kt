package com.example.jugcoach.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.dao.CoachDao
import com.example.jugcoach.data.dao.SettingsDao
import com.example.jugcoach.data.entity.Coach
import com.example.jugcoach.data.entity.SettingCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateCoachViewModel @Inject constructor(
    private val coachDao: CoachDao,
    private val settingsDao: SettingsDao
) : ViewModel() {

    private val _apiKeys = MutableStateFlow<List<String>>(emptyList())
    val apiKeys: StateFlow<List<String>> = _apiKeys.asStateFlow()

    init {
        loadApiKeys()
    }

    private fun loadApiKeys() {
        viewModelScope.launch {
            settingsDao.getSettingsByCategory(SettingCategory.API_KEY).collectLatest { settings ->
                _apiKeys.value = settings.map { it.key }
            }
        }
    }

    fun createCoach(
        name: String,
        apiKeyName: String,
        description: String? = null,
        specialties: String? = null,
        systemPrompt: String? = null
    ) {
        viewModelScope.launch {
            val coach = Coach(
                name = name,
                apiKeyName = apiKeyName,
                description = description,
                specialties = specialties,
                systemPrompt = systemPrompt
            )
            coachDao.insertCoach(coach)
        }
    }
}
