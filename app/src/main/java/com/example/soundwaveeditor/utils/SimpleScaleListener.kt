package com.example.soundwaveeditor.utils

import android.view.ScaleGestureDetector


open class SimpleScaleListener : ScaleGestureDetector.OnScaleGestureListener {
    override fun onScaleBegin(scaleDetector: ScaleGestureDetector?) = false

    override fun onScale(scaleDetector: ScaleGestureDetector?) = false

    override fun onScaleEnd(scaleDetector: ScaleGestureDetector?) = Unit
}
