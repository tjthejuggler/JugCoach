    package com.example.jugcoach.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.databinding.ItemPatternBinding
import com.google.android.material.chip.Chip

class PatternAdapter(
    private val onPatternClick: (Pattern) -> Unit
) : ListAdapter<Pattern, PatternAdapter.PatternViewHolder>(PatternDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatternViewHolder {
        val binding = ItemPatternBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PatternViewHolder(binding, onPatternClick)
    }

    override fun onBindViewHolder(holder: PatternViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PatternViewHolder(
        private val binding: ItemPatternBinding,
        private val onPatternClick: (Pattern) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(pattern: Pattern) {
            binding.apply {
                // If the name is a number (like "55500"), show it with a more descriptive format
                patternName.text = if (pattern.name.matches(Regex("\\d+"))) {
                    "Pattern ${pattern.name}"
                } else {
                    pattern.name
                }
                patternDescription.text = pattern.explanation
                
                // Show pattern info
                patternDifficulty.text = pattern.difficulty?.let { "Difficulty: $it" } ?: "Difficulty: -"
                patternBalls.text = pattern.num?.let { "Balls: $it" } ?: "Balls: -"
                patternRecord.text = pattern.record?.let { "Record: ${it.catches}c" } ?: "Record: -"

                root.setOnClickListener { onPatternClick(pattern) }
            }
        }
    }

    private class PatternDiffCallback : DiffUtil.ItemCallback<Pattern>() {
        override fun areItemsTheSame(oldItem: Pattern, newItem: Pattern): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Pattern, newItem: Pattern): Boolean {
            return oldItem == newItem
        }
    }
}
