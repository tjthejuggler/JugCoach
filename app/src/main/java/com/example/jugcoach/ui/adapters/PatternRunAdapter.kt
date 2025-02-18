package com.example.jugcoach.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.example.jugcoach.R
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.databinding.ItemPatternRunBinding
import com.example.jugcoach.ui.chat.PatternRunState

class PatternRunAdapter(
    private val onStartTimer: () -> Unit,
    private val onEndRun: (Boolean) -> Unit,
    private val onPatternClick: (Pattern) -> Unit,
    private val onClose: () -> Unit
) : RecyclerView.Adapter<PatternRunAdapter.PatternRunViewHolder>() {

    private var currentState: PatternRunState? = null

    class PatternRunViewHolder(
        val binding: ItemPatternRunBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatternRunViewHolder {
        android.util.Log.d("TimerDebug", "onCreateViewHolder called")
        val binding = ItemPatternRunBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        binding.root.visibility = View.VISIBLE
        android.util.Log.d("TimerDebug", "Created view holder with root visibility: ${binding.root.visibility}")
        return PatternRunViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PatternRunViewHolder, position: Int) {
        android.util.Log.d("TimerDebug", "onBindViewHolder called for position: $position")
        if (currentState == null) {
            android.util.Log.d("TimerDebug", "currentState is null in onBindViewHolder")
            return
        }
        
        currentState?.let { state ->
            android.util.Log.d("TimerDebug", """
                PatternRunAdapter.onBindViewHolder:
                Pattern: ${state.pattern.name}
                isTimerRunning: ${state.isTimerRunning}
                isCountingDown: ${state.isCountingDown}
                countdownTime: ${state.countdownTime}
                showEndButtons: ${state.showEndButtons}
                holder.binding.root.visibility: ${holder.binding.root.visibility}
            """.trimIndent())
            
            with(holder.binding) {
                patternName.apply {
                    text = state.pattern.name
                    setOnClickListener { onPatternClick(state.pattern) }
                }
                
                // Timer display
                timerDisplay.text = if (state.isCountingDown) {
                    val seconds = (state.countdownTime / -1000).toInt()
                    if (seconds == 0) "GO!" else seconds.toString()
                } else {
                    val seconds = (state.elapsedTime / 1000).toInt()
                    String.format("%02d:%02d", seconds / 60, seconds % 60)
                }
                android.util.Log.d("TimerDebug", "Timer display set to: ${timerDisplay.text}")

                // Start timer button
                startTimerButton.setOnClickListener {
                    android.util.Log.d("TimerDebug", "Start timer button clicked")
                    onStartTimer()
                }
                startTimerButton.isEnabled = !state.isTimerRunning
                startTimerButton.visibility = if (state.isTimerRunning) View.GONE else View.VISIBLE
                android.util.Log.d("TimerDebug", "Start timer button - enabled: ${startTimerButton.isEnabled}, visible: ${startTimerButton.visibility == View.VISIBLE}")

                // End run buttons
                endRunButtons.visibility = if (state.showEndButtons) View.VISIBLE else View.GONE
                endCatchButton.setOnClickListener { onEndRun(true) }
                endDropButton.setOnClickListener { onEndRun(false) }
                android.util.Log.d("TimerDebug", "End run buttons visible: ${endRunButtons.visibility == View.VISIBLE}")

                // Close button
                closeButton.setOnClickListener { onClose() }
            }
        }
    }

    override fun getItemCount(): Int {
        val count = if (currentState != null) 1 else 0
        android.util.Log.d("TimerDebug", "getItemCount() returning: $count (currentState: ${currentState != null})")
        return count
    }

    fun bind(state: PatternRunState?) {
        android.util.Log.d("TimerDebug", "PatternRunAdapter.bind() called with state: $state")
        currentState = state
        android.util.Log.d("TimerDebug", "Calling notifyDataSetChanged()")
        notifyDataSetChanged()
    }

    companion object {
        fun create(
            onStartTimer: () -> Unit,
            onEndRun: (Boolean) -> Unit,
            onPatternClick: (Pattern) -> Unit,
            onClose: () -> Unit
        ): PatternRunAdapter {
            return PatternRunAdapter(onStartTimer, onEndRun, onPatternClick, onClose)
        }
    }
}