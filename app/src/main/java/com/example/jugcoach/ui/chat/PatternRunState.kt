package com.example.jugcoach.ui.chat

import com.example.jugcoach.data.entity.Pattern

data class PatternRunState(
    val pattern: Pattern,
    val isTimerRunning: Boolean = false,
    val elapsedTime: Long = 0L,
    val showEndButtons: Boolean = false,
    val isCountingDown: Boolean = false,
    val countdownTime: Long = -5000L // Negative for countdown, positive for elapsed time
)

sealed class PatternRunEvent {
    data class StartRun(val pattern: Pattern) : PatternRunEvent()
    object StartTimer : PatternRunEvent()
    object TimerTick : PatternRunEvent()
    data class EndRun(val wasCatch: Boolean, val catches: Int? = null) : PatternRunEvent()
    object CancelRun : PatternRunEvent()
}