package com.example.jugcoach.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for logging LLM prompts and interactions for debugging purposes.
 */
object PromptLogger {
    private const val TAG = "LLM_PROMPT"
    private const val LOG_FOLDER = "prompt_logs"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    /**
     * Log a complete interaction with the LLM, including system prompt and user message
     */
    fun logInteraction(context: Context, systemPrompt: String, userMessage: String) {
        try {
            val logDir = File(context.getExternalFilesDir(null), LOG_FOLDER)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val timestamp = dateFormat.format(Date())
            val logFile = File(logDir, "prompt_log_$timestamp.txt")

            val logContent = buildString {
                appendLine("=== LLM Interaction Log ===")
                appendLine("Timestamp: $timestamp")
                appendLine()
                systemPrompt.lines().forEach { line ->
                    appendLine(line)
                }
                appendLine()
                appendLine("=== Current Message ===")
                appendLine("User: $userMessage")
                appendLine()
                appendLine("=== End of Log ===")
            }

            // Write to file
            logFile.writeText(logContent)

            // Log to Android Studio's logcat with proper line breaks
            logContent.lines().forEach { line ->
                Log.i(TAG, line)
            }

            // Log file path for reference
            Log.d(TAG, "Logged interaction to ${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging prompt", e)
        }
    }

    /**
     * Get the path to the logs directory
     */
    fun getLogsDirectory(context: Context): File {
        return File(context.getExternalFilesDir(null), LOG_FOLDER)
    }

    /**
     * List all log files
     */
    fun listLogs(context: Context): List<File> {
        val logDir = getLogsDirectory(context)
        return if (logDir.exists()) {
            logDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * Delete old logs (keeping only the last N logs)
     */
    fun cleanupOldLogs(context: Context, keepCount: Int = 100) {
        val logs = listLogs(context)
        if (logs.size > keepCount) {
            logs.drop(keepCount).forEach { it.delete() }
        }
    }
}
