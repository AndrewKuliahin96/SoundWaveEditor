package com.example.soundwaveeditor

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.SeekBar
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.random.Random
import kotlin.random.nextUBytes


@ExperimentalUnsignedTypes
class MainActivity : AppCompatActivity() {

    private val onSeekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {

        override fun onProgressChanged(seekBar: SeekBar, p1: Int, p2: Boolean) {
            val pos = vSoundEditor.currentColumnsCount / 100 * p1

            when (seekBar.id) {
                R.id.sbLeft -> { vSoundEditor.leftSlideBar = pos }
                R.id.sbRight -> { vSoundEditor.rightSlideBar = pos }
                R.id.sbFirstPosition -> {
                    vSoundEditor.firstVisibleColumn = (vSoundEditor.columnBytes.size  - vSoundEditor.currentColumnsCount) / 100 * p1
                }
            }
        }

        override fun onStartTrackingTouch(p0: SeekBar?) = Unit

        override fun onStopTrackingTouch(p0: SeekBar?) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawHistogram()

        sbLeft.setOnSeekBarChangeListener(onSeekBarChangeListener)
        sbRight.setOnSeekBarChangeListener(onSeekBarChangeListener)
        sbFirstPosition.setOnSeekBarChangeListener(onSeekBarChangeListener)
    }

    private fun drawHistogram() {
        val soundDurationMs = 65_000

        val bytes = getUBytes(1_900).toMutableList()

        vSoundEditor.columnBytes = bytes
        vSoundEditor.maxColumnsCount = 800
        vSoundEditor.currentColumnsCount = 700
        vSoundEditor.firstVisibleColumn = 200
        vSoundEditor.rightSlideBar = 400
        vSoundEditor.leftSlideBar = 100
        vSoundEditor.soundDuration = soundDurationMs
    }

    private fun getUBytes(size: Int) = Random.nextUBytes(size)
}
