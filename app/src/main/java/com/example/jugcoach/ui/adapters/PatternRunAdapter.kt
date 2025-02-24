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

                // Show pattern stats
                patternStats.apply {
                    val runsWithCatchesAndTime = state.pattern.runHistory.runs
                        .filter { it.catches != null && it.duration != null }
                    
                    if (runsWithCatchesAndTime.isNotEmpty()) {
                        val totalCatches = runsWithCatchesAndTime.sumOf { it.catches!! }
                        val totalSeconds = runsWithCatchesAndTime.sumOf { it.duration!! }
                        val overallCpm = (totalCatches.toDouble() / totalSeconds.toDouble()) * 60
                        val timeInMinutes = totalSeconds / 60.0
                        
                        text = root.context.getString(
                            R.string.pattern_stats_format,
                            overallCpm,
                            timeInMinutes
                        )
                        visibility = View.VISIBLE
                    } else {
                        visibility = View.GONE
                    }
                }

                // Setup show records functionality
                showRecordsText.setOnClickListener {
                    recordsSection.visibility = if (recordsSection.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }

                // Find and display records
                val runs = state.pattern.runHistory.runs
                    .filter { it.catches != null && it.duration != null }

                // Clean end records
                val cleanEndRuns = runs.filter { it.isCleanEnd }
                cleanEndRecords.text = if (cleanEndRuns.isNotEmpty()) {
                    val bestCatches = cleanEndRuns.maxByOrNull { it.catches!! }
                    val bestDuration = cleanEndRuns.maxByOrNull { it.duration!! }
                    buildString {
                        bestCatches?.let { run ->
                            appendLine(root.context.getString(
                                R.string.record_format,
                                run.catches!!,
                                run.duration!! / 60,
                                run.duration!! % 60
                            ))
                        }
                        if (bestDuration != bestCatches) {
                            bestDuration?.let { run ->
                                append(root.context.getString(
                                    R.string.record_format,
                                    run.catches!!,
                                    run.duration!! / 60,
                                    run.duration!! % 60
                                ))
                            }
                        }
                    }
                } else {
                    root.context.getString(R.string.no_records)
                }

                // Drop records
                val dropRuns = runs.filter { !it.isCleanEnd }
                dropRecords.text = if (dropRuns.isNotEmpty()) {
                    val bestCatches = dropRuns.maxByOrNull { it.catches!! }
                    val bestDuration = dropRuns.maxByOrNull { it.duration!! }
                    buildString {
                        bestCatches?.let { run ->
                            appendLine(root.context.getString(
                                R.string.record_format,
                                run.catches!!,
                                run.duration!! / 60,
                                run.duration!! % 60
                            ))
                        }
                        if (bestDuration != bestCatches) {
                            bestDuration?.let { run ->
                                append(root.context.getString(
                                    R.string.record_format,
                                    run.catches!!,
                                    run.duration!! / 60,
                                    run.duration!! % 60
                                ))
                            }
                        }
                    }
                } else {
                    root.context.getString(R.string.no_records)
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