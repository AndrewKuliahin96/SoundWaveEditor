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
        val soundDurationMs = 265_000

        val bytes = getUBytes(1_900).toMutableList()

        vSoundEditor.columnBytes = bytes
        vSoundEditor.maxColumnsCount = 800
        vSoundEditor.currentColumnsCount = 200
        vSoundEditor.firstVisibleColumn = 20
        vSoundEditor.rightSlideBar = 150
        vSoundEditor.leftSlideBar = 50
        vSoundEditor.soundDuration = soundDurationMs
    }

    private fun getUBytes(size: Int) = Random.nextUBytes(size)
}
