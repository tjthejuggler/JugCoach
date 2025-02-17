package com.example.jugcoach.ui.chat

import android.view.LayoutInflater
import com.example.jugcoach.data.entity.Coach
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jugcoach.R
import com.example.jugcoach.databinding.ItemChatMessageBinding
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class ChatAdapter(
    private var currentCoach: Coach? = null
) : ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

    fun updateCoach(coach: Coach?) {
        currentCoach = coach
        notifyDataSetChanged()
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        android.util.Log.d("ChatAdapter", "Creating new ViewHolder")
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        holder.bind(message)
    }

    override fun submitList(list: List<ChatMessage>?) {
        // Create a new copy for proper diffing, but don't filter out internal messages
        val newList = list?.toList()
        super.submitList(newList)
    }

    inner class MessageViewHolder(
        private val binding: ItemChatMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            binding.apply {
                // Set message text and timestamp
                messageText.text = message.text
                timestampText.text = message.timestamp
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

                // Configure sender-specific UI
                // Configure message layout
                messageCard.apply {
                    when {
                        message.messageType == ChatMessage.MessageType.RUN_SUMMARY -> {
                            senderText.text = context.getString(R.string.run_completed)
                            setCardBackgroundColor(ContextCompat.getColor(context, R.color.run_summary_background))
                            (layoutParams as ConstraintLayout.LayoutParams).apply {
                                marginStart = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_large)
                                marginEnd = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_small)
                                horizontalBias = 1.0f
                            }
                        }
                        message.sender == ChatMessage.Sender.USER -> {
                            senderText.text = "You"
                            setCardBackgroundColor(ContextCompat.getColor(context, R.color.user_message_background))
                            (layoutParams as ConstraintLayout.LayoutParams).apply {
                                marginStart = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_large)
                                marginEnd = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_small)
                                horizontalBias = 1.0f
                            }
                        }
                        else -> {
                            senderText.text = currentCoach?.name ?: "Coach"
                            val backgroundColor = when (message.messageType) {
                                ChatMessage.MessageType.ACTION -> R.color.action_message_background
                                ChatMessage.MessageType.THINKING -> R.color.thinking_message_background
                                ChatMessage.MessageType.TALKING -> R.color.talking_message_background
                                ChatMessage.MessageType.RUN_SUMMARY -> R.color.run_summary_background
                            }
                            setCardBackgroundColor(ContextCompat.getColor(context, backgroundColor))
                            (layoutParams as ConstraintLayout.LayoutParams).apply {
                                marginStart = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_small)
                                marginEnd = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_large)
                                horizontalBias = 0.0f
                            }
                        }
                    }
                    
                    strokeWidth = itemView.context.resources.getDimensionPixelSize(R.dimen.message_stroke_width)
                    strokeColor = ContextCompat.getColor(context, R.color.message_border)
                }

                // Handle error state
                if (message.isError) {
                    messageCard.strokeWidth = itemView.context.resources.getDimensionPixelSize(R.dimen.error_stroke_width)
                    messageCard.strokeColor = ContextCompat.getColor(root.context, R.color.error_stroke)
                } else {
                    messageCard.strokeWidth = 0
                }
            }
        }
    }

    private class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem  // Use data class equals for complete comparison
        }

        override fun getChangePayload(oldItem: ChatMessage, newItem: ChatMessage): Any? {
            // Return non-null to trigger partial update instead of full rebind
            return if (oldItem.id == newItem.id) Unit else null
        }
    }
}
