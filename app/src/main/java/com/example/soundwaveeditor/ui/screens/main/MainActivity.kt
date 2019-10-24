package com.example.soundwaveeditor.ui.screens.main

import android.os.Bundle
import android.util.Log
import com.example.soundwaveeditor.R
import com.example.soundwaveeditor.ui.screens.base.BaseLifecycleActivity
import com.example.soundwaveeditor.ui.screens.picksound.PickSoundFragment


@ExperimentalUnsignedTypes
class MainActivity : BaseLifecycleActivity<MainViewModel>(LAYOUT_ID) {

    companion object {
        private const val LAYOUT_ID = R.layout.activity_main
    }

    override val viewModelClass = MainViewModel::class.java
    override val containerId = R.id.container

    override fun observeLiveData() = Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            replaceFragment(newFragmentInstance<PickSoundFragment>(), false)
            Log.e("MAIN ACTIVITY", "fragment replaced")
        }
    }
}
