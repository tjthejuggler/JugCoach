package com.example.jugcoach.data.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AnthropicService {
    @POST("v1/messages")
    suspend fun sendMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2024-01-01",
        @Body request: AnthropicRequest
    ): AnthropicResponse
}
