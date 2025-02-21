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
import com.example.jugcoach.util.SettingsConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKeys: List<ApiKeyItem> = emptyList(),
    val patternCount: Int = 0,
    val isSaving: Boolean = false,
    val isImporting: Boolean = false,
    val message: String? = null,
    val routingModelName: String = "",
    val routingModelKey: String = "",
    val toolUseModelName: String = "",
    val toolUseModelKey: String = ""
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
        loadModelSettings()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Load pattern count
            patternDao.getCount(1L).collectLatest { count ->
                _uiState.update { it.copy(patternCount = count) }
            }
        }

        viewModelScope.launch {
            // Load API keys
            settingsDao.getSettingsByCategory(SettingCategory.API_KEY).collectLatest { settings ->
                android.util.Log.d("SettingsViewModel", "Loading API keys: ${settings.size} found")
                val apiKeys = settings.map { setting ->
                    ApiKeyItem(
                        name = setting.key,
                        value = setting.value
                    )
                }
                android.util.Log.d("SettingsViewModel", "API keys loaded: ${apiKeys.map { it.name }}")
                _uiState.update { it.copy(apiKeys = apiKeys) }
            }
        }
    }

    fun addApiKey(name: String = "") {
        val currentKeys = _uiState.value.apiKeys
        // If no name provided, generate a default one
        val newKeyName = if (name.isBlank()) {
            "API Key ${currentKeys.size + 1}"
        } else {
            name
        }
        val newKey = ApiKeyItem(name = newKeyName, value = "")
        android.util.Log.d("SettingsViewModel", "Adding new API key: $newKeyName")
        _uiState.update { it.copy(apiKeys = currentKeys + newKey) }
    }

    fun saveApiKey(name: String, value: String) {
        viewModelScope.launch {
            android.util.Log.d("SettingsViewModel", "Saving API key: $name")
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
                android.util.Log.d("SettingsViewModel", "API key saved successfully: $name")
                showMessage("API key saved successfully")
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to save API key", e)
                showMessage("Failed to save API key: ${e.message}")
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteApiKey(name: String) {
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsViewModel", "Deleting API key: $name")
                
                // Check if any coaches are using this API key
                val coaches = coachDao.getAllCoaches().first()
                val usingCoaches = coaches.filter { it.apiKeyName == name }
                
                if (usingCoaches.isNotEmpty()) {
                    android.util.Log.w("SettingsViewModel", "API key in use by coaches: ${usingCoaches.map { it.name }}")
                    showMessage("Cannot delete API key. It is being used by: ${usingCoaches.joinToString { it.name }}")
                    return@launch
                }

                settingsDao.deleteSettingByKey(name)
                android.util.Log.d("SettingsViewModel", "API key deleted successfully: $name")
                showMessage("API key deleted successfully")
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to delete API key", e)
                showMessage("Failed to delete API key: ${e.message}")
            }
        }
    }

    fun importPatternsFromUri(uri: Uri, asShared: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            try {
                android.util.Log.d("SettingsViewModel", "Starting pattern import from URI: $uri")
                val coachId = if (asShared) null else 1L
                patternImporter.importFromUri(uri, coachId, replaceExisting = true)
                android.util.Log.d("SettingsViewModel", "Patterns imported successfully")
                showMessage("Patterns imported successfully")
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to import patterns", e)
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

    fun deleteAllPatterns() {
        viewModelScope.launch {
            try {
                patternDao.deleteAllPatterns()
                showMessage("All patterns deleted successfully")
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to delete patterns", e)
                showMessage("Failed to delete patterns: ${e.message}")
            }
        }
    }

    private fun loadModelSettings() {
        viewModelScope.launch {
            try {
                val routingModelName = settingsDao.getSettingValue(SettingsConstants.ROUTING_MODEL_NAME_KEY) ?: ""
                val routingModelKey = settingsDao.getSettingValue(SettingsConstants.ROUTING_MODEL_KEY_KEY) ?: ""
                val toolUseModelName = settingsDao.getSettingValue(SettingsConstants.TOOL_USE_MODEL_NAME_KEY) ?: ""
                val toolUseModelKey = settingsDao.getSettingValue(SettingsConstants.TOOL_USE_MODEL_KEY_KEY) ?: ""

                _uiState.update {
                    it.copy(
                        routingModelName = routingModelName,
                        routingModelKey = routingModelKey,
                        toolUseModelName = toolUseModelName,
                        toolUseModelKey = toolUseModelKey
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to load model settings", e)
                showMessage("Failed to load model settings: ${e.message}")
            }
        }
    }

    fun saveModelSettings(
        routingModelName: String,
        routingModelKey: String,
        toolUseModelName: String,
        toolUseModelKey: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // Save routing model settings
                settingsDao.setSettingValue(
                    key = SettingsConstants.ROUTING_MODEL_NAME_KEY,
                    value = routingModelName,
                    type = SettingType.STRING,
                    category = SettingCategory.SYSTEM,
                    description = "Routing Model Name"
                )
                settingsDao.setSettingValue(
                    key = SettingsConstants.ROUTING_MODEL_KEY_KEY,
                    value = routingModelKey,
                    type = SettingType.STRING,
                    category = SettingCategory.SYSTEM,
                    description = "Routing Model API Key",
                    isEncrypted = true
                )

                // Save tool use model settings
                settingsDao.setSettingValue(
                    key = SettingsConstants.TOOL_USE_MODEL_NAME_KEY,
                    value = toolUseModelName,
                    type = SettingType.STRING,
                    category = SettingCategory.SYSTEM,
                    description = "Tool Use Model Name"
                )
                settingsDao.setSettingValue(
                    key = SettingsConstants.TOOL_USE_MODEL_KEY_KEY,
                    value = toolUseModelKey,
                    type = SettingType.STRING,
                    category = SettingCategory.SYSTEM,
                    description = "Tool Use Model API Key",
                    isEncrypted = true
                )

                _uiState.update {
                    it.copy(
                        routingModelName = routingModelName,
                        routingModelKey = routingModelKey,
                        toolUseModelName = toolUseModelName,
                        toolUseModelKey = toolUseModelKey
                    )
                }
                showMessage("Model settings saved successfully")
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to save model settings", e)
                showMessage("Failed to save model settings: ${e.message}")
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
