package com.example.jugcoach.data.api

import com.google.gson.annotations.SerializedName

data class AnthropicRequest(
    val model: String = "claude-3-opus-20240229",
    val messages: List<Message>,
    @SerializedName("max_tokens")
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val system: String? = null
) {
    data class Message(
        val role: String,
        val content: List<Content>
    ) {
        constructor(role: String, text: String) : this(
            role = role,
            content = listOf(Content(text))
        )
    }

    data class Content(
        val type: String = "text",
        val text: String
    )
}

data class AnthropicResponse(
    val id: String,
    val type: String,
    val role: String,
    val model: String,
    val content: List<Content>,
    val usage: Usage,
    @SerializedName("stop_reason")
    val stopReason: String?,
    @SerializedName("stop_sequence")
    val stopSequence: String?
) {
    data class Content(
        val type: String,
        val text: String
    )

    data class Usage(
        @SerializedName("input_tokens")
        val inputTokens: Int,
        @SerializedName("output_tokens")
        val outputTokens: Int
    )
}
