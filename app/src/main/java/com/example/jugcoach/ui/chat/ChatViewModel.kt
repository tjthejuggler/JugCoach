package com.example.jugcoach.ui.chat

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.api.AnthropicRequest
import com.example.jugcoach.data.api.AnthropicResponse
import com.example.jugcoach.data.api.AnthropicService
import com.example.jugcoach.data.dao.CoachDao
import com.example.jugcoach.data.dao.NoteDao
import com.example.jugcoach.data.dao.SettingsDao
import com.example.jugcoach.data.dao.ConversationDao
import com.example.jugcoach.data.dao.ChatMessageDao
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.entity.*
import com.google.gson.JsonParser
import com.example.jugcoach.util.PromptLogger
import com.example.jugcoach.util.SystemPromptLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentCoach: Coach? = null,
    val availableCoaches: List<Coach> = emptyList(),
    val currentConversation: Conversation? = null,
    val availableConversations: List<Conversation> = emptyList()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    private val noteDao: NoteDao,
    private val coachDao: CoachDao,
    private val conversationDao: ConversationDao,
    private val chatMessageDao: ChatMessageDao,
    private val patternDao: PatternDao,
    private val anthropicService: AnthropicService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val gson = Gson()
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _availableApiKeys = MutableStateFlow<List<String>>(emptyList())
    val availableApiKeys: StateFlow<List<String>> = _availableApiKeys.asStateFlow()

    init {
        loadData()
        ensureHeadCoach()
        loadApiKeys()
    }

    private fun loadApiKeys() {
        viewModelScope.launch {
            settingsDao.getSettingsByCategory(SettingCategory.API_KEY)
                .collect { settings: List<Settings> ->
                    val validKeys = settings
                        .filter { it.value.isNotBlank() }
                        .map { it.key }
                    _availableApiKeys.value = validKeys
                }
        }
    }

    fun updateCoachApiKey(apiKeyName: String) {
        viewModelScope.launch {
            val currentCoach = _uiState.value.currentCoach ?: return@launch
            val updatedCoach = currentCoach.copy(apiKeyName = apiKeyName)
            coachDao.updateCoach(updatedCoach)
            _uiState.update { it.copy(currentCoach = updatedCoach) }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            // Load coaches first
            coachDao.getAllCoaches()
                .distinctUntilChanged()
                .collect { coaches ->
                    val currentCoach = coaches.find { it.isHeadCoach } ?: coaches.firstOrNull()
                    _uiState.update { state ->
                        state.copy(
                            availableCoaches = coaches,
                            currentCoach = currentCoach
                        )
                    }

                    // Only load conversations if we have a current coach
                    currentCoach?.let { coach ->
                        conversationDao.getConversationsForCoach(coach.id)
                            .distinctUntilChanged()
                            .collect { conversations ->
                                val currentConversation = conversations.firstOrNull()
                                _uiState.update { state ->
                                    state.copy(
                                        availableConversations = conversations,
                                        currentConversation = currentConversation
                                    )
                                }

                                // Load messages for current conversation
                                currentConversation?.let { conversation ->
                                    loadMessagesForConversation(conversation)
                                }
                            }
                    }
                }
        }
    }

    fun createNewConversation() {
        viewModelScope.launch {
            val currentCoach = _uiState.value.currentCoach ?: return@launch
            
            // Delete any blank conversations for this coach
            val conversations = conversationDao.getConversationsForCoach(currentCoach.id).first()
            conversations.forEach { conversation ->
                conversationDao.deleteIfEmpty(conversation.id)
            }
            
            val timestamp = System.currentTimeMillis()
            val conversation = Conversation(
                coachId = currentCoach.id,
                title = "Chat ${java.time.format.DateTimeFormatter.ISO_INSTANT.format(Instant.now())}",
                createdAt = timestamp,
                lastMessageAt = timestamp
            )
            val id = conversationDao.insert(conversation)
            val newConversation = conversation.copy(id = id)
            
            // Clear messages and select the new conversation
            _uiState.update { it.copy(
                messages = emptyList(),
                currentConversation = newConversation
            )}
        }
    }

    private fun loadMessagesForConversation(conversation: Conversation) {
        viewModelScope.launch {
            chatMessageDao.getMessagesForConversation(conversation.id)
                .distinctUntilChanged()
                .collect { messages ->
                    val uiMessages = messages.map { msg ->
                        ChatMessage(
                            id = msg.id.toString(),
                            text = msg.text,
                            sender = if (msg.isFromUser) ChatMessage.Sender.USER else ChatMessage.Sender.COACH,
                            timestamp = Instant.ofEpochMilli(msg.timestamp),
                            isError = msg.isError,
                            messageType = when {
                                msg.text.startsWith("Tool Output:") -> ChatMessage.MessageType.ACTION
                                msg.text.contains("analyzing tool output") -> ChatMessage.MessageType.THINKING
                                else -> ChatMessage.MessageType.TALKING
                            },
                            isInternal = msg.text.startsWith("Tool Output:") || extractJsonFromText(msg.text) != null
                        )
                    }
                    _uiState.update { it.copy(messages = uiMessages) }
                }
        }
    }

    fun selectConversation(conversation: Conversation) {
        if (_uiState.value.currentConversation?.id == conversation.id) return
        _uiState.update { it.copy(
            currentConversation = conversation,
            messages = emptyList() // Clear messages first
        )}
        
        // Load messages for the selected conversation
        loadMessagesForConversation(conversation)
    }

    fun updateConversationTitle(title: String) {
        viewModelScope.launch {
            val conversation = _uiState.value.currentConversation ?: return@launch
            conversationDao.updateTitle(conversation.id, title)
        }
    }

    fun toggleConversationFavorite() {
        viewModelScope.launch {
            val conversation = _uiState.value.currentConversation ?: return@launch
            conversationDao.updateFavorite(conversation.id, !conversation.isFavorite)
        }
    }

    private fun ensureHeadCoach() {
        viewModelScope.launch {
            coachDao.createHeadCoach()
        }
    }

    fun selectCoach(coach: Coach) {
        if (_uiState.value.currentCoach?.id == coach.id) return
        _uiState.update { it.copy(currentCoach = coach) }
        
        viewModelScope.launch {
            // Check if coach has any conversations
            val conversations = conversationDao.getConversationsForCoach(coach.id).first()
            if (conversations.isEmpty()) {
                // Create a default conversation for new coach
                val timestamp = System.currentTimeMillis()
                val conversation = Conversation(
                    coachId = coach.id,
                    title = "First Chat with ${coach.name}",
                    createdAt = timestamp,
                    lastMessageAt = timestamp
                )
                val id = conversationDao.insert(conversation)
                val newConversation = conversation.copy(id = id)
                
                // Select the new conversation
                selectConversation(newConversation)
            }
        }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            val currentCoach = _uiState.value.currentCoach ?: return@launch

            // Get or create conversation in a transaction to ensure consistency
            val conversation = try {
                val conv = conversationDao.getOrCreateConversation(
                    existingConversation = _uiState.value.currentConversation,
                    coachId = currentCoach.id,
                    title = "Chat ${java.time.format.DateTimeFormatter.ISO_INSTANT.format(Instant.now())}"
                )

                // Verify conversation exists before proceeding
                conversationDao.getConversationById(conv.id)
                    ?: throw IllegalStateException("Failed to verify conversation existence")
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = "Failed to create conversation: ${e.message}"
                )}
                return@launch
            }

            // Update UI with the verified conversation
            _uiState.update { it.copy(
                currentConversation = conversation,
                messages = emptyList()
            )}

            // Add user message
            try {
                val timestamp = System.currentTimeMillis()
                val userMessage = com.example.jugcoach.data.entity.ChatMessage(
                    conversationId = conversation.id,
                    text = text,
                    isFromUser = true,
                    timestamp = timestamp
                )
                chatMessageDao.insertAndUpdateConversation(userMessage, conversation.id, timestamp)
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = "Failed to save message: ${e.message}"
                )}
                return@launch
            }

            // Get API key for current coach
            val apiKeyName = currentCoach.apiKeyName
            val apiKey = settingsDao.getSettingValue(apiKeyName)
            if (apiKey.isNullOrEmpty()) {
                addMessage(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = "Please set up the API key '$apiKeyName' in Settings to chat with ${currentCoach.name}.",
                        sender = ChatMessage.Sender.COACH,
                        timestamp = Instant.now(),
                        isError = true
                    )
                )
                return@launch
            }

            // Show loading state
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Get last few messages for context (reversed to get correct chronological order)
                val recentMessages = _uiState.value.messages.takeLast(10)

                val messageHistory = recentMessages.map { msg ->
                    AnthropicRequest.Message(
                        role = if (msg.sender == ChatMessage.Sender.USER) "user" else "assistant",
                        content = listOf(AnthropicRequest.Content(text = msg.text))
                    )
                }

                val systemPrompt = SystemPromptLoader.loadSystemPrompt(context, currentCoach.systemPrompt)
                val request = AnthropicRequest(
                    system = systemPrompt,
                    messages = messageHistory + AnthropicRequest.Message(
                        role = "user",
                        content = listOf(AnthropicRequest.Content(text = text))
                    ),
                    tools = listOf(
                        AnthropicRequest.Tool(
                            name = "lookupPattern",
                            description = "Get full details of a specific pattern given its pattern_id.",
                            inputSchema = AnthropicRequest.InputSchema(
                                properties = mapOf(
                                    "pattern_id" to AnthropicRequest.Property(
                                        type = "string",
                                        description = "The unique identifier for the pattern."
                                    )
                                ),
                                required = listOf("pattern_id")
                            )
                        ),
                        // Add other tools here following the same pattern
                    )
                )

                // Log the complete request for debugging
                PromptLogger.logInteraction(
                    context = context,
                    systemPrompt = """
                        === Complete Request ===
                        System Prompt: $systemPrompt
                        
                        === Message History ===
                        ${recentMessages.joinToString("\n\n") { msg ->
                            """
                            Role: ${if (msg.sender == ChatMessage.Sender.USER) "user" else "assistant"}
                            Content: ${msg.text}
                            """.trimIndent()
                        }}
                        
                        Role: user
                        Content: $text
                    """.trimIndent(),
                    userMessage = text
                )

                // Log the initial request with full JSON
                Log.d("JugCoachDebug", "Initial Request JSON: ${gson.toJson(request)}")
                Log.d("JugCoachDebug", "Tools provided: ${gson.toJson(request.tools)}")

                val response = anthropicService.sendMessage(
                    apiKey = apiKey,
                    request = request
                )

                // Log the entire response with full JSON
                Log.d("JugCoachDebug", "Initial API Response JSON: ${gson.toJson(response)}")
                Log.d("JugCoachDebug", "Stop reason: ${response.stopReason}")

                // Get initial response text and try to extract tool calls
                var toolCalls = response.toolCalls
                var toolResults = mutableListOf<String>()
                var initialResponse = response.content.firstOrNull()?.text ?: "No response from the coach"

                Log.d("JugCoachDebug", "Initial response text: $initialResponse")
                Log.d("JugCoachDebug", "Tool calls from response: ${toolCalls?.size ?: 0}")

                // If no tool calls in response object, try to parse the text as JSON
                if (toolCalls.isNullOrEmpty()) {
                    Log.d("JugCoachDebug", "No tool calls in response. Attempting to parse text as JSON.")
                    val assistantText = initialResponse.trim()
                    
                    // Always try to extract a JSON substring
                    val jsonSubstring = extractJsonFromText(assistantText)
                    if (jsonSubstring != null) {
                        Log.d("JugCoachDebug", "Extracted JSON substring: $jsonSubstring")
                        try {
                            val jsonObject = JsonParser.parseString(jsonSubstring).asJsonObject
                            if (jsonObject.has("tool") && jsonObject.has("arguments")) {
                                val toolName = jsonObject.get("tool").asString
                                val arguments = jsonObject.get("arguments").asJsonObject
                                Log.d("JugCoachDebug", "Successfully parsed JSON - tool: $toolName, arguments: $arguments")
                                
                                toolCalls = listOf(AnthropicResponse.ToolCall(
                                    id = UUID.randomUUID().toString(),
                                    type = "function",
                                    name = toolName,
                                    arguments = arguments.toString()
                                ))
                                Log.d("JugCoachDebug", "Created tool call object: ${gson.toJson(toolCalls)}")
                                
                                // Clear the initial response since it was just a tool call
                                initialResponse = ""
                            } else {
                                Log.d("JugCoachDebug", "JSON missing required fields - tool: ${jsonObject.has("tool")}, arguments: ${jsonObject.has("arguments")}")
                            }
                        } catch (e: Exception) {
                            Log.d("JugCoachDebug", "Error parsing extracted JSON: ${e.message}")
                        }
                    } else {
                        Log.d("JugCoachDebug", "Failed to extract JSON from assistant text")
                    }
                }

                if (!toolCalls.isNullOrEmpty()) {
                    // Save initial assistant response (if not empty) as internal message
                    if (initialResponse.isNotEmpty()) {
                        val initialTimestamp = System.currentTimeMillis()
                        val initialMessage = com.example.jugcoach.data.entity.ChatMessage(
                            conversationId = conversation.id,
                            text = initialResponse,
                            isFromUser = false,
                            timestamp = initialTimestamp,
                            isInternal = true
                        )
                        chatMessageDao.insertAndUpdateConversation(initialMessage, conversation.id, initialTimestamp)
                    }

                    for (toolCall in toolCalls) {
                        Log.d("JugCoachDebug", "Processing tool call: ${toolCall.name} with arguments: ${toolCall.arguments}")
                        
                        when (toolCall.name) {
                            "lookupPattern" -> {
                                val jsonObject = JsonParser.parseString(toolCall.arguments).asJsonObject
                                val patternId = jsonObject.get("pattern_id")?.asString
                                Log.d("JugCoachDebug", "Extracted pattern_id: $patternId")
                                
                                if (patternId != null) {
                                    val pattern = patternDao.getPatternById(patternId, currentCoach.id)
                                    if (pattern != null) {
                                        val patternInfo = """
                                            Pattern Details:
                                            Name: ${pattern.name}
                                            Difficulty: ${pattern.difficulty ?: "Not specified"}
                                            Siteswap: ${pattern.siteswap ?: "Not specified"}
                                            Number of Balls: ${pattern.num ?: "Not specified"}
                                            Explanation: ${pattern.explanation ?: "No explanation available"}
                                            Tags: ${pattern.tags.joinToString(", ")}
                                            Prerequisites: ${pattern.prerequisites.joinToString(", ")}
                                            Related Patterns: ${pattern.related.joinToString(", ")}
                                            ${if (pattern.gifUrl != null) "Animation: ${pattern.gifUrl}" else ""}
                                            ${if (pattern.video != null) "Video Tutorial: ${pattern.video}" else ""}
                                            ${if (pattern.url != null) "Additional Resources: ${pattern.url}" else ""}
                                        """.trimIndent()
                                        toolResults.add(patternInfo)
                                    } else {
                                        toolResults.add("Pattern not found: $patternId")
                                    }
                                }
                            }
                            // Add other tool handlers here as needed
                        }
                    }

                    // Insert tool output as a dedicated internal message
                    if (toolResults.isNotEmpty()) {
                        Log.d("JugCoachDebug", "Tool results to process: ${toolResults.size}")
                        val toolOutputTimestamp = System.currentTimeMillis()
                        val toolOutputMessage = com.example.jugcoach.data.entity.ChatMessage(
                            conversationId = conversation.id,
                            text = "Tool Output:\n\n${toolResults.joinToString("\n\n")}",
                            isFromUser = false,
                            timestamp = toolOutputTimestamp,
                            isInternal = true
                        )
                        chatMessageDao.insertAndUpdateConversation(toolOutputMessage, conversation.id, toolOutputTimestamp)

                        // Get updated message history including tool output
                        Log.d("JugCoachDebug", "Building updated message history")
                        val updatedMessages = _uiState.value.messages + listOf(
                            ChatMessage(
                                id = UUID.randomUUID().toString(),
                                text = "Tool Output:\n\n${toolResults.joinToString("\n\n")}",
                                sender = ChatMessage.Sender.COACH,
                                timestamp = Instant.ofEpochMilli(toolOutputTimestamp),
                                isError = false,
                                isInternal = true
                            )
                        )

                        // Make follow-up API call with explicit instruction to analyze tool output
                        Log.d("JugCoachDebug", "Building follow-up request")
                        val followUpRequest = AnthropicRequest(
                            system = systemPrompt,
                            messages = updatedMessages.map { msg ->
                                AnthropicRequest.Message(
                                    role = if (msg.sender == ChatMessage.Sender.USER) "user" else "assistant",
                                    content = listOf(AnthropicRequest.Content(text = msg.text))
                                )
                            } + AnthropicRequest.Message(
                                role = "user",
                                content = listOf(AnthropicRequest.Content(text = "Please analyze the above tool output and explain its implications for the juggling pattern."))
                            ),
                            tools = request.tools // Keep the same tools available
                        )

                        Log.d("JugCoachDebug", "Follow-up request payload: $followUpRequest")
                        
                        val followUpResponse = anthropicService.sendMessage(
                            apiKey = apiKey,
                            request = followUpRequest
                        )
                        
                        Log.d("JugCoachDebug", "Follow-up response received: $followUpResponse")

                        // Save the analysis response as a visible message
                        val analysisTimestamp = System.currentTimeMillis()
                        Log.d("JugCoachDebug", "Saving analysis response")
                        val analysisMessage = com.example.jugcoach.data.entity.ChatMessage(
                            conversationId = conversation.id,
                            text = followUpResponse.content.firstOrNull()?.text ?: "No analysis provided",
                            isFromUser = false,
                            timestamp = analysisTimestamp,
                            isInternal = false
                        )
                        chatMessageDao.insertAndUpdateConversation(analysisMessage, conversation.id, analysisTimestamp)
                    }
                } else {
                    // If no tool calls, just save the initial response as a visible message
                    val timestamp = System.currentTimeMillis()
                    val coachMessage = com.example.jugcoach.data.entity.ChatMessage(
                        conversationId = conversation.id,
                        text = initialResponse,
                        isFromUser = false,
                        timestamp = timestamp,
                        isInternal = false
                    )
                    chatMessageDao.insertAndUpdateConversation(coachMessage, conversation.id, timestamp)
                }

            } catch (e: retrofit2.HttpException) {
                val errorMessage = when (e.code()) {
                    401 -> "Invalid API key. Please check your settings."
                    429 -> "Too many requests. Please try again later."
                    else -> "API error: ${e.message()}"
                }

                addMessage(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = errorMessage,
                        sender = ChatMessage.Sender.COACH,
                        timestamp = Instant.now(),
                        isError = true
                    )
                )
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("401") == true -> "Invalid API key. Please check your settings."
                    e.message?.contains("timeout") == true -> "Request timed out. Please try again."
                    else -> "Failed to get response: ${e.message}"
                }

                addMessage(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = errorMessage,
                        sender = ChatMessage.Sender.COACH,
                        timestamp = Instant.now(),
                        isError = true
                    )
                )
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    private fun extractJsonFromText(text: String): String? {
        // This regex will match a JSON object (assuming it starts with { and ends with })
        val jsonRegex = "\\{.*\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
        val matchResult = jsonRegex.find(text)
        return matchResult?.value
    }

    private fun extractToolCallsFromText(text: String): List<AnthropicResponse.ToolCall>? {
        try {
            Log.d("JugCoachDebug", "Starting tool call extraction from text")
            Log.d("JugCoachDebug", "Input text: $text")

            // First try to extract JSON substring if the text contains non-JSON content
            val jsonText = if (text.trim().startsWith("{") && text.trim().endsWith("}")) {
                text
            } else {
                extractJsonFromText(text) ?: return null
            }

            // Try parsing as a single tool call first
            try {
                val jsonObject = JsonParser.parseString(jsonText).asJsonObject
                if (jsonObject.has("tool") && jsonObject.has("arguments")) {
                    return listOf(
                        AnthropicResponse.ToolCall(
                            id = UUID.randomUUID().toString(),
                            type = "function",
                            name = jsonObject.get("tool").asString,
                            arguments = jsonObject.get("arguments").toString()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.d("JugCoachDebug", "Not a single tool call JSON: ${e.message}")
            }

            // If not a single tool call, look for multiple tool calls
            val pattern = """\{(?:[^{}]|"[^"]*")*"tool"\s*:\s*"[^"]*"(?:[^{}]|"[^"]*")*"arguments"\s*:\s*\{[^{}]*\}(?:[^{}]|"[^"]*")*\}""".toRegex()
            val matches = pattern.findAll(jsonText)
            
            Log.d("JugCoachDebug", "Searching for tool call JSON objects")
            
            val toolCalls = matches.mapNotNull { match ->
                try {
                    val json = match.value
                    Log.d("JugCoachDebug", "Found potential tool call JSON: $json")
                    
                    val jsonObject = gson.fromJson(json, Map::class.java)
                    val tool = jsonObject["tool"]
                    val arguments = jsonObject["arguments"]
                    
                    Log.d("JugCoachDebug", "Parsed JSON - tool: $tool, arguments: $arguments")
                    
                    if (tool != null && arguments != null) {
                        val toolCall = AnthropicResponse.ToolCall(
                            id = UUID.randomUUID().toString(),
                            type = "function",
                            name = tool as String,
                            arguments = gson.toJson(arguments)
                        )
                        Log.d("JugCoachDebug", "Successfully created tool call: ${gson.toJson(toolCall)}")
                        toolCall
                    } else {
                        Log.d("JugCoachDebug", "Missing required fields in JSON - tool: $tool, arguments: $arguments")
                        null
                    }
                } catch (e: Exception) {
                    Log.d("JugCoachDebug", "Failed to parse tool call JSON: ${e.message}")
                    Log.d("JugCoachDebug", "Exception stack trace: ${e.stackTrace.joinToString("\n")}")
                    null
                }
            }.toList()

            Log.d("JugCoachDebug", "Extracted ${toolCalls.size} valid tool calls")
            return toolCalls.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.d("JugCoachDebug", "Failed to extract tool calls from text: ${e.message}")
            Log.d("JugCoachDebug", "Exception stack trace: ${e.stackTrace.joinToString("\n")}")
            return null
        }
    }
}
