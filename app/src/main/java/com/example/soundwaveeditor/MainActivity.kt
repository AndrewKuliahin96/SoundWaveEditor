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
            when (seekBar.id) {
                R.id.sbLeft -> {
                    vSoundEditor.leftSlideBar = vSoundEditor.volumeColumns.size / 100 * p1
                    vSoundEditor1.leftSlideBar = vSoundEditor1.volumeColumns.size / 100 * p1
                }
                R.id.sbRight -> {
                    vSoundEditor.rightSlideBar = vSoundEditor.volumeColumns.size / 100 * p1
                    vSoundEditor1.rightSlideBar = vSoundEditor1.volumeColumns.size / 100 * p1
                }
                R.id.sbFirstPosition -> {
                    vSoundEditor.firstVisibleColumn = vSoundEditor.volumeColumns.size / 100 * p1
                    vSoundEditor1.firstVisibleColumn = vSoundEditor1.volumeColumns.size / 100 * p1
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
        val soundDurationMs = 8000

        vSoundEditor.volumeColumns = getUBytes(1900).toMutableList()
        vSoundEditor.maxColumnsCount = 800
        vSoundEditor.currentColumnsCount = 700
        vSoundEditor.firstVisibleColumn = 0
        vSoundEditor.rightSlideBar = 400
        vSoundEditor.leftSlideBar = 100
        vSoundEditor.soundDuration = soundDurationMs

        vSoundEditor1.volumeColumns = getUBytes(900).toMutableList()
        vSoundEditor1.maxColumnsCount = 800
        vSoundEditor1.currentColumnsCount = 700
        vSoundEditor1.firstVisibleColumn = 200
        vSoundEditor1.rightSlideBar = 400
        vSoundEditor1.leftSlideBar = 200
        vSoundEditor1.soundDuration = soundDurationMs
    }

    private fun getUBytes(size: Int) = Random.nextUBytes(size)
}
