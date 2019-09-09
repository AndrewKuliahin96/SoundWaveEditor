package com.example.soundwaveeditor.extensions

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.widget.CompoundButton


private const val KEYBOARD_HEIGHT_RATIO = 0.15
private var blockKeyboardListener: Boolean = true
private const val ALPHA_ENABLED = 1.0f
private const val ALPHA_DISABLED = 0.5f

fun View.OnClickListener.setClickListeners(vararg views: View) {
    views.forEach { view -> view.setOnClickListener(this) }
}

fun View.hide(gone: Boolean = true) {
    visibility = if (gone) View.GONE else View.INVISIBLE
}

fun View.show() {
    visibility = View.VISIBLE
}

fun View.isVisible() = visibility == View.VISIBLE

fun View.setVisibility(isVisible: Boolean, gone: Boolean = true) = if (isVisible) show() else hide(gone)

fun View.addKeyboardStateChangeListener(listener: KeyBoardStateChangedListener) = ViewTreeObserver.OnGlobalLayoutListener {
    val rect = Rect()
    getWindowVisibleDisplayFrame(rect)
    val screenHeight = rootView.height
    val keypadHeight = screenHeight - rect.bottom

    when {
        !blockKeyboardListener && (keypadHeight > screenHeight * KEYBOARD_HEIGHT_RATIO) -> {
            blockKeyboardListener = true
            listener.onKeyboardOpened()
        }
        blockKeyboardListener && (keypadHeight <= screenHeight * KEYBOARD_HEIGHT_RATIO) -> {
            blockKeyboardListener = false
            listener.onKeyboardClosed()
        }
    }
}.also { viewTreeObserver.addOnGlobalLayoutListener(it) }

fun View.removeKeyboardStateChangeListener(listener: ViewTreeObserver.OnGlobalLayoutListener?) {
    viewTreeObserver.removeOnGlobalLayoutListener(listener)
}

fun View?.setEnabledWithAlpha(isEnable: Boolean, disableAlpha: Float = ALPHA_DISABLED, enabledAlpha: Float = ALPHA_ENABLED) {
    this?.apply {
        isEnabled = isEnable
        alpha = if (isEnable) enabledAlpha else disableAlpha
    }
}


inline fun <T : View> T.afterMeasured(crossinline f: T.() -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (measuredWidth > 0 && measuredHeight > 0) {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                f()
            }
        }
    })
}

fun View.disable() {
    alpha = ALPHA_DISABLED
    isEnabled = false
}

fun View.enable() {
    alpha = ALPHA_ENABLED
    isEnabled = true
}

fun CompoundButton.OnCheckedChangeListener.setOnCheckedChangeListeners(vararg views: CompoundButton) {
    views.forEach { view -> view.setOnCheckedChangeListener(this) }
}

interface KeyBoardStateChangedListener {
    fun onKeyboardOpened()
    fun onKeyboardClosed()
}
