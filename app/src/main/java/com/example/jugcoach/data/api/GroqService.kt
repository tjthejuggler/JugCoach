package com.example.jugcoach.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GroqService {
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: GroqChatRequest
    ): Response<GroqChatResponse>
}

data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val tools: List<GroqTool>? = null,
    val toolChoice: String? = null,
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val stop: List<String>? = null
)

data class GroqMessage(
    val role: String,
    val content: String,
    val name: String? = null
)

data class GroqTool(
    val type: String,
    val function: GroqFunction
)

data class GroqFunction(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
)

data class GroqChatResponse(
    val id: String,
    val model: String,
    val choices: List<GroqChoice>,
    val usage: GroqUsage
)

data class GroqChoice(
    val index: Int,
    val message: GroqMessage,
    val finishReason: String
)

data class GroqUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
