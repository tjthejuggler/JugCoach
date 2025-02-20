package com.example.jugcoach.util

object RunUtils {
    /**
     * Calculates catches per minute from the number of catches and duration in seconds.
     * Returns null if either catches or duration is null.
     *
     * @param catches The number of catches in the run
     * @param durationSeconds The duration of the run in seconds
     * @return The calculated catches per minute, or null if either input is null
     */
    fun calculateCatchesPerMinute(catches: Int?, durationSeconds: Long?): Double? {
        if (catches == null || durationSeconds == null || durationSeconds <= 0) {
            return null
        }
        
        // Convert duration to minutes and calculate catches per minute
        val durationMinutes = durationSeconds.toDouble() / 60.0
        return catches.toDouble() / durationMinutes
    }
}