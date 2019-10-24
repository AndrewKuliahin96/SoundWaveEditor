package com.example.soundwaveeditor.extensions

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment


internal const val NO_FLAGS = 0

fun AppCompatActivity.hideKeyboard() = currentFocus?.let {
    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).apply {
        hideSoftInputFromWindow(it.windowToken, NO_FLAGS)
    }
}

fun Fragment.hideKeyboard() = activity.takeIf { it is AppCompatActivity }?.let {
    (it as AppCompatActivity).hideKeyboard()
}
