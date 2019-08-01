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
        vSoundEditor.volumeColumns = getUBytes(600).toMutableList()
        vSoundEditor.maxColumnsCount = 400
        vSoundEditor.currentColumnsCount = 350
        vSoundEditor.firstVisibleColumn = 50
        vSoundEditor.rightSlideBar = 200
        vSoundEditor.leftSlideBar = 80
    }

    private fun getUBytes(size: Int) = Random.nextUBytes(size)
}
