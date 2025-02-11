package com.example.jugcoach.ui.details

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jugcoach.data.entity.ProposalWithCoach
import com.example.jugcoach.databinding.ItemProposalBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProposalAdapter(
    private val onApprove: (ProposalWithCoach) -> Unit,
    private val onReject: (ProposalWithCoach) -> Unit
) : ListAdapter<ProposalWithCoach, ProposalAdapter.ViewHolder>(ProposalDiffCallback()) {

    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProposalBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemProposalBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ProposalWithCoach) {
            val proposal = item.proposal
            val coach = item.coach

            binding.apply {
                coachName.text = coach.name
                proposalDate.text = dateFormat.format(Date(proposal.createdAt))

                // Parse and display changes
                val changes = gson.fromJson<Map<String, Any>>(
                    proposal.proposedChanges,
                    object : TypeToken<Map<String, Any>>() {}.type
                )
                
                val changesText = buildString {
                    changes.forEach { (field, value) ->
                        append("• $field: $value\n")
                    }
                }
                proposedChanges.text = changesText

                // Set up buttons
                approveButton.setOnClickListener { onApprove(item) }
                rejectButton.setOnClickListener { onReject(item) }
            }
        }
    }

    private class ProposalDiffCallback : DiffUtil.ItemCallback<ProposalWithCoach>() {
        override fun areItemsTheSame(oldItem: ProposalWithCoach, newItem: ProposalWithCoach): Boolean {
            return oldItem.proposal.id == newItem.proposal.id
        }

        override fun areContentsTheSame(oldItem: ProposalWithCoach, newItem: ProposalWithCoach): Boolean {
            return oldItem == newItem
        }
    }
}
