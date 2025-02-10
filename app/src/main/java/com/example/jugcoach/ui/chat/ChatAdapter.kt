package com.example.jugcoach.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jugcoach.R
import com.example.jugcoach.databinding.ItemChatMessageBinding
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

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
        android.util.Log.d("ChatAdapter", "Binding message at position $position: ${message.sender} - ${message.text}")
        holder.bind(message)
    }

    override fun submitList(list: List<ChatMessage>?) {
        android.util.Log.d("ChatAdapter", "Submitting new list with ${list?.size ?: 0} messages")
        list?.forEachIndexed { index, msg ->
            android.util.Log.d("ChatAdapter", "Message $index: ${msg.sender} - ${msg.text}")
        }
        super.submitList(list?.toList())
    }

    class MessageViewHolder(
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
                when (message.sender) {
                    ChatMessage.Sender.USER -> {
                        senderText.text = "You"
                        messageCard.apply {
                            setCardBackgroundColor(ContextCompat.getColor(context, R.color.user_message_background))
                            layoutParams = (layoutParams as ViewGroup.MarginLayoutParams).apply {
                                marginStart = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_large)
                                marginEnd = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_small)
                            }
                        }
                    }
                    ChatMessage.Sender.COACH -> {
                        senderText.text = "Coach"
                        messageCard.apply {
                            setCardBackgroundColor(ContextCompat.getColor(context, R.color.coach_message_background))
                            layoutParams = (layoutParams as ViewGroup.MarginLayoutParams).apply {
                                marginStart = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_small)
                                marginEnd = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_large)
                            }
                        }
                    }
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
            val result = oldItem.id == newItem.id
            android.util.Log.d("ChatAdapter", "Comparing items: ${oldItem.id} == ${newItem.id} = $result")
            return result
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            val result = oldItem == newItem
            android.util.Log.d("ChatAdapter", "Comparing contents: $oldItem == $newItem = $result")
            return result
        }
    }
}
