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
        val binding = ItemPatternRunBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        binding.root.visibility = View.VISIBLE
        return PatternRunViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PatternRunViewHolder, position: Int) {
        if (currentState == null) {
            return
        }
        
        currentState?.let { state ->
            
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

                // Start timer button
                startTimerButton.apply {
                    setOnClickListener { onStartTimer() }
                    isEnabled = !state.isTimerRunning
                    visibility = if (state.isTimerRunning) View.GONE else View.VISIBLE
                }

                // End run buttons
                endRunButtons.visibility = if (state.showEndButtons) View.VISIBLE else View.GONE
                endCatchButton.setOnClickListener { onEndRun(true) }
                endDropButton.setOnClickListener { onEndRun(false) }

                // Close button
                closeButton.setOnClickListener { onClose() }
            }
        }
    }

    override fun getItemCount(): Int {
        return if (currentState != null) 1 else 0
    }

    fun bind(state: PatternRunState?) {
        currentState = state
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