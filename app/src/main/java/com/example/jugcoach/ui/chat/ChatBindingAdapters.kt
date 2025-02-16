package com.example.jugcoach.ui.chat

import androidx.databinding.BindingAdapter
import com.google.android.material.card.MaterialCardView
import com.example.jugcoach.ui.chat.ChatMessage

@BindingAdapter("messageBackground")
fun setMessageBackground(view: MaterialCardView, message: ChatMessage?) {
    if (message == null) return
    val context = view.context
    val colorRes = when {
        message.sender == ChatMessage.Sender.USER -> com.example.jugcoach.R.color.user_message_background
        message.messageType == ChatMessage.MessageType.ACTION -> com.example.jugcoach.R.color.action_message_background
        message.messageType == ChatMessage.MessageType.THINKING -> com.example.jugcoach.R.color.thinking_message_background
        else -> com.example.jugcoach.R.color.talking_message_background
    }
    view.setCardBackgroundColor(context.getColor(colorRes))
}
