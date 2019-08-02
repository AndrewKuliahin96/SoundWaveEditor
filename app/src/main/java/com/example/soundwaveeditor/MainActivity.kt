package com.example.soundwaveeditor

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.random.Random
import kotlin.random.nextUBytes


@ExperimentalUnsignedTypes
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawHistogram()
    }

    private fun drawHistogram() {
        val soundDurationMs = 9800

        vSoundEditor.volumeColumns = getUBytes(900).toMutableList()
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
