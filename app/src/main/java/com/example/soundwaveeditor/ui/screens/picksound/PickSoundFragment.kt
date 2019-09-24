package com.example.soundwaveeditor.ui.screens.picksound

import android.annotation.SuppressLint
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.soundwaveeditor.R
import com.example.soundwaveeditor.extensions.setVisibility
import com.example.soundwaveeditor.ui.screens.base.BaseFragment
import kotlinx.android.synthetic.main.fragment_pick_sound.*
import android.os.Handler
import android.util.Log
import java.io.File


@Suppress("SameParameterValue")
@ExperimentalUnsignedTypes
class PickSoundFragment : BaseFragment(LAYOUT_ID) {

    companion object {
        private const val LAYOUT_ID = R.layout.fragment_pick_sound

        fun getInstance(args: Bundle? = null) = PickSoundFragment().apply {
            args?.let { arguments = args }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showAlert("Do u wanna pick sound to process?", "Open audio", true,
            R.string.dialog_ok, {
                vSoundEditor.setVisibility(true)
                drawHistogram()
            },
            R.string.dialog_cancel, {
                Toast.makeText(context, "Cancel", Toast.LENGTH_SHORT).show()
            })
    }

//    override fun onActivityCreated(savedInstanceState: Bundle?) {
//        super.onActivityCreated(savedInstanceState)
//
//        showAlert("Do u wanna pick sound to process?", "Open audio", true,
//            R.string.dialog_ok, {
//                vSoundEditor.setVisibility(true)
//                drawHistogram()
//            },
//            R.string.dialog_cancel, {
//                Toast.makeText(context, "Cancel", Toast.LENGTH_SHORT).show()
//            })
//    }

    private fun showAlert(message: CharSequence, dialogTitle: CharSequence?, cancelable: Boolean, positiveRes: Int?,
                          positiveFun: () -> Unit, negativeRes: Int?, negativeFun: () -> Unit) {
        context?.let {
            AlertDialog.Builder(it).apply {
                setMessage(message)
                setCancelable(cancelable)
                dialogTitle?.let { title -> setTitle(title) }
                setPositiveButton(positiveRes?.let { pos -> pos } ?: R.string.dialog_ok) { _, _ -> positiveFun() }
                negativeRes?.let { neg -> setNegativeButton(neg) { _, _ -> negativeFun() } }
                show()
            }
        }
    }

    // TODO NEED TO SAVE VIEW STATE


    private var player: MediaPlayer? = null
    private val handler = Handler()
    private var timer = 0

    @SuppressLint("SetTextI18n")
    private fun drawHistogram() {
        vSoundEditor.apply {

            val path = "/sdcard/Music/A\$AP Rocky x Moby x T.I. x Kid Cudi - A\$AP Forever REMIX [Рифмы и Панчи].mp3"

            // TODO remove later: this thing not needed, cause we will get bytes from audio soundfile
//            columnBytes = getShorts(1_900).toMutableList()

            // Optional, but good practice as minVis... and current...
            maxVisibleColumnsCount = 1_800
            currentVisibleColumnsCount = 700

            // OK
            firstVisibleColumn = 0

            // TODO place in center? (need to talk about it)
            rightSlideBar = 150
            leftSlideBar = 50

            loadedCallback = {
                // TODO play music here

                Log.e("CALLBACK", "ok")

                player = MediaPlayer()
                player?.setAudioStreamType(AudioManager.STREAM_MUSIC)
                player?.setDataSource(context, Uri.parse(File(path).absolutePath))

                player?.setOnPreparedListener {
                    player?.start()

                    Log.e("PREPARED", "ok")

                    object : Runnable {
                        override fun run() {

                            // TODO rem this sh*it
                            synchronized(this) {
                                timer += 10
                            }

//                            currentPlayTimeMs = player?.currentPosition ?: 0
                            currentPlayTimeMs = timer

                            handler.postDelayed(this, 10)
                        }
                    }.run()
                }

                player?.prepareAsync()
            }

            // TODO get sound duration from audio soundfile, that we pass into view
            fileName = path
        }
    }
}
