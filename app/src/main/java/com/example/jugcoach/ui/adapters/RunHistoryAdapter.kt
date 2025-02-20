package com.example.jugcoach.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jugcoach.R
import com.example.jugcoach.data.entity.Run
import java.text.SimpleDateFormat
import java.util.*

class RunHistoryAdapter : RecyclerView.Adapter<RunHistoryAdapter.RunViewHolder>() {
    private var runs: List<Run> = emptyList()
    private val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

    class RunViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val runInfo: TextView = view.findViewById(R.id.run_info)
        val catchesPerMinute: TextView = view.findViewById(R.id.run_catches_per_minute)
        val runDate: TextView = view.findViewById(R.id.run_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_run_history, parent, false)
        return RunViewHolder(view)
    }

    override fun onBindViewHolder(holder: RunViewHolder, position: Int) {
        val run = runs[position]
        val context = holder.itemView.context

        // Format the run info text
        val runInfoText = buildString {
            if (run.catches != null) {
                append("${run.catches} catches")
                if (run.duration != null) {
                    append(" in ${run.duration} seconds")
                }
            }
            if (run.isCleanEnd) {
                if (run.catches != null) append(" - ")
                append("Clean end")
            }
        }
        holder.runInfo.text = runInfoText

        // Show catches per minute if available
        holder.catchesPerMinute.apply {
            if (run.catchesPerMinute != null) {
                visibility = View.VISIBLE
                text = context.getString(R.string.catches_per_minute_format, run.catchesPerMinute)
            } else {
                visibility = View.GONE
            }
        }

        // Format and set the date
        holder.runDate.text = dateFormat.format(Date(run.date))
    }

    override fun getItemCount() = runs.size

    fun submitList(newRuns: List<Run>) {
        runs = newRuns
        notifyDataSetChanged()
    }
}