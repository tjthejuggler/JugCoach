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
import com.example.jugcoach.data.firebase.AuthRepository
import com.example.jugcoach.data.importer.PatternImporter
import com.example.jugcoach.util.SettingsConstants
import com.google.firebase.auth.FirebaseUser
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
    val toolUseModelKey: String = "",
    val isLoggingIn: Boolean = false,
    val isLoggedIn: Boolean = false,
    val username: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    private val patternDao: PatternDao,
    private val coachDao: CoachDao,
    private val patternImporter: PatternImporter,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadData()
        loadModelSettings()
        observeAuthState()
    }
    
    private fun observeAuthState() {
        viewModelScope.launch {
            // Observe current Firebase user
            authRepository.currentUser.collect { user ->
                _uiState.update {
                    it.copy(
                        isLoggedIn = user != null
                    )
                }
            }
        }
        
        viewModelScope.launch {
            // Observe username
            authRepository.username.collect { username ->
                _uiState.update {
                    it.copy(
                        username = username
                    )
                }
            }
        }
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
    
    /**
     * Login to Skilldex using username and password
     */
    fun login(usernameOrEmail: String, password: String) {
        if (usernameOrEmail.isBlank() || password.isBlank()) {
            showMessage("Username/email and password are required")
            return
        }
        
        android.util.Log.d("RecordSync", "Login attempt started with input: $usernameOrEmail")
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true) }
            try {
                // First, try to log in directly if the input looks like an email
                val result = if (usernameOrEmail.contains('@')) {
                    android.util.Log.d("RecordSync", "Attempting direct email login with: $usernameOrEmail")
                    authRepository.login(usernameOrEmail, password)
                } else {
                    android.util.Log.d("RecordSync", "Attempting username login with: $usernameOrEmail")
                    // Otherwise, try the username lookup
                    authRepository.loginWithUsername(usernameOrEmail, password)
                }
                
                result.onSuccess { user ->
                    android.util.Log.d("RecordSync", "Login successful for user: ${user.email}")
                    android.util.Log.d("RecordSync", "User ID: ${user.uid}")
                    android.util.Log.d("RecordSync", "Is email verified: ${user.isEmailVerified}")
                    
                    // Check if username was properly set after login
                    android.util.Log.d("RecordSync", "Current username from repository: ${authRepository.username.value}")
                    
                    // Give a slight delay to make sure Firebase auth state is updated
                    viewModelScope.launch {
                        try {
                            kotlinx.coroutines.delay(500)
                            
                            // Try enhanced username resolution
                            android.util.Log.d("RecordSync", "Attempting enhanced username resolution after login")
                            authRepository.tryToFetchUsernameFromAllSources()
                            
                            // Wait for it to complete
                            kotlinx.coroutines.delay(500)
                            android.util.Log.d("RecordSync", "Username after enhanced resolution: ${authRepository.username.value}")
                            
                            // If we found a username, show a more detailed message
                            if (authRepository.username.value != null) {
                                showMessage("Login successful as ${authRepository.username.value}")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("RecordSync", "Error in post-login username resolution", e)
                        }
                    }
                    
                    showMessage("Login successful")
                }.onFailure { error ->
                    android.util.Log.e("RecordSync", "Login failed", error)
                    android.util.Log.e("RecordSync", "Error message: ${error.message}")
                    
                    // Try as email if it failed as username and doesn't contain @ (might be an email without @ in the input)
                    if (!usernameOrEmail.contains('@')) {
                        android.util.Log.d("RecordSync", "Attempting fallback direct login with: $usernameOrEmail")
                        try {
                            authRepository.login(usernameOrEmail, password)
                                .onSuccess { user ->
                                    android.util.Log.d("RecordSync", "Fallback login successful")
                                    android.util.Log.d("RecordSync", "User email: ${user.email}")
                                    android.util.Log.d("RecordSync", "Current username after fallback: ${authRepository.username.value}")
                                    showMessage("Login successful")
                                }
                                .onFailure { fallbackError ->
                                    android.util.Log.e("RecordSync", "Fallback login failed", fallbackError)
                                    android.util.Log.e("RecordSync", "Fallback error message: ${fallbackError.message}")
                                    showMessage("Login failed: No account found. Try your email address instead of username.")
                                }
                        } catch (e: Exception) {
                            android.util.Log.e("RecordSync", "Exception during fallback login", e)
                            android.util.Log.e("RecordSync", "Exception message: ${e.message}")
                            showMessage("Login failed: No account found. Try your email address instead of username.")
                        }
                    } else {
                        showMessage("Login failed: ${error.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RecordSync", "Login error", e)
                android.util.Log.e("RecordSync", "Exception message: ${e.message}")
                android.util.Log.e("RecordSync", "Stack trace: ${e.stackTraceToString()}")
                showMessage("Login failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoggingIn = false) }
                
                // Final state check
                val currentUser = authRepository.currentUser.value
                val currentUsername = authRepository.username.value
                android.util.Log.d("RecordSync", "After login attempt - User logged in: ${currentUser != null}")
                android.util.Log.d("RecordSync", "After login attempt - Current email: ${currentUser?.email}")
                android.util.Log.d("RecordSync", "After login attempt - Current username: $currentUsername")
            }
        }
    }
    
    /**
     * Logout from Skilldex
     */
    fun logout() {
        viewModelScope.launch {
            try {
                android.util.Log.d("RecordSync", "Logging out user - current username: ${authRepository.username.value}")
                android.util.Log.d("RecordSync", "Current auth state before logout: ${authRepository.currentUser.value != null}")
                authRepository.signOut()
                android.util.Log.d("RecordSync", "Logout completed - current auth state: ${authRepository.currentUser.value != null}")
                showMessage("Logged out successfully")
            } catch (e: Exception) {
                android.util.Log.e("RecordSync", "Logout error", e)
                android.util.Log.e("RecordSync", "Error message: ${e.message}")
                android.util.Log.e("RecordSync", "Stack trace: ${e.stackTraceToString()}")
                showMessage("Logout failed: ${e.message}")
            }
        }
    }
    
    /**
     * Test database connection to help diagnose issues
     */
    fun testDatabaseConnection() {
        viewModelScope.launch {
            try {
                android.util.Log.d("RecordSync", "Starting database connection test")
                android.util.Log.d("RecordSync", "Current auth state: ${authRepository.currentUser.value != null}")
                android.util.Log.d("RecordSync", "Current username: ${authRepository.username.value}")
                
                // Try to force a username refresh
                if (authRepository.currentUser.value != null) {
                    android.util.Log.d("RecordSync", "Forcing username refresh...")
                    authRepository.tryToFetchUsernameFromAllSources()
                    
                    // Wait a moment for it to update
                    kotlinx.coroutines.delay(500)
                    android.util.Log.d("RecordSync", "Username after refresh: ${authRepository.username.value}")
                }
                
                // Directly test Firebase database access
                val firebaseManager = (authRepository as? AuthRepository)?.firebaseManager
                if (firebaseManager != null) {
                    firebaseManager.testDatabaseAccessAndStructure()
                } else {
                    android.util.Log.e("RecordSync", "Could not access FirebaseManager")
                }
                
                // Try to get user record ID if logged in
                if (authRepository.currentUser.value != null) {
                    android.util.Log.d("RecordSync", "Testing getUserRecordId...")
                    val recordId = authRepository.getUserRecordId()
                    android.util.Log.d("RecordSync", "User record ID result: $recordId")
                }
                
                val resultMessage = if (authRepository.username.value != null) {
                    "Connection test complete. Username found: ${authRepository.username.value}"
                } else {
                    "Connection test complete, but username is still null. Check logs for details."
                }
                
                showMessage(resultMessage)
            } catch (e: Exception) {
                android.util.Log.e("RecordSync", "Error testing connection", e)
                android.util.Log.e("RecordSync", "Error message: ${e.message}")
                showMessage("Connection test failed: ${e.message}")
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
