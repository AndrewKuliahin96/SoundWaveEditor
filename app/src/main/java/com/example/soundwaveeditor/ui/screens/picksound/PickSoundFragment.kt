package com.example.soundwaveeditor.ui.screens.picksound

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.soundwaveeditor.R
import android.util.Log
import androidx.lifecycle.Observer
import com.example.soundwaveeditor.extensions.setClickListeners
import com.example.soundwaveeditor.extensions.setVisibility
import com.example.soundwaveeditor.ui.screens.base.BaseLifecycleFragment
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_pick_sound.*
import java.io.File
import java.util.concurrent.TimeUnit


@Suppress("SameParameterValue")
@ExperimentalUnsignedTypes
class PickSoundFragment : BaseLifecycleFragment<PickSoundViewModel>(), View.OnClickListener {

    override val viewModelClass = PickSoundViewModel::class.java
    override val layoutId = R.layout.fragment_pick_sound
    override val toolbarId = -1
    override val toolbarTitleId = -1
    override val backNavId = -1
    override val isHaveToolbar = false

    private var rxPermissions: Disposable? = null

    override fun observeLiveData() {
        viewModel.errorLiveData.observe(this, Observer {
            logE("error occurred, ${it.toString()}")
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            rxPermissions = RxPermissions(this)
                .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe { granted ->
                    if (granted) {
                        comeOn()
                    } else {
                        Toast.makeText(context, "Permission denied!", Toast.LENGTH_SHORT).show()
                    }
                }

            setClickListeners(bPlay, bPause, bStop)
        }

        logE("onViewCreated")
    }

    override fun onStop() {
        super.onStop()
        logE("onStop")
    }

    override fun onDestroyView() {
        super.onDestroyView()

        logE("onDestroyView")

        rxPermissions?.dispose()
    }

    private fun logE(message: String) {
        Log.e("PICK SOUND FR", message)
    }

    override fun onClick(view: View?) {
        view?.id?.let {
            // TODO impl playing listener interface

            when (it) {
                R.id.bPlay -> {}
                R.id.bPause -> {}
                R.id.bStop -> {}
            }
        }
    }

    private fun comeOn() {
        showAlert("Do u wanna pick sound to process?", "Open audio", true,
            R.string.dialog_ok, {
                vSoundEditor.setVisibility(true)
                drawHistogram()
                bTrim.setOnClickListener { vSoundEditor.trimAudio() }
            }, R.string.dialog_cancel) { Toast.makeText(context, "Cancel", Toast.LENGTH_SHORT).show() }
    }

    override fun showAlert(message: CharSequence, title: CharSequence?, cancelable: Boolean, positiveRes: Int?,
                                   positiveFun: () -> Unit, negativeRes: Int?, negativeFun: () -> Unit) {
        context?.let {
            AlertDialog.Builder(it).apply {
                setMessage(message)
                setCancelable(cancelable)
                title?.let { title -> setTitle(title) }
                setPositiveButton(positiveRes?.let { pos -> pos } ?: R.string.dialog_ok) { _, _ -> positiveFun() }
                negativeRes?.let { neg -> setNegativeButton(neg) { _, _ -> negativeFun() } }
                show()
            }
        }
    }

    private var player: MediaPlayer? = null

    @SuppressLint("SetTextI18n")
    private fun drawHistogram() {
        vSoundEditor.apply {

            // Testing for audio formats cases
//            val path = "/storage/sdcard1/Test/20SYL - Voices ft Rita J (instru).wav"      // OK
//            val path = "/storage/sdcard1/Test/20SYL - Voices ft Rita J (instru).mp3"      // OK
//

            val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                "/sdcard/Download/smrtdeath - Black castle (feat. New Jerzey Devil).mp3"      // Samsung
            } else {
                "/storage/sdcard1/Music/smrtdeath - Black castle (feat. New Jerzey Devil).mp3"  // Xiaomi
            }

            // TODO did file opening non-blocking to avoid ANR state

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

                player?.setOnPreparedListener { p ->
                    p.start()

                    Log.e("PREPARED", "ok")

                    seekingCallback = {
                        p.seekTo(it)

                        Log.e("SEEK", "$it")
                    }

                    updatePeriod?.let {
                        Flowable
                            .interval(it, TimeUnit.MILLISECONDS)
                            .takeWhile { p.isPlaying }
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

            fileName = path
        }
    }
}
