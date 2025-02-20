package com.example.jugcoach.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jugcoach.R
import com.example.jugcoach.data.entity.Run
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RunAdapter : RecyclerView.Adapter<RunAdapter.ViewHolder>() {
    private var runs: List<Run> = emptyList()

    fun setRuns(newRuns: List<Run>) {
        runs = newRuns
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_run_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val run = runs[position]
        holder.bind(run)
    }

    override fun getItemCount() = runs.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val runInfo: TextView = view.findViewById(R.id.run_info)
        private val runDate: TextView = view.findViewById(R.id.run_date)
        private val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

        fun bind(run: Run) {
            val context = itemView.context
            val infoParts = mutableListOf<String>()

            // Add catches if present
            run.catches?.let {
                infoParts.add("$it catches")
            }

            // Add duration if present
            run.duration?.let {
                val minutes = it / 60
                val seconds = it % 60
                infoParts.add("%02d:%02d".format(minutes, seconds))
            }

            // Add shorter end status
            infoParts.add(if (run.isCleanEnd) "Clean" else "Drop")

            // Set run info text
            runInfo.text = infoParts.joinToString(" - ")

            // Set date
            runDate.text = dateFormat.format(Date(run.date))
        }
    }
}