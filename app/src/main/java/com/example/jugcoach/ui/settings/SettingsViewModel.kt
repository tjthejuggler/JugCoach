package com.example.jugcoach.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.dao.SettingsDao
import com.example.jugcoach.data.dao.CoachDao
import com.example.jugcoach.data.entity.SettingCategory
import com.example.jugcoach.data.entity.SettingType
import com.example.jugcoach.data.entity.Settings
import com.example.jugcoach.data.importer.PatternImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKeys: List<ApiKeyItem> = emptyList(),
    val patternCount: Int = 0,
    val isSaving: Boolean = false,
    val isImporting: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    private val patternDao: PatternDao,
    private val coachDao: CoachDao,
    private val patternImporter: PatternImporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private suspend fun getCurrentCoachId(): Long {
        return coachDao.getAllCoaches().first().find { it.isHeadCoach }?.id ?: 1L
    }

    private fun loadData() {
        viewModelScope.launch {
            val coachId = getCurrentCoachId()
            // Load pattern count
            patternDao.getCount(coachId).collectLatest { count ->
                _uiState.update { it.copy(patternCount = count) }
            }
        }

        viewModelScope.launch {
            // Load API keys
            settingsDao.getSettingsByCategory(SettingCategory.API_KEY).collectLatest { settings ->
                val apiKeys = settings.map { setting ->
                    ApiKeyItem(
                        name = setting.key,
                        value = setting.value
                    )
                }
                _uiState.update { it.copy(apiKeys = apiKeys) }
            }
        }
    }

    fun addApiKey() {
        val currentKeys = _uiState.value.apiKeys
        val newKeyName = "llm_api_key_${currentKeys.size + 1}"
        val newKey = ApiKeyItem(name = newKeyName, value = "")
        _uiState.update { it.copy(apiKeys = currentKeys + newKey) }
    }

    fun saveApiKey(name: String, value: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                settingsDao.setSettingValue(
                    key = name,
                    value = value,
                    type = SettingType.STRING,
                    category = SettingCategory.API_KEY,
                    description = "LLM API Key",
                    isEncrypted = true
                )
                showMessage("API key saved successfully")
            } catch (e: Exception) {
                showMessage("Failed to save API key: ${e.message}")
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteApiKey(name: String) {
        viewModelScope.launch {
            try {
                settingsDao.deleteSettingByKey(name)
                showMessage("API key deleted successfully")
            } catch (e: Exception) {
                showMessage("Failed to delete API key: ${e.message}")
            }
        }
    }

    fun importPatternsFromUri(uri: Uri, asShared: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            try {
                val coachId = if (asShared) null else getCurrentCoachId()
                patternImporter.importFromUri(uri, coachId)
                showMessage("Patterns imported successfully")
            } catch (e: Exception) {
                showMessage("Failed to import patterns: ${e.message}")
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
