package com.example.soundwaveeditor.ui.screens.picksound

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
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
import android.util.Log
import androidx.core.content.ContextCompat.checkSelfPermission
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.startCoroutine


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

        val r = RxPermissions(this)
            .request(Manifest.permission.READ_EXTERNAL_STORAGE)
            .subscribe { granted ->
                if (granted) {
                    comeOn()
                } else {
                    Toast.makeText(context, "Permission denied!", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun comeOn() {
        showAlert("Do u wanna pick sound to process?", "Open audio", true,
            R.string.dialog_ok, {
                vSoundEditor.setVisibility(true)
                drawHistogram()
                // TODO refactor this
                bTrim.setOnClickListener {
                    vSoundEditor.trimAudio()
                }
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

            // TODO fix file formats
            // Testing for audio formats cases
//            val path = "/storage/sdcard1/Test/example.mp3"      // OK
//            val path = "/storage/sdcard1/Test/example.wav"      // No columns
            val path = "/storage/sdcard1/Test/20SYL - Voices ft Rita J (instru).wav"      // OK
//            val path = "/storage/sdcard1/Test/20SYL - Voices ft Rita J (instru).mp3"      // OK

            // on Samsung ->
//            val path = "/sdcard/Music/glad_valakas_mrwhite_-_vitas_(zf.fm).mp3"

            // TODO need to optimize WAVE files opening (now time === 2 sec in best case)
            // TODO also did it non-blocking to avoid ANR state

//            val path = "/storage/sdcard1/Music/barnacle boi ☔ - _ flöating ☁.mp3"

            // Testing for duration cases
            // lb more than 5 min
//            val path = "/sdcard/Music/A\$AP Rocky x Moby x T.I. x Kid Cudi - A\$AP Forever REMIX [Рифмы и Панчи].mp3"

            // lb more than 1 min
//            val path = "/sdcard/Music/\$ki Mask The \$lump God - Gone (Interlude).mp3"

            // Optional, but good practice as minVis... and current...
//            maxVisibleColumnsCount = 1_800
//            currentVisibleColumnsCount = 700

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

                // TODO replace by s-thing non-depr or remove
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
                                // TODO sync with media player position
                                updatingCallback(t.time())
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
