package com.example.soundwaveeditor.extensions

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.annotation.ArrayRes
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment


fun Context.getDrawableCompat(@DrawableRes id: Int) = ContextCompat.getDrawable(applicationContext, id)

fun Context.getColorCompat(@ColorRes id: Int) = ContextCompat.getColor(applicationContext, id)

fun Context.getStringArray(@ArrayRes id: Int) = resources.getStringArray(id)

fun Context?.getStringApp(@StringRes stringId: Int) = this?.resources?.getString(stringId)

fun Context.getInteger(intRes: Int) = this.resources.getInteger(intRes)

fun Fragment.showKeyboard() = activity?.showKeyboard()

fun Context.dpToPx(dp: Float) = resources.displayMetrics.density * dp

fun Context.pxToDp(px: Float) = px / resources.displayMetrics.density

fun Context.showKeyboard() {
    (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.apply {
        toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }
}
