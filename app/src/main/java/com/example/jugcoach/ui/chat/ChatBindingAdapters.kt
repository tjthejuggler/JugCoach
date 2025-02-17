package com.example.jugcoach.ui.chat

import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.google.android.material.card.MaterialCardView
import com.example.jugcoach.ui.chat.ChatMessage

object ChatBindingAdapters {
    @JvmStatic
    @BindingAdapter("messageBackground")
    fun setMessageBackground(view: MaterialCardView, message: ChatMessage?) {
        if (message == null) return
        val context = view.context
        val colorRes = when {
            message.messageType == ChatMessage.MessageType.RUN_SUMMARY -> com.example.jugcoach.R.color.run_summary_background
            message.sender == ChatMessage.Sender.USER -> com.example.jugcoach.R.color.user_message_background
            message.messageType == ChatMessage.MessageType.ACTION -> com.example.jugcoach.R.color.action_message_background
            message.messageType == ChatMessage.MessageType.THINKING -> com.example.jugcoach.R.color.thinking_message_background
            else -> com.example.jugcoach.R.color.talking_message_background
        }
        view.setCardBackgroundColor(context.getColor(colorRes))
    }

    @BindingAdapter(value = ["messageText", "onPatternClick"], requireAll = true)
    @JvmStatic
    fun setMessageText(textView: TextView, message: ChatMessage?, onPatternClick: ((String) -> Unit)?) {
        if (message == null || onPatternClick == null) {
            textView.text = message?.text
            return
        }

        val text = message.text
        val spannableString = SpannableString(text)

        // Only process RUN_SUMMARY messages
        if (message.messageType == ChatMessage.MessageType.RUN_SUMMARY) {
            val firstLine = text.lines().firstOrNull() ?: return
            val patternName = firstLine.trim()
            
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    onPatternClick.invoke(patternName)
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = true
                    ds.color = textView.currentTextColor // Keep the same color as the text
                    ds.strokeWidth = 2f // Make underline thicker
                    ds.bgColor = android.graphics.Color.parseColor("#33FFFFFF") // Semi-transparent white background
                }
            }

            spannableString.setSpan(
                clickableSpan,
                0,
                patternName.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.text = spannableString
    }
}
