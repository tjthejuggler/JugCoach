package com.example.jugcoach.util

import android.content.Context

/**
 * Utility class for loading and combining system prompts
 */
object SystemPromptLoader {
    private const val SYSTEM_PROMPT_PATH = "LLM_coach/system_prompt.txt"
    private const val UNIVERSAL_PROMPT_PATH = "LLM_coach/universal_system_prompt.txt"

    /**
     * Load and combine system prompts
     * @param context Android context
     * @param coachPrompt Optional coach-specific prompt
     * @return Combined system prompt
     */
    fun loadSystemPrompt(context: Context, coachPrompt: String? = null): String {
        val systemPrompt = try {
            context.assets.open(SYSTEM_PROMPT_PATH).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            // If system prompt file is missing, use empty string
            ""
        }

        val universalPrompt = try {
            context.assets.open(UNIVERSAL_PROMPT_PATH).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            // If universal prompt file is missing, use empty string
            ""
        }

        return buildString {
            // Add coach-specific prompt if provided
            coachPrompt?.let {
                appendLine(it)
                appendLine()
            }

            // Add base system prompt
            if (systemPrompt.isNotEmpty()) {
                appendLine(systemPrompt)
                appendLine()
            }

            // Add universal database access instructions
            if (universalPrompt.isNotEmpty()) {
                appendLine(universalPrompt)
            }
        }
    }
}
