package com.example.jugcoach.util

import android.content.Context

/**
 * Utility class for loading and combining system prompts
 */
object SystemPromptLoader {
    private const val SYSTEM_PROMPT_PATH = "LLM_coach/system_prompt.txt"

    /**
     * Load the system prompt
     * @param context Android context
     * @param coachPrompt Optional coach-specific prompt to prepend
     * @return Complete system prompt
     */
    fun loadSystemPrompt(context: Context, coachPrompt: String? = null): String {
        val systemPrompt = try {
            context.assets.open(SYSTEM_PROMPT_PATH).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load system prompt", e)
        }

        return buildString {
            // Add coach-specific prompt if provided
            coachPrompt?.let {
                appendLine(it)
                appendLine()
            }

            // Add system prompt
            append(systemPrompt)
        }
    }
}
