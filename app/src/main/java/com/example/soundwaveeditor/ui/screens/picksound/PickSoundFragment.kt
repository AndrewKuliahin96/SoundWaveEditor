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
import com.example.soundwaveeditor.ui.view.SoundWaveEditorView
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.concurrent.TimeUnit


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

    @SuppressLint("SetTextI18n")
    private fun drawHistogram() {
        vSoundEditor.apply {
            val path = "/sdcard/Music/A\$AP Rocky x Moby x T.I. x Kid Cudi - A\$AP Forever REMIX [Рифмы и Панчи].mp3"
//            val path = "/sdcard/Music/\$ki Mask The \$lump God - Gone (Interlude).mp3"

            // Optional, but good practice as minVis... and current...
            maxVisibleColumnsCount = 1_800
            currentVisibleColumnsCount = 700

            // OK
            firstVisibleColumn = 0

            // TODO place in center? (need to talk about it)
            rightSlideBar = 150
            leftSlideBar = 50

            loadedCallback = {
                soundData?.let { sd ->
                    sd.averageBitrate?.let { tvBitrate.text = "Average bitrate: $it kbps" }
                    sd.sampleRate?.let { tvFrequency.text = "Sample rate: $it Hz"  }

                    soundDuration.takeIf { it > 0L }?.let {
                        val minutes = it / 1_000 / 60
                        val seconds = it / 1_000 % 60

                        tvDuration.text = "Duration: $minutes:$seconds"
                    }

                    tvInfo.text = "${sd.artist ?: "Unknown artist"} - " +
                            "${sd.title ?: "No track name"} (${sd.album ?: "Unknown album"}, " +
                            "${sd.year ?: "No year info"}) ${sd.fileType ?: "Unknown file type"}"
                }

                player = MediaPlayer()
                player?.setAudioStreamType(AudioManager.STREAM_MUSIC)
                player?.setDataSource(context, Uri.parse(File(path).absolutePath))

                player?.setOnCompletionListener {
                    Log.e("COMPLETED", "ok")
                }

                player?.setOnPreparedListener {
                    player?.start()

                    Log.e("PREPARED", "ok")

                    updatePeriod?.let {
                        Flowable
                            .interval(it, TimeUnit.MILLISECONDS)
                            .takeWhile { player?.isPlaying ?: false }
                            .timeInterval()
                            .onBackpressureLatest()
                            .subscribeOn(Schedulers.computation())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ t ->
                                updatingCallback(t.time())

                                Log.e("FLOW", "new emission")
                            }, { e ->
                                Log.e("ERROR", "${e.message}")
                            }, {
                                Log.e("COMPLETE", "ok")
                            })
                    }
                }

                player?.prepareAsync()
            }

            // TODO get sound duration from audio soundfile, that we pass into view
            fileName = path
        }
    }
}
