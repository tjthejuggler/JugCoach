package com.example.jugcoach.ui.chat

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.navigation.findNavController
import com.example.jugcoach.R
import com.example.jugcoach.databinding.ItemPatternRunBinding
import java.util.concurrent.TimeUnit

class PatternRunView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ItemPatternRunBinding.inflate(LayoutInflater.from(context), this, true)

    fun bind(
        state: PatternRunState,
        onStartTimer: () -> Unit,
        onEndRun: (Boolean) -> Unit,
        onPatternClick: () -> Unit,
        onClose: () -> Unit
    ) {
        // Set pattern name and click listener
        binding.patternName.text = state.pattern.name
        binding.patternName.setOnClickListener {
            findNavController().navigate(
                R.id.action_nav_chat_to_patternDetailsFragment,
                androidx.core.os.bundleOf("patternId" to state.pattern.id)
            )
        }

        // Format and display timer
        val timeToDisplay = if (state.isCountingDown) state.countdownTime else state.elapsedTime
        val isNegative = timeToDisplay < 0
        val absoluteTime = Math.abs(timeToDisplay)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(absoluteTime)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(absoluteTime) % 60
        binding.timerDisplay.text = String.format("%s%02d:%02d",
            if (isNegative) "-" else "",
            minutes,
            seconds
        )

        // Show/hide appropriate buttons based on state
        binding.startTimerButton.visibility = if (state.isTimerRunning) GONE else VISIBLE
        binding.endRunButtons.visibility = if (state.showEndButtons) VISIBLE else GONE

        // Set up button click listeners
        binding.startTimerButton.setOnClickListener { onStartTimer() }
        binding.endCatchButton.setOnClickListener { onEndRun(true) }
        binding.endDropButton.setOnClickListener { onEndRun(false) }
        binding.closeButton.setOnClickListener { onClose() }
    }
}