package com.hidble.phonekeyboard

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatEditText

/**
 * 可滚动 EditText：内容溢出时可内部上下滚动。
 * 手指按在文本框上时，如果本框还有内容可滚动，就禁止外层 ScrollView 拦截，
 * 保证“滑动文本框 = 滚动文本框内容”，而不是整个页面在滚。
 */
class ScrollableEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val canScroll = canScrollVertically(1) || canScrollVertically(-1)
            parent?.requestDisallowInterceptTouchEvent(canScroll)
        }
        return super.onTouchEvent(event)
    }
}
