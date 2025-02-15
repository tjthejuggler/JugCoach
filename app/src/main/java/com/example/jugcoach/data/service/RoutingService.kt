package com.example.jugcoach.data.service

import android.util.Log
import com.example.jugcoach.data.dao.SettingsDao
import com.example.jugcoach.data.api.GroqService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutingService @Inject constructor(
    private val groqService: GroqService,
    private val settingsDao: SettingsDao
) {
    suspend fun routeQuery(query: String): String {
        val modelName = settingsDao.getSettingValue("routing_model_name")
        val apiKey = settingsDao.getSettingValue("routing_model_key")

        if (modelName.isNullOrEmpty() || apiKey.isNullOrEmpty()) {
            Log.w("RoutingService", "Model name or API key not set, falling back to no tool")
            return "no_tool"
        }

        try {
            val response = groqService.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = com.example.jugcoach.data.api.GroqChatRequest(
                    model = modelName,
                    messages = listOf(
                        com.example.jugcoach.data.api.GroqMessage(
                            role = "system",
                            content = """
                                You are a routing assistant. Determine if tools are needed based on the user query.
                                If the query is about looking up a specific pattern, respond with 'lookupPattern'.
                                If the query is about searching for patterns, respond with 'searchPatterns'.
                                If no tools are needed, respond with 'no_tool'.
                            """.trimIndent()
                        ),
                        com.example.jugcoach.data.api.GroqMessage(
                            role = "user",
                            content = query
                        )
                    ),
                    maxTokens = 20
                )
            )

            if (!response.isSuccessful) {
                Log.e("RoutingService", "API error: ${response.code()} - ${response.message()}")
                return "no_tool"
            }

            val routingDecision = response.body()?.choices?.firstOrNull()?.message?.content?.trim() ?: "no_tool"
            Log.d("RoutingService", "Routing decision: $routingDecision")
            
            return when (routingDecision) {
                "lookupPattern", "searchPatterns" -> routingDecision
                else -> "no_tool"
            }
        } catch (e: Exception) {
            Log.e("RoutingService", "Failed to route query", e)
            return "no_tool"
        }
    }
}
