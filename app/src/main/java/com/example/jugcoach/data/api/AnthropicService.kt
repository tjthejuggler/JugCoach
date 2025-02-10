package com.example.jugcoach.data.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AnthropicService {
    @POST("v1/messages")
    suspend fun sendMessage(
        @Header("x-api-key") apiKey: String, // Raw API key without any prefix
        @Header("anthropic-version") version: String = "2023-06-01",
        @Header("content-type") contentType: String = "application/json",
        @Header("accept") accept: String = "application/json",
        @Body request: AnthropicRequest
    ): AnthropicResponse
}
