package com.example.jugcoach.ui.pattern

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jugcoach.R
import com.example.jugcoach.data.entity.Run
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RunHistoryAdapter : ListAdapter<Run, RunHistoryAdapter.RunViewHolder>(RunDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_run_history, parent, false)
        return RunViewHolder(view)
    }

    override fun onBindViewHolder(holder: RunViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RunViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
        private val runInfo: TextView = itemView.findViewById(R.id.run_info)
        private val runDate: TextView = itemView.findViewById(R.id.run_date)

        fun bind(run: Run) {
            val info = when {
                run.catches != null -> "${run.catches} catches"
                run.duration != null -> "${run.duration} seconds"
                else -> "Unknown"
            }
            val endStatus = if (run.isCleanEnd) "Clean end" else "Dropped"
            runInfo.text = "$info - $endStatus"
            runDate.text = dateFormat.format(Date(run.date))
        }
    }

    private class RunDiffCallback : DiffUtil.ItemCallback<Run>() {
        override fun areItemsTheSame(oldItem: Run, newItem: Run): Boolean {
            return oldItem.date == newItem.date
        }

        override fun areContentsTheSame(oldItem: Run, newItem: Run): Boolean {
            return oldItem == newItem
        }
    }
}