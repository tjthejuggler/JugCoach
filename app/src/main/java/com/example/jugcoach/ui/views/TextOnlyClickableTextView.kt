package com.example.jugcoach.ui.views

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import com.google.android.material.textview.MaterialTextView

class TextOnlyClickableTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialTextView(context, attrs, defStyleAttr) {

    private val textBounds = Rect()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val text = text
            if (text.isNotEmpty()) {
                paint.getTextBounds(text.toString(), 0, text.length, textBounds)
                
                // Adjust bounds based on padding and gravity
                val textX = when {
                    layout.alignment == android.text.Layout.Alignment.ALIGN_CENTER -> (width - textBounds.width()) / 2
                    else -> paddingLeft
                }
                val textY = (height - textBounds.height()) / 2

                textBounds.offset(textX, textY)

                // Check if touch is within text bounds
                if (!textBounds.contains(event.x.toInt(), event.y.toInt())) {
                    return false
                }
            }
        }
        return super.onTouchEvent(event)
    }
}