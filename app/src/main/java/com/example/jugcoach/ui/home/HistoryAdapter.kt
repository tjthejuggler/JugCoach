package com.example.jugcoach.ui.home

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jugcoach.R
import com.example.jugcoach.data.dao.CoachDao
import com.example.jugcoach.data.entity.HistoryEntry
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class HistoryAdapter(
    private val onPatternClicked: (String) -> Unit,
    private val lifecycleOwner: LifecycleOwner,
    private val coachDao: CoachDao
) : ListAdapter<HistoryEntry, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    // Cache for coach names to avoid repeated lookups
    private val coachNameCache = ConcurrentHashMap<Long, String>()
    
    // Default coach name to use when not found
    private val defaultCoachName = "Coach"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_entry, parent, false)
        return HistoryViewHolder(view, onPatternClicked, lifecycleOwner, ::getCoachName)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    // Fetch coach name from cache or database
    private suspend fun getCoachName(coachId: Long): String {
        // Return from cache if available
        coachNameCache[coachId]?.let { return it }
        
        // Otherwise fetch from database
        return try {
            val coach = coachDao.getCoachById(coachId)
            val name = coach?.name ?: defaultCoachName
            coachNameCache[coachId] = name
            name
        } catch (e: Exception) {
            android.util.Log.e("HistoryAdapter", "Error getting coach name", e)
            defaultCoachName
        }
    }

    class HistoryViewHolder(
        itemView: View,
        private val onPatternClicked: (String) -> Unit,
        private val lifecycleOwner: LifecycleOwner,
        private val getCoachName: suspend (Long) -> String
    ) : RecyclerView.ViewHolder(itemView) {
        private val timelineDot: View = itemView.findViewById(R.id.timeline_dot)
        private val timelineTextType: TextView = itemView.findViewById(R.id.text_entry_type)
        private val timelineTextAuthor: TextView = itemView.findViewById(R.id.text_entry_author)
        private val timelineTextDescription: TextView = itemView.findViewById(R.id.text_entry_description)
        private val timelineTextTime: TextView = itemView.findViewById(R.id.text_entry_time)
        private val timelineTextDate: TextView = itemView.findViewById(R.id.text_entry_date)
        
        private val context: Context = itemView.context

        fun bind(entry: HistoryEntry) {
            // Set type text and color based on entry type
            when (entry.type) {
                HistoryEntry.TYPE_RUN_ADDED -> {
                    timelineTextType.text = context.getString(R.string.run_added)
                    timelineDot.setBackgroundColor(ContextCompat.getColor(context, R.color.run_added_color))
                }
                HistoryEntry.TYPE_PATTERN_CREATED -> {
                    timelineTextType.text = context.getString(R.string.pattern_created)
                    timelineDot.setBackgroundColor(ContextCompat.getColor(context, R.color.pattern_created_color))
                }
                HistoryEntry.TYPE_COACH_CREATED -> {
                    timelineTextType.text = context.getString(R.string.coach_created)
                    timelineDot.setBackgroundColor(ContextCompat.getColor(context, R.color.coach_created_color))
                }
                else -> {
                    timelineTextType.text = entry.type
                    timelineDot.setBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimary))
                }
            }
            
            // Set author text
            if (entry.isFromUser) {
                timelineTextAuthor.text = context.getString(R.string.by_user)
            } else {
                // If from coach, set default text first
                timelineTextAuthor.text = context.getString(R.string.by_coach, "Coach")
                
                // Then attempt to load the coach name if we have a relatedCoachId
                entry.relatedCoachId?.let { coachId ->
                    lifecycleOwner.lifecycleScope.launch {
                        val coachName = getCoachName(coachId)
                        timelineTextAuthor.text = context.getString(R.string.by_coach, coachName)
                    }
                }
            }
            
            // Set description
            timelineTextDescription.text = entry.description
            
            // Set time and date
            val timestamp = entry.timestamp
            val date = Date(timestamp)
            
            // Format time (e.g., "10:30 AM")
            timelineTextTime.text = DateFormat.getTimeFormat(context).format(date)
            
            // Format date based on when it happened
            timelineTextDate.text = when {
                DateUtils.isToday(timestamp) -> context.getString(R.string.today)
                isYesterday(timestamp) -> context.getString(R.string.yesterday)
                else -> DateFormat.getDateFormat(context).format(date)
            }
            
            // Make pattern clickable if it has a related pattern
            if (entry.relatedPatternId != null) {
                itemView.setOnClickListener {
                    onPatternClicked(entry.relatedPatternId)
                }
            } else {
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            }
        }
        
        private fun isYesterday(timestamp: Long): Boolean {
            val yesterday = Calendar.getInstance()
            yesterday.add(Calendar.DAY_OF_YEAR, -1)
            yesterday.set(Calendar.HOUR_OF_DAY, 0)
            yesterday.set(Calendar.MINUTE, 0)
            yesterday.set(Calendar.SECOND, 0)
            yesterday.set(Calendar.MILLISECOND, 0)
            
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)
            
            return timestamp >= yesterday.timeInMillis && timestamp < today.timeInMillis
        }
    }
    
    private class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryEntry>() {
        override fun areItemsTheSame(oldItem: HistoryEntry, newItem: HistoryEntry): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: HistoryEntry, newItem: HistoryEntry): Boolean {
            return oldItem == newItem
        }
    }
}