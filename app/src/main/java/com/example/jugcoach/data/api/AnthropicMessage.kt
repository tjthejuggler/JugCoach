package com.example.jugcoach.data.api

import com.google.gson.annotations.SerializedName

data class AnthropicRequest(
    val model: String = "claude-3-5-sonnet-20240620",
    val messages: List<Message>,
    val system: String? = null,
    @SerializedName("max_tokens")
    val maxTokens: Int = 1024,
    val temperature: Double = 0.7,
    val tools: List<Tool>? = null
) {
    data class Message(
        val role: String,
        val content: List<Content>
    ) {
        constructor(role: String, text: String) : this(
            role = role,
            content = listOf(Content(text = text))
        )
    }

    data class Content(
        val type: String = "text",
        val text: String
    )

    data class Tool(
        val name: String,
        val description: String,
        @SerializedName("input_schema")
        val inputSchema: InputSchema
    )

    data class InputSchema(
        val type: String = "object",
        val properties: Map<String, Property>,
        val required: List<String>? = null
    )

    data class Property(
        val type: String,
        val description: String? = null,
        val items: Property? = null,
        val properties: Map<String, Property>? = null
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
    val stopSequence: String?,
    @SerializedName("tool_calls")
    val toolCalls: List<ToolCall>? = null
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

    data class ToolCall(
        val id: String,
        val type: String,
        val name: String,
        val arguments: String
    )
}
